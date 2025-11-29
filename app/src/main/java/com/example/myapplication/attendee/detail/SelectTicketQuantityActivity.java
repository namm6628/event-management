package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

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

/**
 * Chọn loại vé + số lượng cho event KHÔNG có sơ đồ ghế.
 */
public class SelectTicketQuantityActivity extends AppCompatActivity {

    // Truyền sang PaymentActivity
    public static final String EXTRA_SELECTED_TICKETS = "selectedTickets"; // giữ cho rõ key
    public static final String EXTRA_TOTAL_AMOUNT     = "totalPrice";

    private String eventId;
    private String eventTitle;

    private FirebaseFirestore db;

    private RecyclerView recyclerView;
    private TicketQuantityAdapter adapter;

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

        adapter = new TicketQuantityAdapter(ticketTypes, this::updateSummary);
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

            double unitPrice = t.getEffectivePrice(false); // chỉ early-bird

            if (unitPrice > 0) {
                totalQty += qty;
                totalAmount += unitPrice * qty;
            } else {
                totalQty += qty; // vé miễn phí
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

        // list đơn giản để PaymentActivity dùng cho card "chia tiền"
        ArrayList<HashMap<String, Object>> selectedForPayment = new ArrayList<>();
        StringBuilder ticketNamesBuilder = new StringBuilder();

        for (TicketType t : ticketTypes) {
            int qty = t.getSelectedQuantity();
            if (qty <= 0) continue;

            double unitPrice = t.getEffectivePrice(false); // dùng giá đã áp dụng (early-bird)

            totalQty += qty;
            totalAmount += unitPrice * qty;

            // build chuỗi "VIP x2 • Thường x1"
            if (ticketNamesBuilder.length() > 0) {
                ticketNamesBuilder.append(" • ");
            }
            ticketNamesBuilder.append(t.getName() == null ? "Vé" : t.getName())
                    .append(" x").append(qty);

            // map truyền sang PaymentActivity
            HashMap<String, Object> m = new HashMap<>();
            m.put("label", "");                 // không có ghế cụ thể
            m.put("type", t.getName());         // tên loại vé
            m.put("price", unitPrice);          // giá sau ưu đãi
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
        i.putExtra("quantity", totalQty);              // PaymentActivity đang đọc key này
        i.putExtra("ticketNames", ticketNames);        // để hiển thị "VIP x2 • ..."
        i.putExtra(EXTRA_TOTAL_AMOUNT, totalAmount);   // = "totalPrice"
        i.putExtra(EXTRA_SELECTED_TICKETS, selectedForPayment); // = "selectedTickets"
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
        private final NumberFormat nf =
                NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        public TicketQuantityAdapter(List<TicketType> data, OnQuantityChange callback) {
            this.data = data;
            this.callback = callback;
        }

        @Nullable
        @Override
        public VH onCreateViewHolder(@Nullable android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_select_ticket_type, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@Nullable VH holder, int position) {
            holder.bind(data.get(position), callback, nf);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvRemain, tvQuantity;
            View btnMinus, btnPlus;

            VH(@Nullable View itemView) {
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
                      NumberFormat nf) {

                tvName.setText(t.getName() == null ? "Loại vé" : t.getName());

                // 🔥 Giá đang áp dụng (có early-bird)
                double unitPrice = t.getEffectivePrice(false); // hiện tại chưa dùng member
                String priceStr = (unitPrice == 0d)
                        ? "Miễn phí"
                        : nf.format(unitPrice) + " ₫";
                tvPrice.setText(priceStr);

                int quota = t.getQuota();
                int sold  = t.getSold();
                int remain = Math.max(0, quota - sold);
                tvRemain.setText("Còn lại: " + remain);

                tvQuantity.setText(String.valueOf(t.getSelectedQuantity()));

                // ====== ƯU ĐÃI ĐẶT SỚM: CÒN X/limit VÉ ƯU ĐÃI ======
                TextView tvPromo = itemView.findViewById(R.id.tvPromo); // thêm trong XML
                if (tvPromo != null) {
                    Integer limit = t.getEarlyBirdLimit();
                    if (limit != null && limit > 0 && sold < limit
                            && t.getEarlyBirdPrice() != null && t.getEarlyBirdPrice() > 0) {

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
