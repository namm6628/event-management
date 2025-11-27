package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectTicketsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private RecyclerView recyclerTickets;
    private TextView tvSummary;
    private MaterialButton btnContinue;
    private MaterialToolbar toolbar;

    private String eventId;
    private String eventTitle = "";
    private TicketSelectionAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Biến lưu tổng kết (để truyền sang màn hình thanh toán)
    private int finalTotalQty = 0;
    private double finalTotalPrice = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_tickets);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            finish();
            return;
        }

        // Ánh xạ View
        toolbar = findViewById(R.id.toolbar);
        recyclerTickets = findViewById(R.id.recyclerTickets);
        tvSummary = findViewById(R.id.tvSummary);
        btnContinue = findViewById(R.id.btnContinue);

        // Setup Toolbar
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup RecyclerView
        adapter = new TicketSelectionAdapter();
        recyclerTickets.setLayoutManager(new LinearLayoutManager(this));
        recyclerTickets.setAdapter(adapter);

        // Load dữ liệu từ Firebase
        loadEventInfo();
        loadTicketTypes();

        // --- XỬ LÝ CHUYỂN TRANG (Activity -> Activity) ---
        // [CẬP NHẬT] - Nút Tiếp tục
        btnContinue.setOnClickListener(v -> {
            int totalQty = adapter.getTotalQuantity();
            double totalPrice = adapter.getTotalPrice();

            if (totalQty > 0) {
                // 1. Tạo danh sách chi tiết vé để lưu DB
                ArrayList<HashMap<String, Object>> selectedTickets = new ArrayList<>();
                // 2. Tạo chuỗi tên vé để hiển thị (VD: "Vé VIP (x2), Vé Thường (x1)")
                StringBuilder ticketNamesBuilder = new StringBuilder();

                for (TicketType t : adapter.items) {
                    if (t.quantitySelected > 0) {
                        // Data cho DB
                        HashMap<String, Object> item = new HashMap<>();
                        item.put("name", t.name);
                        item.put("price", t.price);
                        item.put("quantity", t.quantitySelected);
                        selectedTickets.add(item);

                        // Data cho hiển thị
                        if (ticketNamesBuilder.length() > 0) ticketNamesBuilder.append(", ");
                        ticketNamesBuilder.append(t.name).append(" (x").append(t.quantitySelected).append(")");
                    }
                }

                Intent intent = new Intent(SelectTicketsActivity.this, PaymentActivity.class);
                intent.putExtra("eventId", eventId);
                intent.putExtra("eventTitle", eventTitle);
                intent.putExtra("quantity", totalQty);
                intent.putExtra("totalPrice", totalPrice);

                // [MỚI] Gửi thêm tên hiển thị và danh sách chi tiết
                intent.putExtra("ticketNames", ticketNamesBuilder.toString());
                intent.putExtra("selectedTickets", selectedTickets);

                startActivity(intent);
            } else {
                Toast.makeText(this, "Vui lòng chọn vé", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadEventInfo() {
        db.collection("events").document(eventId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                eventTitle = doc.getString("title");
                if (eventTitle == null) eventTitle = "Sự kiện";
                if (getSupportActionBar() != null) getSupportActionBar().setTitle(eventTitle);
            }
        });
    }

    private void loadTicketTypes() {
        db.collection("events").document(eventId).collection("ticketTypes").get()
                .addOnSuccessListener(snap -> {
                    List<TicketType> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketType t = d.toObject(TicketType.class);
                        if (t != null) list.add(t);
                    }
                    // (Nếu không có loại vé thì tạo vé giả như code cũ...)
                    if (list.isEmpty()) {
                        db.collection("events").document(eventId).get().addOnSuccessListener(doc -> {
                            Double price = doc.getDouble("price");
                            if (price == null) price = 0.0;
                            TicketType defaultTicket = new TicketType("Vé tham dự", price);
                            defaultTicket.quota = 1000L; defaultTicket.sold = 0L; // Giả lập
                            list.add(defaultTicket);
                            adapter.submit(list);
                        });
                    } else {
                        adapter.submit(list);
                    }
                });
    }

    // --- HÀM QUAN TRỌNG: Tính toán lại tổng tiền mỗi khi bấm +/- ---
    // Hàm này được Adapter gọi mỗi khi người dùng thay đổi số lượng
    private void updateTotalUI() {
        finalTotalQty = adapter.getTotalQuantity();
        finalTotalPrice = adapter.getTotalPrice();

        if (finalTotalQty > 0) {
            String priceStr = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(finalTotalPrice) + " ₫";
            tvSummary.setText(finalTotalQty + " vé • " + priceStr);
            btnContinue.setEnabled(true); // Bật nút
            btnContinue.setText("Tiếp tục (" + priceStr + ")");
        } else {
            tvSummary.setText("Vui lòng chọn vé");
            btnContinue.setEnabled(false); // Tắt nút
            btnContinue.setText("Tiếp tục");
        }
    }

    // ================== ADAPTER & MODEL ==================

    public static class TicketType {
        public String name;
        public Double price;
        public Long quota;
        public Long sold;
        public int quantitySelected = 0; // Lưu số lượng đang chọn

        public TicketType() {}
        public TicketType(String name, Double price) { this.name = name; this.price = price; }
    }

    private class TicketSelectionAdapter extends RecyclerView.Adapter<TicketSelectionAdapter.VH> {
        private final List<TicketType> items = new ArrayList<>();

        void submit(List<TicketType> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        int getTotalQuantity() {
            int sum = 0;
            for (TicketType t : items) sum += t.quantitySelected;
            return sum;
        }

        double getTotalPrice() {
            double sum = 0;
            for (TicketType t : items) sum += (t.quantitySelected * (t.price != null ? t.price : 0));
            return sum;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Nạp layout ITEM (item_select_ticket.xml) cho từng dòng
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_ticket, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TicketType item = items.get(position);

            h.tvName.setText(item.name);
            String priceStr = (item.price == null || item.price == 0) ? "Miễn phí" :
                    NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(item.price) + " ₫";
            h.tvPrice.setText(priceStr);
            h.tvQty.setText(String.valueOf(item.quantitySelected));

            // Xử lý nút Cộng (+)
            h.btnPlus.setOnClickListener(v -> {
                // Kiểm tra còn vé không
                long quota = item.quota == null ? 0 : item.quota;
                long sold = item.sold == null ? 0 : item.sold;
                long available = quota - sold;

                if (quota > 0 && item.quantitySelected >= available) {
                    Toast.makeText(SelectTicketsActivity.this, "Chỉ còn " + available + " vé", Toast.LENGTH_SHORT).show();
                    return;
                }

                item.quantitySelected++;
                notifyItemChanged(position); // Cập nhật số trên giao diện dòng này
                updateTotalUI(); // GỌI NGƯỢC LẠI ACTIVITY ĐỂ TÍNH TỔNG
            });

            // Xử lý nút Trừ (-)
            h.btnMinus.setOnClickListener(v -> {
                if (item.quantitySelected > 0) {
                    item.quantitySelected--;
                    notifyItemChanged(position);
                    updateTotalUI(); // GỌI NGƯỢC LẠI ACTIVITY ĐỂ TÍNH TỔNG
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvQty;
            // Dùng View hoặc TextView tùy theo layout bạn dùng (ở đây map theo layout bạn gửi)
            TextView btnMinus, btnPlus;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvTicketName);
                tvPrice = v.findViewById(R.id.tvTicketPrice);
                tvQty = v.findViewById(R.id.tvQuantity);
                // Layout bạn gửi dùng TextView làm nút bấm
                btnMinus = v.findViewById(R.id.btnMinus);
                btnPlus = v.findViewById(R.id.btnPlus);
            }
        }
    }
}