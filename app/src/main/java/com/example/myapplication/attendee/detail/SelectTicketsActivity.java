package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SelectTicketsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private RecyclerView recyclerTickets;
    private TextView tvSummary;
    private MaterialButton btnContinue;
    private TicketSelectAdapter adapter;

    private String eventId;
    private int totalQuantity = 0;
    private double totalPrice = 0d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_tickets);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerTickets = findViewById(R.id.recyclerTickets);
        tvSummary = findViewById(R.id.tvSummary);
        btnContinue = findViewById(R.id.btnContinue);

        adapter = new TicketSelectAdapter(this::onSelectionChanged);
        recyclerTickets.setLayoutManager(new LinearLayoutManager(this));
        recyclerTickets.setAdapter(adapter);

        // Chưa chọn vé -> disable
        btnContinue.setEnabled(false);
        tvSummary.setText("Vui lòng chọn vé");

        btnContinue.setOnClickListener(v -> onClickContinue());

        loadTickets();
    }

    // ================== LOAD TICKET TYPES ==================

    private void loadTickets() {
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    List<TicketSelectAdapter.TicketType> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketSelectAdapter.TicketType t =
                                d.toObject(TicketSelectAdapter.TicketType.class);
                        if (t != null) {
                            t.id = d.getId();
                            list.add(t);
                        }
                    }
                    adapter.submit(list);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không tải được loại vé: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    // ================== HANDLE SELECTION ==================

    private void onSelectionChanged(List<TicketSelectAdapter.TicketType> list) {
        int qty = 0;
        double total = 0d;

        for (TicketSelectAdapter.TicketType t : list) {
            if (t.selected > 0 && t.price != null) {
                qty += t.selected;
                total += t.selected * t.price;
            }
        }

        totalQuantity = qty;
        totalPrice = total;

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        if (qty == 0) {
            tvSummary.setText("Vui lòng chọn vé");
            btnContinue.setText("Tiếp tục");
            btnContinue.setEnabled(false);
        } else {
            String qtyStr = "x" + qty + " vé";
            String priceStr = total <= 0
                    ? getString(R.string.free)
                    : nf.format(total) + " đ";
            tvSummary.setText(qtyStr);
            btnContinue.setText("Tiếp tục - " + priceStr);
            btnContinue.setEnabled(true);
        }
    }

    // ================== CONTINUE: CREATE ORDER + EVENTATTENDEE ==================

    private void onClickContinue() {
        if (totalQuantity <= 0) {
            Toast.makeText(this, "Vui lòng chọn vé", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        String userEmail = user.getEmail();

        // Copy current data
        List<TicketSelectAdapter.TicketType> current = new ArrayList<>(adapter.data);
        List<Map<String, Object>> ticketItems = new ArrayList<>();

        WriteBatch batch = db.batch();

        // Order & Event ref
        DocumentReference orderRef = db.collection("orders").document();
        DocumentReference eventRef = db.collection("events").document(eventId);

        // Duyệt từng loại vé
        for (TicketSelectAdapter.TicketType t : current) {
            if (t.selected <= 0) continue;

            long quota = t.quota == null ? 0 : t.quota;
            long sold = t.sold == null ? 0 : t.sold;
            long available = quota - sold;

            if (quota > 0 && t.selected > available) {
                Toast.makeText(
                        this,
                        "Loại vé " + t.name + " chỉ còn " + available + " vé",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // Thêm vào mảng ticketItems
            Map<String, Object> m = new HashMap<>();
            m.put("typeId", t.id);
            m.put("name", t.name);
            m.put("price", t.price);
            m.put("quantity", t.selected);
            ticketItems.add(m);

            // Update sold
            DocumentReference ticketRef = db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .document(t.id);

            long newSold = sold + t.selected;
            batch.update(ticketRef, "sold", newSold);
        }

        // Trừ availableSeats của event
        batch.update(eventRef, "availableSeats",
                FieldValue.increment(-totalQuantity));

        // Build order map (phù hợp với isValidOrder trong rules)
        Map<String, Object> order = new HashMap<>();
        order.put("eventId", eventId);
        order.put("userId", userId);
        order.put("userEmail", userEmail);
        order.put("userName", "Test User");
        order.put("phone", "0123456789");
        order.put("totalTickets", totalQuantity);
        order.put("totalAmount", totalPrice);
        order.put("status", "paid"); // cho export ngay
        order.put("tickets", ticketItems);
        order.put("createdAt", FieldValue.serverTimestamp());

        batch.set(orderRef, order);

        // ✅ Tạo / cập nhật eventAttendees để dùng cho Firestore rules reviews
        DocumentReference attendeeRef = db.collection("eventAttendees")
                .document(eventId + "_" + userId);

        Map<String, Object> attendee = new HashMap<>();
        attendee.put("eventId", eventId);
        attendee.put("userId", userId);
        attendee.put("createdAt", FieldValue.serverTimestamp());

        // merge để mua nhiều lần không bị lỗi
        batch.set(attendeeRef, attendee, SetOptions.merge());

        btnContinue.setEnabled(false);

        batch.commit()
                .addOnSuccessListener(unused -> {
                    btnContinue.setEnabled(true);

                    Intent i = new Intent(this, OrderSuccessActivity.class);
                    i.putExtra(OrderSuccessActivity.EXTRA_ORDER_ID, orderRef.getId());
                    i.putExtra(OrderSuccessActivity.EXTRA_TOTAL_QTY, totalQuantity);
                    i.putExtra(OrderSuccessActivity.EXTRA_TOTAL_PRICE, totalPrice);
                    startActivity(i);

                    finish();
                })
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    Toast.makeText(
                            this,
                            "Lỗi đặt vé: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ================== ADAPTER ==================

    private static class TicketSelectAdapter
            extends RecyclerView.Adapter<TicketSelectAdapter.VH> {

        interface OnSelectionChanged {
            void onChanged(List<TicketType> list);
        }

        static class TicketType {
            public String id;
            public String name;
            public Double price;
            public Long quota;
            public Long sold;
            public int selected = 0;

            public TicketType() {}
        }

        // Activity có thể đọc được private này (do là inner class)
        private final List<TicketType> data = new ArrayList<>();
        private final OnSelectionChanged callback;

        TicketSelectAdapter(OnSelectionChanged callback) {
            this.callback = callback;
        }

        void submit(List<TicketType> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
            notifyChanged();
        }

        private void notifyChanged() {
            if (callback != null) {
                callback.onChanged(new ArrayList<>(data));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getInflatedView(parent);
            return new VH(v, this);
        }

        private View getInflatedView(ViewGroup parent) {
            return View.inflate(parent.getContext(), R.layout.item_select_ticket, null);
            // nếu muốn chuẩn hơn:
            // return LayoutInflater.from(parent.getContext())
            //        .inflate(R.layout.item_select_ticket, parent, false);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TicketType t = data.get(position);
            holder.bind(t);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        // ===== ViewHolder =====
        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvSoldOut, tvQty, btnMinus, btnPlus;
            View layoutCounter;

            VH(@NonNull View itemView, TicketSelectAdapter adapter) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvTicketName);
                tvPrice = itemView.findViewById(R.id.tvTicketPrice);
                tvSoldOut = itemView.findViewById(R.id.tvSoldOut);
                tvQty = itemView.findViewById(R.id.tvQuantity);
                btnMinus = itemView.findViewById(R.id.btnMinus);
                btnPlus = itemView.findViewById(R.id.btnPlus);
                layoutCounter = itemView.findViewById(R.id.layoutCounter);

                btnMinus.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    TicketType t = adapter.data.get(pos);
                    if (t.selected > 0) {
                        t.selected--;
                        adapter.notifyItemChanged(pos);
                        adapter.notifyChanged();
                    }
                });

                btnPlus.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    TicketType t = adapter.data.get(pos);

                    long quota = t.quota == null ? 0 : t.quota;
                    long sold = t.sold == null ? 0 : t.sold;
                    long available = quota - sold;

                    if (quota > 0 && available <= 0) return;
                    if (quota > 0 && t.selected >= available) return;

                    t.selected++;
                    adapter.notifyItemChanged(pos);
                    adapter.notifyChanged();
                });
            }

            void bind(TicketType t) {
                tvName.setText(t.name == null ? "Loại vé" : t.name);

                String priceStr;
                if (t.price == null || t.price == 0d) {
                    priceStr = "Miễn phí";
                } else {
                    priceStr = NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(t.price) + " đ";
                }
                tvPrice.setText(priceStr);

                long quota = t.quota == null ? 0 : t.quota;
                long sold = t.sold == null ? 0 : t.sold;
                long available = quota - sold;
                boolean soldOut = (quota > 0 && available <= 0);

                if (soldOut) {
                    // hiện chip Hết vé, ẩn counter, làm mờ cả dòng
                    tvSoldOut.setVisibility(View.VISIBLE);
                    layoutCounter.setVisibility(View.GONE);
                    itemView.setAlpha(0.4f);
                } else {
                    tvSoldOut.setVisibility(View.GONE);
                    layoutCounter.setVisibility(View.VISIBLE);
                    itemView.setAlpha(1f);
                }

                tvQty.setText(String.valueOf(t.selected));
            }
        }
    }
}
