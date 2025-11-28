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

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chọn loại vé + số lượng cho event KHÔNG có sơ đồ ghế.
 */
public class SelectTicketQuantityActivity extends AppCompatActivity {

    // Truyền sang PaymentActivity
    public static final String EXTRA_SELECTED_TICKETS = "selectedTickets";
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
            if (qty > 0 && t.getPrice() > 0) {
                totalQty += qty;
                totalAmount += t.getPrice() * qty;
            } else if (qty > 0 && t.getPrice() == 0d) {
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
        ArrayList<SelectedTicket> selected = new ArrayList<>();

        for (TicketType t : ticketTypes) {
            int qty = t.getSelectedQuantity();
            if (qty <= 0) continue;

            totalQty += qty;
            double price = t.getPrice();
            totalAmount += price * qty;

            SelectedTicket st = new SelectedTicket();
            st.ticketTypeId = t.getId();
            st.name = t.getName();
            st.price = price;
            st.quantity = qty;
            selected.add(st);
        }

        if (totalQty <= 0) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 vé", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra("eventId", eventId);
        i.putExtra("eventTitle", eventTitle);
        i.putExtra(EXTRA_TOTAL_AMOUNT, totalAmount);
        i.putExtra(EXTRA_SELECTED_TICKETS, selected); // Serializable
        startActivity(i);
    }

    /** Model tóm tắt vé đã chọn – truyền sang PaymentActivity */
    public static class SelectedTicket implements Serializable {
        public String ticketTypeId;
        public String name;
        public double price;
        public int quantity;
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

                String priceStr = (t.getPrice() == 0d)
                        ? "Miễn phí"
                        : nf.format(t.getPrice()) + " ₫";
                tvPrice.setText(priceStr);

                int quota = t.getQuota();
                int sold  = t.getSold();
                int remain = Math.max(0, quota - sold);
                tvRemain.setText("Còn lại: " + remain);

                tvQuantity.setText(String.valueOf(t.getSelectedQuantity()));

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
