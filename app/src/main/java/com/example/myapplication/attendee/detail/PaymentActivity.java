package com.example.myapplication.attendee.detail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private TextView tvEventName, tvQuantity, tvTotalPrice, tvTotalPriceInfo, tvTicketType;
    private RadioGroup rgPaymentMethods;
    private MaterialButton btnConfirmPayment;

    private String eventId, eventTitle, userId, ticketNames, ticketType;
    private int quantity;
    private double totalPrice;
    // Mỗi phần tử: { seatId, label, type, price }
    private ArrayList<HashMap<String, Object>> selectedTickets;
    private ArrayList<String> selectedSeatIds;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private View cardSplitBill;
    private TextView tvSplitInfo;
    private View btnShareBill;

    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        // 1. Nhận dữ liệu truyền sang
        Intent intent = getIntent();
        eventId        = intent.getStringExtra("eventId");
        eventTitle     = intent.getStringExtra("eventTitle");
        quantity       = intent.getIntExtra("quantity", 1);
        totalPrice     = intent.getDoubleExtra("totalPrice", 0);
        ticketNames    = intent.getStringExtra("ticketNames");
        ticketType     = intent.getStringExtra("ticketType");
        selectedTickets= (ArrayList<HashMap<String, Object>>) intent.getSerializableExtra("selectedTickets");
        selectedSeatIds= intent.getStringArrayListExtra("selectedSeatIds");

        userId = FirebaseAuth.getInstance().getUid();

        // 2. Ánh xạ View
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tvTotalPrice      = findViewById(R.id.tvTotalPrice);
        tvTotalPriceInfo  = findViewById(R.id.tvTotalPriceInfo);
        tvTicketType      = findViewById(R.id.tvTicketType);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        cardSplitBill     = findViewById(R.id.cardSplitBill);
        tvSplitInfo       = findViewById(R.id.tvSplitInfo);
        btnShareBill      = findViewById(R.id.btnShareBill);

        tvEventName       = findViewById(R.id.tvEventName);
        tvQuantity        = findViewById(R.id.tvQuantity);
        rgPaymentMethods  = findViewById(R.id.rgPaymentMethods);
        btnConfirmPayment = findViewById(R.id.btnConfirmPayment);

        // 3. Hiển thị thông tin chính
        tvEventName.setText(eventTitle);
        tvQuantity.setText(quantity + " vé");

        if (ticketNames != null && !ticketNames.isEmpty()) {
            tvTicketType.setText(ticketNames);   // ví dụ: VIP x1 • Thường x1
        } else if (ticketType != null && !ticketType.isEmpty()) {
            tvTicketType.setText(ticketType);
        } else {
            tvTicketType.setText("Vé tham dự");
        }

        String priceStr = nf.format(totalPrice) + " ₫";
        if (totalPrice == 0) priceStr = "Miễn phí";
        tvTotalPrice.setText(priceStr);
        tvTotalPriceInfo.setText(priceStr);

        setupSplitBill();

        // 4. Sự kiện nút Thanh toán
        btnConfirmPayment.setOnClickListener(v -> processPayment());
    }

    /** Card "Đi nhóm? Chia tiền ngay!" */
    private void setupSplitBill() {
        // Chỉ hiện nếu mua > 1 vé và có tiền
        if (quantity > 1 && totalPrice > 0) {
            cardSplitBill.setVisibility(View.VISIBLE);

            StringBuilder detail = new StringBuilder();

            // Nếu có danh sách selectedTickets (mua theo ghế)
            if (selectedTickets != null && !selectedTickets.isEmpty()) {
                for (HashMap<String, Object> map : selectedTickets) {
                    String label = safeStr(map.get("label")); // A7, B3...
                    String type  = safeStr(map.get("type"));  // VIP, wrt...
                    long price   = 0L;
                    Object pObj  = map.get("price");
                    if (pObj instanceof Number) {
                        price = ((Number) pObj).longValue();
                    }

                    if (detail.length() > 0) detail.append("\n");
                    detail.append("• ").append(type);
                    if (!label.isEmpty()) detail.append(" – ghế ").append(label);
                    if (price > 0) {
                        detail.append(": ").append(nf.format(price)).append(" ₫");
                    }
                }
            }

            // Nếu không có chi tiết từng ghế thì hiển thị đơn giản
            if (detail.length() == 0) {
                detail.append("Tổng tiền: ").append(nf.format(totalPrice)).append(" ₫");
            }

            tvSplitInfo.setText(
                    "Tổng: " + quantity + " vé\n" + detail.toString()
            );

            // Nút Share
            btnShareBill.setOnClickListener(v -> {
                StringBuilder msgDetail = new StringBuilder();

                if (selectedTickets != null && !selectedTickets.isEmpty()) {
                    for (HashMap<String, Object> map : selectedTickets) {
                        String label = safeStr(map.get("label"));
                        String type  = safeStr(map.get("type"));
                        long price   = 0L;
                        Object pObj  = map.get("price");
                        if (pObj instanceof Number) {
                            price = ((Number) pObj).longValue();
                        }

                        if (msgDetail.length() > 0) msgDetail.append("\n");
                        msgDetail.append("- ").append(type);
                        if (!label.isEmpty()) msgDetail.append(" (").append(label).append(")");
                        if (price > 0) {
                            msgDetail.append(": ").append(nf.format(price)).append(" ₫");
                        }
                    }
                } else {
                    msgDetail.append("- Tổng ").append(quantity)
                            .append(" vé: ").append(nf.format(totalPrice)).append(" ₫");
                }

                String msg = "Alo mọi người ơi! 📢\n"
                        + "Mình đang đặt vé đi sự kiện: " + eventTitle + "\n"
                        + "Tổng: " + quantity + " vé, tổng tiền: "
                        + nf.format(totalPrice) + " ₫\n"
                        + "Chi tiết:\n"
                        + msgDetail.toString()
                        + "\n\nMọi người chuyển khoản cho mình nhé 💸";

                // Copy vào clipboard
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Bill Info", msg);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Đã sao chép nội dung!", Toast.LENGTH_SHORT).show();

                // Mở menu chia sẻ
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, msg);
                startActivity(Intent.createChooser(shareIntent, "Gửi yêu cầu thanh toán qua:"));
            });

        } else {
            cardSplitBill.setVisibility(View.GONE);
        }
    }

    private String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ================== Xử lý thanh toán ==================

    private void processPayment() {
        int selectedId = rgPaymentMethods.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn phương thức thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb = findViewById(selectedId);
        String method = rb.getText().toString();

        btnConfirmPayment.setText("Đang xử lý...");
        btnConfirmPayment.setEnabled(false);
        rgPaymentMethods.setEnabled(false);

        new Handler().postDelayed(() -> saveOrderToFirestore(method), 1500);
    }

    private void saveOrderToFirestore(String method) {
        if (eventId == null || userId == null) {
            Toast.makeText(this, "Thiếu thông tin sự kiện hoặc user!", Toast.LENGTH_SHORT).show();
            resetPaymentUi();
            return;
        }

        final DocumentReference eventRef  = db.collection("events").document(eventId);
        final DocumentReference ordersRef = db.collection("orders").document();
        final String orderId = ordersRef.getId();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot snapshot = transaction.get(eventRef);

                    Long availableLong = snapshot.getLong("availableSeats");
                    long available = (availableLong == null) ? 0 : availableLong;

                    if (available < quantity) {
                        throw new RuntimeException("Rất tiếc, vé vừa bán hết!");
                    }

                    // 👇 LẤY OWNER CỦA SỰ KIỆN
                    String ownerId = snapshot.getString("ownerId");   // 👈 rất quan trọng cho thống kê

                    // 1. Trừ vé
                    transaction.update(eventRef, "availableSeats", available - quantity);

                    // 2. Tạo đơn hàng: KHỚP VỚI RULE isValidOrder
                    Map<String, Object> order = new HashMap<>();
                    order.put("eventId", eventId);
                    order.put("userId", userId);

                    // 👇 GẮN THÊM ownerId ĐỂ ORGANIZER ĐỌC & THỐNG KÊ
                    if (ownerId != null) {
                        order.put("ownerId", ownerId);               // 👈 field mới
                    }

                    // các field mà rules yêu cầu
                    order.put("totalTickets", quantity);      // int > 0
                    order.put("totalAmount", totalPrice);     // number >= 0
                    order.put("createdAt", FieldValue.serverTimestamp());
                    order.put("status", "PAID");

                    // OPTIONAL: khởi tạo trạng thái check-in
                    order.put("checkedIn", false);            // 👈 cho QR check-in
                    order.put("checkedInAt", null);

                    // các field thêm tuỳ ý – rules cho phép vì không check keys()
                    order.put("eventTitle", eventTitle);
                    order.put("paymentMethod", method);
                    order.put("quantity", quantity);
                    order.put("totalPrice", totalPrice);

                    if (ticketNames != null && !ticketNames.isEmpty()) {
                        order.put("ticketNames", ticketNames);
                    }
                    if (ticketType != null && !ticketType.isEmpty()) {
                        order.put("ticketType", ticketType);
                    }
                    if (selectedTickets != null) {
                        order.put("tickets", selectedTickets); // trùng tên rule luôn
                    }
                    if (selectedSeatIds != null && !selectedSeatIds.isEmpty()) {
                        order.put("seats", selectedSeatIds);
                    }

                    transaction.set(ordersRef, order);
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (selectedSeatIds != null && !selectedSeatIds.isEmpty()) {
                        updateSeatStatusAfterPayment(eventId, selectedSeatIds);
                    }
                    showSuccessDialog(orderId);
                })
                .addOnFailureListener(e -> {
                    resetPaymentUi();
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }



    private void resetPaymentUi() {
        btnConfirmPayment.setText("Thanh toán ngay");
        btnConfirmPayment.setEnabled(true);
        rgPaymentMethods.setEnabled(true);
    }

    private void updateSeatStatusAfterPayment(String eventId, ArrayList<String> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) return;

        WriteBatch batch = db.batch();
        for (String seatId : seatIds) {
            DocumentReference seatRef = db.collection("events")
                    .document(eventId)
                    .collection("seats")
                    .document(seatId);

            batch.update(seatRef, "status", "booked");
        }
        batch.commit();
    }

    /** Chuyển sang màn hình Thanh toán thành công (activity_order_success.xml) */
    private void showSuccessScreen(String orderId) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_ID", orderId);
        intent.putExtra("TOTAL_QTY", quantity);
        intent.putExtra("TOTAL_PRICE", totalPrice);
        startActivity(intent);
        finish();
    }

    private void showSuccessDialog(String orderId) {
        // Sau khi thanh toán thành công -> chuyển sang màn OrderSuccessActivity
        Intent intent = new Intent(this, OrderSuccessActivity.class);

        intent.putExtra("ORDER_ID", orderId);
        intent.putExtra("TOTAL_QTY", quantity);
        intent.putExtra("TOTAL_PRICE", totalPrice);

        startActivity(intent);
        // Không cho back về màn thanh toán nữa
        finish();
    }

}
