package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Seat;
import com.example.myapplication.common.model.TicketType;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SeatSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID    = "eventId";
    public static final String EXTRA_TICKET_TYPE = "ticketType";
    public static final String EXTRA_MAX_SEATS   = "maxSeats";

    private String eventId;
    private String eventTitle;
    private String ticketType;
    private String ticketNames;

    private boolean isMember = false;

    // Giá trị gốc truyền sang – chỉ dùng nếu EVENT KHÔNG có sơ đồ ghế (fallback)
    private int baseQuantity;
    private double baseTotalPrice;
    private int maxSeats;

    // Giá trị được tính lại theo ghế
    private int currentQuantity = 0;
    private long currentTotalPrice = 0L;
    private String currentTicketSummary = "";

    private FirebaseFirestore db;
    private ListenerRegistration seatListener;

    private MaterialToolbar toolbar;
    private RecyclerView rvSeatMap;
    private TextView txtTicketInfo;
    private TextView txtSelectedSeats;
    private MaterialButton btnConfirmSeats;

    private TextView tvEventNameSeat;
    private TextView tvTicketSummarySeat;
    private TextView tvTotalPriceSeat;

    private SeatMapAdapter adapter;

    // ⭐ MAP ticketTypeId -> TicketType để tính early bird / member
    private final Map<String, TicketType> ticketTypeMap = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_selection);

        db = FirebaseFirestore.getInstance();

        // ===== Nhận data truyền sang từ EventDetailActivity =====
        Intent baseIntent = getIntent();
        eventId        = baseIntent.getStringExtra("eventId");
        eventTitle     = baseIntent.getStringExtra("eventTitle");
        ticketType     = baseIntent.getStringExtra("ticketType");
        ticketNames    = baseIntent.getStringExtra("ticketNames");

        baseQuantity   = baseIntent.getIntExtra("quantity", 0);
        baseTotalPrice = baseIntent.getDoubleExtra("totalPrice", 0);
        maxSeats       = baseIntent.getIntExtra("maxSeats",
                baseQuantity > 0 ? baseQuantity : 10);
        isMember       = baseIntent.getBooleanExtra("isMember", false);

        // ===== Ánh xạ view =====
        toolbar             = findViewById(R.id.toolbar);
        rvSeatMap           = findViewById(R.id.rvSeatMap);
        txtTicketInfo       = findViewById(R.id.txtTicketInfo);
        txtSelectedSeats    = findViewById(R.id.txtSelectedSeats);
        btnConfirmSeats     = findViewById(R.id.btnConfirmSeats);

        tvEventNameSeat     = findViewById(R.id.tvEventNameSeat);
        tvTicketSummarySeat = findViewById(R.id.tvTicketSummarySeat);
        tvTotalPriceSeat    = findViewById(R.id.tvTotalPriceSeat);

        // ===== Toolbar =====
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chọn ghế");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // ===== Text info ban đầu =====
        tvEventNameSeat.setText(eventTitle != null ? eventTitle : "Sự kiện");

        tvTicketSummarySeat.setText("Chọn ghế trên sơ đồ");
        tvTotalPriceSeat.setText("Tổng: 0 ₫");
        txtTicketInfo.setText("Chạm để chọn ghế (có thể chọn nhiều ghế)");

        txtSelectedSeats.setText("Đã chọn: 0 ghế");

        // ===== RecyclerView + adapter =====
        adapter = new SeatMapAdapter(
                maxSeats,
                this::updateHeaderBySelectedSeats
        );

        // span = số cột template (ở SeatLayoutConfigActivity đang là 12)
        rvSeatMap.setLayoutManager(new GridLayoutManager(this, 12));
        rvSeatMap.setAdapter(adapter);

        btnConfirmSeats.setEnabled(false);
        btnConfirmSeats.setOnClickListener(v -> {
            List<Seat> selected = adapter.getSelectedSeats();
            if (selected.isEmpty()) return;

            ArrayList<String> seatIds = new ArrayList<>();
            ArrayList<HashMap<String, Object>> seatTicketMaps = new ArrayList<>();

            long total = 0L;
            for (Seat s : selected) {
                seatIds.add(s.getId());

                long price = getEffectiveSeatPrice(s);   // ⭐ GIÁ ĐÃ ÁP DỤNG ƯU ĐÃI

                HashMap<String, Object> map = new HashMap<>();
                map.put("seatId", s.getId());
                map.put("label", s.getLabel());      // A7, B3...
                map.put("type",  s.getType());       // "VIP", "Standard", ...
                map.put("price", price);             // ⭐ GIÁ SAU ƯU ĐÃI (early bird / member)

                // ⭐ QUAN TRỌNG: thêm ticketTypeId + quantity để PaymentActivity cập nhật sold
                map.put("ticketTypeId", s.getTicketTypeId());
                map.put("quantity", 1);

                total += price;
                seatTicketMaps.add(map);
            }

            int quantity = selected.size();
            double totalPrice = (double) total;

            Intent intent = new Intent(this, PaymentActivity.class);
            intent.putExtra("eventId", eventId);
            intent.putExtra("eventTitle", eventTitle);
            intent.putExtra("quantity", quantity);
            intent.putExtra("totalPrice", totalPrice);
            intent.putExtra("ticketType", ticketType);
            intent.putExtra("ticketNames", currentTicketSummary);
            intent.putExtra("selectedTickets", seatTicketMaps);
            intent.putStringArrayListExtra("selectedSeatIds", seatIds);
            startActivity(intent);
        });

        // ⭐ Load ticketTypes để tính giá theo early bird / member
        loadTicketTypesForSeatPricing();

        // ⭐ Nghe realtime sơ đồ ghế
        listenSeatRealtime();
    }

    /** ⭐ Load ticketTypes cho event để map ticketTypeId -> TicketType (early bird, member) */
    private void loadTicketTypesForSeatPricing() {
        if (eventId == null) return;

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    ticketTypeMap.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketType t = d.toObject(TicketType.class);
                        if (t == null) continue;
                        t.setId(d.getId());
                        ticketTypeMap.put(t.getId(), t);
                    }

                    // Sau khi load loại vé xong, nếu đã có ghế đang chọn thì tính lại header
                    updateHeaderBySelectedSeats(adapter.getSelectedSeats());
                })
                .addOnFailureListener(e -> {
                    // optional: show log / toast
                    // Toast.makeText(this, "Không tải được loại vé: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** ⭐ Giá thực tế của 1 ghế theo loại vé + early bird / member. */
    private long getEffectiveSeatPrice(Seat s) {
        String typeId = s.getTicketTypeId();
        if (typeId != null && ticketTypeMap.containsKey(typeId)) {
            TicketType t = ticketTypeMap.get(typeId);
            if (t != null) {
                double price = t.getEffectivePrice(isMember); // ⭐
                return (long) price;
            }
        }
        return s.getPrice();
    }

    /** Tính lại số lượng, tổng tiền, chuỗi tóm tắt theo các ghế đã chọn */
    private void updateHeaderBySelectedSeats(List<Seat> selectedSeats) {
        currentQuantity = selectedSeats.size();

        currentTotalPrice = 0L;
        Map<String, Integer> typeCount = new HashMap<>();

        for (Seat s : selectedSeats) {
            long price = getEffectiveSeatPrice(s);   // ⭐ DÙNG GIÁ ƯU ĐÃI
            currentTotalPrice += price;

            String type = s.getType();
            if (type == null || type.trim().isEmpty()) {
                type = "Vé tham dự";
            }
            Integer old = typeCount.get(type);
            typeCount.put(type, (old == null ? 0 : old) + 1);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(e.getKey()).append(" x").append(e.getValue());
        }
        currentTicketSummary = (sb.length() == 0)
                ? "Chưa chọn ghế"
                : sb.toString();

        tvTicketSummarySeat.setText(currentTicketSummary);

        String priceStr;
        if (currentTotalPrice == 0) {
            priceStr = "Tổng: Chưa chọn vị trí";
        } else {
            priceStr = "Tổng: " + NumberFormat
                    .getNumberInstance(new Locale("vi", "VN"))
                    .format(currentTotalPrice) + " ₫";
        }
        tvTotalPriceSeat.setText(priceStr);

        txtSelectedSeats.setText("Đã chọn: " + currentQuantity + " ghế");
        btnConfirmSeats.setEnabled(currentQuantity > 0);
    }

    /** Lắng nghe collection seats realtime */
    private void listenSeatRealtime() {
        if (eventId == null) return;

        seatListener = db.collection("events")
                .document(eventId)
                .collection("seats")
                .addSnapshotListener((QuerySnapshot value, FirebaseFirestoreException error) -> {
                    if (error != null || value == null) return;

                    List<Seat> seats = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Seat seat = doc.toObject(Seat.class);
                        if (seat != null) {
                            seat.setId(doc.getId());
                            seats.add(seat);
                        }
                    }

                    if (seats.isEmpty()) {
                        Toast.makeText(this,
                                "Sự kiện này chưa cấu hình sơ đồ ghế, chuyển sang thanh toán.",
                                Toast.LENGTH_SHORT).show();

                        // Fallback: nếu không có sơ đồ ghế, dùng baseQuantity/baseTotalPrice
                        ArrayList<HashMap<String, Object>> fallbackTickets = new ArrayList<>();
                        if (baseQuantity > 0 && baseTotalPrice > 0) {
                            double unitPrice = baseTotalPrice / baseQuantity;
                            HashMap<String, Object> m = new HashMap<>();
                            m.put("ticketTypeId", null);
                            m.put("label", "");
                            m.put("type", ticketType != null ? ticketType : "Vé tham dự");
                            m.put("price", unitPrice);
                            m.put("quantity", baseQuantity);
                            fallbackTickets.add(m);
                        }

                        Intent intent = new Intent(this, PaymentActivity.class);
                        intent.putExtra("eventId", eventId);
                        intent.putExtra("eventTitle", eventTitle);
                        intent.putExtra("quantity", baseQuantity);
                        intent.putExtra("totalPrice", baseTotalPrice);
                        intent.putExtra("ticketType", ticketType);
                        intent.putExtra("ticketNames",
                                ticketNames != null ? ticketNames : "Vé tham dự x" + baseQuantity);
                        intent.putExtra("selectedTickets", fallbackTickets);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    // SẮP XẾP GHẾ
                    java.util.Collections.sort(seats, (s1, s2) -> {
                        String r1 = s1.getRow() == null ? "" : s1.getRow();
                        String r2 = s2.getRow() == null ? "" : s2.getRow();
                        int cmpRow = r1.compareTo(r2);
                        if (cmpRow != 0) return cmpRow;
                        return Integer.compare(s1.getNumber(), s2.getNumber());
                    });

                    adapter.setSeatList(seats); // render full map
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (seatListener != null) seatListener.remove();
    }
}
