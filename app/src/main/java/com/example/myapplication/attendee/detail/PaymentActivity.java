package com.example.myapplication.attendee.detail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private double discountAmount = 0;
    private double finalAmount = 0;
    private String appliedPromoCode = null;

    private ArrayList<HashMap<String, Object>> selectedTickets;
    private ArrayList<String> selectedSeatIds;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private View cardSplitBill;
    private TextView tvSplitInfo;
    private View btnShareBill;

    private EditText edtPromoCode;
    private MaterialButton btnApplyPromo;
    private TextView tvPromoInfo;

    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        Intent intent = getIntent();
        eventId = intent.getStringExtra("eventId");
        eventTitle = intent.getStringExtra("eventTitle");
        quantity = intent.getIntExtra("quantity", 1);
        totalPrice = intent.getDoubleExtra("totalPrice", 0);
        ticketNames = intent.getStringExtra("ticketNames");
        ticketType = intent.getStringExtra("ticketType");
        selectedTickets = (ArrayList<HashMap<String, Object>>) intent.getSerializableExtra("selectedTickets");
        selectedSeatIds = intent.getStringArrayListExtra("selectedSeatIds");

        userId = FirebaseAuth.getInstance().getUid();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvTotalPriceInfo = findViewById(R.id.tvTotalPriceInfo);
        tvTicketType = findViewById(R.id.tvTicketType);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cardSplitBill = findViewById(R.id.cardSplitBill);
        tvSplitInfo = findViewById(R.id.tvSplitInfo);
        btnShareBill = findViewById(R.id.btnShareBill);

        edtPromoCode = findViewById(R.id.edtPromoCode);
        btnApplyPromo = findViewById(R.id.btnApplyPromo);
        tvPromoInfo = findViewById(R.id.tvPromoInfo);

        tvEventName = findViewById(R.id.tvEventName);
        tvQuantity = findViewById(R.id.tvQuantity);
        rgPaymentMethods = findViewById(R.id.rgPaymentMethods);
        btnConfirmPayment = findViewById(R.id.btnConfirmPayment);

        tvEventName.setText(eventTitle);
        tvQuantity.setText(quantity + " vé");

        if (ticketNames != null && !ticketNames.isEmpty()) {
            tvTicketType.setText(ticketNames);
        } else if (ticketType != null && !ticketType.isEmpty()) {
            tvTicketType.setText(ticketType);
        } else {
            tvTicketType.setText("Vé tham dự");
        }

        discountAmount = 0;
        finalAmount = totalPrice;
        updatePriceViews();

        setupSplitBill();

        btnApplyPromo.setOnClickListener(v -> applyPromoCode());

        btnConfirmPayment.setOnClickListener(v -> processPayment());
    }

    private void updatePriceViews() {
        String baseStr = (totalPrice <= 0) ? "Miễn phí" : nf.format(totalPrice) + " ₫";
        String discountStr = (discountAmount <= 0) ? "0 ₫" : "- " + nf.format(discountAmount) + " ₫";
        String finalStr = (finalAmount <= 0) ? "Miễn phí" : nf.format(finalAmount) + " ₫";

        tvTotalPrice.setText(finalStr);
        tvTotalPriceInfo.setText(
                "Tạm tính: " + baseStr +
                        "\nGiảm: " + discountStr +
                        "\nCần thanh toán: " + finalStr
        );
    }

    private void setupSplitBill() {
        if (quantity > 1 && totalPrice > 0) {
            cardSplitBill.setVisibility(View.VISIBLE);

            StringBuilder detail = new StringBuilder();

            if (selectedTickets != null && !selectedTickets.isEmpty()) {
                for (HashMap<String, Object> map : selectedTickets) {
                    String label = safeStr(map.get("label"));
                    String type = safeStr(map.get("type"));
                    long price = (map.get("price") instanceof Number)
                            ? ((Number) map.get("price")).longValue()
                            : 0L;

                    if (detail.length() > 0) detail.append("\n");
                    detail.append("• ").append(type);
                    if (!label.isEmpty()) detail.append(" – ghế ").append(label);
                    if (price > 0) detail.append(": ").append(nf.format(price)).append(" ₫");
                }
            }

            if (detail.length() == 0) {
                detail.append("Tổng tiền: ").append(nf.format(totalPrice)).append(" ₫");
            }

            tvSplitInfo.setText("Tổng: " + quantity + " vé\n" + detail);

            btnShareBill.setOnClickListener(v -> {
                StringBuilder msgDetail = new StringBuilder();

                if (selectedTickets != null && !selectedTickets.isEmpty()) {
                    for (HashMap<String, Object> map : selectedTickets) {
                        String label = safeStr(map.get("label"));
                        String type = safeStr(map.get("type"));
                        long price = (map.get("price") instanceof Number)
                                ? ((Number) map.get("price")).longValue()
                                : 0L;

                        if (msgDetail.length() > 0) msgDetail.append("\n");
                        msgDetail.append("- ").append(type);
                        if (!label.isEmpty()) msgDetail.append(" (").append(label).append(")");
                        if (price > 0) msgDetail.append(": ").append(nf.format(price)).append(" ₫");
                    }
                } else {
                    msgDetail.append("- Tổng ").append(quantity)
                            .append(" vé: ").append(nf.format(totalPrice)).append(" ₫");
                }

                String msg = "Alo mọi người ơi! \n"
                        + "Mình đang đặt vé đi sự kiện: " + eventTitle + "\n"
                        + "Tổng: " + quantity + " vé, tổng tiền: "
                        + nf.format(totalPrice) + " ₫\n"
                        + "Chi tiết:\n"
                        + msgDetail
                        + "\n\nMọi người chuyển khoản cho mình nhé ";

                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Bill Info", msg));

                Toast.makeText(this, "Đã sao chép nội dung!", Toast.LENGTH_SHORT).show();

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

    // ----------------- APPLY PROMO CODE -------------------

    private void applyPromoCode() {
        if (totalPrice <= 0) {
            Toast.makeText(this, "Đơn miễn phí không cần mã giảm giá", Toast.LENGTH_SHORT).show();
            return;
        }

        String raw = edtPromoCode.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mã khuyến mãi", Toast.LENGTH_SHORT).show();
            return;
        }

        final String code = raw.toUpperCase(Locale.ROOT);
        btnApplyPromo.setEnabled(false);
        tvPromoInfo.setText("Đang kiểm tra mã...");

        db.collection("promotions")
                .document(code)
                .get()
                .addOnSuccessListener(doc -> {
                    btnApplyPromo.setEnabled(true);

                    if (!doc.exists()) {
                        appliedPromoCode = null;
                        discountAmount = 0;
                        finalAmount = totalPrice;
                        tvPromoInfo.setText("Mã không hợp lệ.");
                        updatePriceViews();
                        return;
                    }

                    Boolean active = doc.getBoolean("active");
                    if (active != null && !active) {
                        appliedPromoCode = null;
                        discountAmount = 0;
                        finalAmount = totalPrice;
                        tvPromoInfo.setText("Mã đã bị khoá.");
                        updatePriceViews();
                        return;
                    }

                    Timestamp expiry = doc.getTimestamp("expiry");
                    if (expiry != null && expiry.toDate().before(new Date())) {
                        appliedPromoCode = null;
                        discountAmount = 0;
                        finalAmount = totalPrice;
                        tvPromoInfo.setText("Mã đã hết hạn.");
                        updatePriceViews();
                        return;
                    }

                    // Các điều kiện khác...
                    Double value = getDoubleField(doc, "value");
                    String type = doc.getString("type");

                    if (value == null || value <= 0) {
                        tvPromoInfo.setText("Mã không hợp lệ.");
                        return;
                    }

                    double discount;
                    if ("PERCENT".equalsIgnoreCase(type)) {
                        discount = totalPrice * (value / 100d);
                    } else {
                        discount = value;
                    }

                    discountAmount = Math.min(discount, totalPrice);
                    finalAmount = totalPrice - discountAmount;

                    appliedPromoCode = code;
                    tvPromoInfo.setText("Đã áp dụng mã " + code);
                    updatePriceViews();

                })
                .addOnFailureListener(e -> {
                    btnApplyPromo.setEnabled(true);
                    tvPromoInfo.setText("Lỗi kiểm tra mã.");
                });
    }

    private Double getDoubleField(DocumentSnapshot doc, String field) {
        Object v = doc.get(field);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return null;
    }

    // ----------------- PAYMENT PROCESS -------------------

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
        for (int i = 0; i < rgPaymentMethods.getChildCount(); i++) {
            View child = rgPaymentMethods.getChildAt(i);
            child.setEnabled(false);
        }


        new Handler().postDelayed(() -> saveOrderToFirestore(method), 1500);
    }

    private void saveOrderToFirestore(String method) {
        if (eventId == null || userId == null) {
            Toast.makeText(this, "Thiếu thông tin!", Toast.LENGTH_SHORT).show();
            resetPaymentUi();
            return;
        }

        final DocumentReference eventRef = db.collection("events").document(eventId);
        final DocumentReference ordersRef = db.collection("orders").document();
        final String orderId = ordersRef.getId();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot snapshot = transaction.get(eventRef);

                    Long availableLong = snapshot.getLong("availableSeats");
                    long available = (availableLong == null) ? 0 : availableLong;

                    if (available < quantity) {
                        throw new RuntimeException("Rất tiếc, vé vừa bán hết!");
                    }

                    String ownerId = snapshot.getString("ownerId");

                    transaction.update(eventRef, "availableSeats", available - quantity);

                    Map<String, Object> order = new HashMap<>();
                    order.put("eventId", eventId);
                    order.put("userId", userId);

                    if (ownerId != null) order.put("ownerId", ownerId);

                    // TRƯỚC transaction.set(ordersRef, order):

                    double payable = finalAmount;

// Nếu vì lý do gì finalAmount chưa set, fallback sang totalPrice
                    if (payable <= 0 && totalPrice > 0 && discountAmount <= 0) {
                        payable = totalPrice;
                    }

                    order.put("totalTickets", quantity);
                    order.put("totalAmount", payable);
                    order.put("createdAt", FieldValue.serverTimestamp());
                    order.put("status", "PAID");

                    order.put("originalAmount", totalPrice);
                    order.put("discountAmount", discountAmount);
                    if (appliedPromoCode != null) {
                        order.put("promoCode", appliedPromoCode);
                    }

                    order.put("checkedIn", false);
                    order.put("checkedInAt", null);

                    order.put("eventTitle", eventTitle);
                    order.put("paymentMethod", method);
                    order.put("quantity", quantity);
                    order.put("totalPrice", totalPrice);

                    if (ticketNames != null) order.put("ticketNames", ticketNames);
                    if (ticketType != null) order.put("ticketType", ticketType);
                    if (selectedTickets != null) order.put("tickets", selectedTickets);
                    if (selectedSeatIds != null) order.put("seats", selectedSeatIds);

                    transaction.set(ordersRef, order);
                    return null;
                })
                .addOnSuccessListener(unused -> {

                    // ⚡ Cập nhật trạng thái ghế (nếu có)
                    if (selectedSeatIds != null && !selectedSeatIds.isEmpty()) {
                        updateSeatStatusAfterPayment(eventId, selectedSeatIds);
                    }

                    // ⚡ Cập nhật sold từng loại vé
                    if (selectedTickets != null && !selectedTickets.isEmpty()) {
                        updateTicketSoldAfterPayment(eventId, selectedTickets);
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
        for (int i = 0; i < rgPaymentMethods.getChildCount(); i++) {
            View child = rgPaymentMethods.getChildAt(i);
            child.setEnabled(true);
        }

    }

    private void updateSeatStatusAfterPayment(String eventId, ArrayList<String> seatIds) {
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

    // ----------------- NEW: UPDATE SOLD -------------------

    private void updateTicketSoldAfterPayment(String eventId,
                                              ArrayList<HashMap<String, Object>> tickets) {

        WriteBatch batch = db.batch();

        for (HashMap<String, Object> t : tickets) {
            Object idObj = t.get("ticketTypeId");
            Object qtyObj = t.get("quantity");

            if (idObj == null || !(qtyObj instanceof Number)) continue;

            String ticketTypeId = String.valueOf(idObj);
            long qty = ((Number) qtyObj).longValue();
            if (qty <= 0) continue;

            DocumentReference ticketRef = db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .document(ticketTypeId);

            batch.update(ticketRef, "sold", FieldValue.increment(qty));
        }

        batch.commit()
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không thể cập nhật số vé đã bán: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // ----------------- SUCCESS SCREEN -------------------

    private void showSuccessDialog(String orderId) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_ID", orderId);
        intent.putExtra("TOTAL_QTY", quantity);
        intent.putExtra("TOTAL_PRICE", finalAmount > 0 ? finalAmount : totalPrice);
        startActivity(intent);
        finish();
    }
}
