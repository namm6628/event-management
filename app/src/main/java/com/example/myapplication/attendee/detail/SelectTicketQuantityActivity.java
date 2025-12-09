package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.TicketType;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class SelectTicketQuantityActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_TICKETS = "selectedTickets";
    public static final String EXTRA_TOTAL_AMOUNT     = "totalPrice";

    private String eventId;
    private String eventTitle;

    private FirebaseFirestore db;

    private RecyclerView recyclerView;
    private TicketQuantityAdapter adapter;
    private boolean isMember = false;

    private TextView tvTotalQty, tvTotalPrice;
    private MaterialButton btnContinue;

    private final List<TicketType> ticketTypes = new ArrayList<>();
    private final NumberFormat currencyFormat =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_ticket_quantity);

        // Nhận data từ EventDetailActivity
        Intent intent = getIntent();
        eventId    = intent.getStringExtra("eventId");
        eventTitle = intent.getStringExtra("eventTitle");
        isMember   = intent.getBooleanExtra("isMember", false);

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chọn vé");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvHeader = findViewById(R.id.tvHeader);
        if (eventTitle != null && !eventTitle.isEmpty()) {
            tvHeader.setText("Sự kiện: " + eventTitle);
        }

        recyclerView = findViewById(R.id.recyclerTicketTypes);
        tvTotalQty   = findViewById(R.id.tvTotalQuantity);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        btnContinue  = findViewById(R.id.btnContinue);

        adapter = new TicketQuantityAdapter(ticketTypes, isMember, this::updateSummary);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnContinue.setOnClickListener(v -> onContinueClicked());
        btnContinue.setEnabled(false);

        loadTicketTypes();
    }

    private void loadTicketTypes() {
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    ticketTypes.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketType t = d.toObject(TicketType.class);
                        if (t == null) continue;
                        t.setId(d.getId());
                        ticketTypes.add(t);
                    }
                    adapter.notifyDataSetChanged();
                    updateSummary();
                })
                .addOnFailureListener(e -> Toast.makeText(
                        this,
                        "Không tải được loại vé: " + e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show());
    }

    private void updateSummary() {
        int totalQty = 0;
        double totalAmount = 0d;

        for (TicketType t : ticketTypes) {
            int qty = t.getSelectedQuantity();
            if (qty <= 0) continue;

            double unitPrice = t.getEffectivePrice(isMember);

            if (unitPrice > 0) {
                totalQty += qty;
                totalAmount += unitPrice * qty;
            } else {
                totalQty += qty;
            }
        }

        tvTotalQty.setText("Tổng: " + totalQty + " vé");
        if (totalAmount > 0) {
            tvTotalPrice.setText("Thành tiền: " + currencyFormat.format(totalAmount) + " ₫");
        } else {
            tvTotalPrice.setText("Thành tiền: 0 ₫");
        }

        btnContinue.setEnabled(totalQty > 0);
    }

    private void onContinueClicked() {
        int totalQty = 0;
        double totalAmount = 0d;

        ArrayList<HashMap<String, Object>> selectedForPayment = new ArrayList<>();
        StringBuilder ticketNamesBuilder = new StringBuilder();

        for (TicketType t : ticketTypes) {
            int qty = t.getSelectedQuantity();
            if (qty <= 0) continue;

            double unitPrice = t.getEffectivePrice(isMember);

            totalQty += qty;
            totalAmount += unitPrice * qty;

            if (ticketNamesBuilder.length() > 0) {
                ticketNamesBuilder.append(" • ");
            }
            ticketNamesBuilder.append(t.getName() == null ? "Vé" : t.getName())
                    .append(" x").append(qty);

            HashMap<String, Object> m = new HashMap<>();
            m.put("ticketTypeId", t.getId());
            m.put("label", "");
            m.put("type", t.getName());
            m.put("price", unitPrice);
            m.put("quantity", qty);
            selectedForPayment.add(m);
        }

        if (totalQty <= 0) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 vé", Toast.LENGTH_SHORT).show();
            return;
        }

        String ticketNames = ticketNamesBuilder.toString();

        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra("eventId", eventId);
        i.putExtra("eventTitle", eventTitle);
        i.putExtra("quantity", totalQty);
        i.putExtra("ticketNames", ticketNames);
        i.putExtra(EXTRA_TOTAL_AMOUNT, totalAmount);
        i.putExtra(EXTRA_SELECTED_TICKETS, selectedForPayment);
        startActivity(i);
    }

    // ================== ADAPTER ==================
    public static class TicketQuantityAdapter
            extends RecyclerView.Adapter<TicketQuantityAdapter.VH> {

        public interface OnQuantityChange {
            void onChanged();
        }

        private final List<TicketType> data;
        private final OnQuantityChange callback;
        private final boolean isMember;
        private final NumberFormat nf =
                NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        public TicketQuantityAdapter(List<TicketType> data,
                                     boolean isMember,
                                     OnQuantityChange callback) {
            this.data = data;
            this.isMember = isMember;
            this.callback = callback;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_select_ticket_type, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position), callback, nf, isMember);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvRemain, tvQuantity;
            View btnMinus, btnPlus;

            VH(@NonNull View itemView) {
                super(itemView);
                tvName     = itemView.findViewById(R.id.tvTicketName);
                tvPrice    = itemView.findViewById(R.id.tvTicketPrice);
                tvRemain   = itemView.findViewById(R.id.tvTicketRemain);
                tvQuantity = itemView.findViewById(R.id.tvQuantity);
                btnMinus   = itemView.findViewById(R.id.btnMinus);
                btnPlus    = itemView.findViewById(R.id.btnPlus);
            }

            void bind(TicketType t,
                      OnQuantityChange callback,
                      NumberFormat nf,
                      boolean isMember) {

                tvName.setText(t.getName() == null ? "Loại vé" : t.getName());

                double unitPrice = t.getEffectivePrice(isMember);
                String priceStr = (unitPrice == 0d)
                        ? "Miễn phí"
                        : nf.format(unitPrice) + " ₫";
                tvPrice.setText(priceStr);

                TextView tvEarlyBird = itemView.findViewById(R.id.tvEarlyBird);
                String promoLabel = t.getPromoLabel(isMember);
                if (promoLabel != null) {
                    tvEarlyBird.setText(promoLabel);
                    tvEarlyBird.setVisibility(View.VISIBLE);
                } else {
                    tvEarlyBird.setVisibility(View.GONE);
                }

                int quota = t.getQuota();
                int sold  = t.getSold();
                int remain = Math.max(0, quota - sold);
                tvRemain.setText("Còn lại: " + remain);

                tvQuantity.setText(String.valueOf(t.getSelectedQuantity()));

                TextView tvPromo = itemView.findViewById(R.id.tvPromo);
                if (tvPromo != null) {
                    Integer limit = t.getEarlyBirdLimit();
                    if (limit != null && limit > 0
                            && sold < limit
                            && t.getEarlyBirdPrice() != null
                            && t.getEarlyBirdPrice() > 0) {

                        int remainingEarly = limit - sold;
                        if (remainingEarly < 0) remainingEarly = 0;

                        String promoText = "Ưu đãi đặt sớm: còn "
                                + remainingEarly + "/" + limit + " vé ưu đãi";
                        tvPromo.setText(promoText);
                        tvPromo.setVisibility(View.VISIBLE);
                    } else {
                        tvPromo.setVisibility(View.GONE);
                    }
                }

                btnMinus.setOnClickListener(v -> {
                    int q = t.getSelectedQuantity();
                    if (q > 0) {
                        t.setSelectedQuantity(q - 1);
                        tvQuantity.setText(String.valueOf(t.getSelectedQuantity()));
                        if (callback != null) callback.onChanged();
                    }
                });

                btnPlus.setOnClickListener(v -> {
                    int q = t.getSelectedQuantity();
                    int remain1 = Math.max(0, t.getQuota() - t.getSold());
                    if (q < remain1) {
                        t.setSelectedQuantity(q + 1);
                        tvQuantity.setText(String.valueOf(t.getSelectedQuantity()));
                        if (callback != null) callback.onChanged();
                    } else {
                        Toast.makeText(
                                itemView.getContext(),
                                "Loại vé này chỉ còn " + remain1 + " vé",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        }
    }
}
