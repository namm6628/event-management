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

    private static final long LOYALTY_VND_PER_POINT = 100L;

    private TextView tvEventName, tvQuantity, tvTotalPrice, tvTicketType;
    private TextView tvSubtotal, tvDiscount, tvTotalPriceInfo;
    private RadioGroup rgPaymentMethods;
    private MaterialButton btnConfirmPayment;

    private String eventId, eventTitle, userId, ticketNames, ticketType;
    private int quantity;
    private double totalPrice;

    // Giảm giá
    private double discountPromo = 0;
    private double discountPoints = 0;
    private double discountAmount = 0;
    private double finalAmount = 0;
    private String appliedPromoCode = null;

    // Loyalty
    private long currentLoyaltyPoints = 0;
    private long pointsUsed = 0;

    private ArrayList<HashMap<String, Object>> selectedTickets;
    private ArrayList<String> selectedSeatIds;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private View cardSplitBill;
    private TextView tvSplitInfo;
    private View btnShareBill;

    private EditText edtPromoCode;
    private MaterialButton btnApplyPromo;
    private TextView tvPromoInfo;

    // Loyalty views
    private TextView tvPointsSummary;
    private EditText edtUsePoints;
    private MaterialButton btnApplyPoints;
    private TextView tvPointsInfo;

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
        tvTicketType = findViewById(R.id.tvTicketType);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvTotalPriceInfo = findViewById(R.id.tvTotalPriceInfo);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cardSplitBill = findViewById(R.id.cardSplitBill);
        tvSplitInfo = findViewById(R.id.tvSplitInfo);
        btnShareBill = findViewById(R.id.btnShareBill);

        edtPromoCode = findViewById(R.id.edtPromoCode);
        btnApplyPromo = findViewById(R.id.btnApplyPromo);
        tvPromoInfo = findViewById(R.id.tvPromoInfo);

        tvPointsSummary = findViewById(R.id.tvPointsSummary);
        edtUsePoints = findViewById(R.id.edtUsePoints);
        btnApplyPoints = findViewById(R.id.btnApplyPoints);
        tvPointsInfo = findViewById(R.id.tvPointsInfo);

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

        discountPromo = 0;
        discountPoints = 0;
        updatePriceViews();

        setupSplitBill();

        btnApplyPromo.setOnClickListener(v -> applyPromoCode());
        btnApplyPoints.setOnClickListener(v -> applyLoyaltyPoints());
        btnConfirmPayment.setOnClickListener(v -> processPayment());

        loadUserLoyaltyPoints();
    }

    private void updatePriceViews() {
        discountAmount = discountPromo + discountPoints;
        if (discountAmount > totalPrice) discountAmount = totalPrice;

        finalAmount = totalPrice - discountAmount;
        if (finalAmount < 0) finalAmount = 0;

        String subtotalStr = totalPrice <= 0 ? "Miễn phí" : nf.format(totalPrice) + " đ";
        String discountStr = discountAmount <= 0 ? "0 đ" : "- " + nf.format(discountAmount) + " đ";
        String finalStr = finalAmount <= 0 ? "Miễn phí" : nf.format(finalAmount) + " đ";

        if (tvSubtotal != null) tvSubtotal.setText(subtotalStr);

        if (tvDiscount != null) {
            if (discountAmount <= 0) {
                tvDiscount.setText("0 đ");
            } else if (discountPoints > 0 && pointsUsed > 0) {
                tvDiscount.setText(nf.format(discountAmount) + " đ"
                        + " (" + nf.format(discountPoints) + " đ từ " + pointsUsed + " điểm)");
            } else {
                tvDiscount.setText(nf.format(discountAmount) + " đ");
            }
        }

        if (tvTotalPriceInfo != null) tvTotalPriceInfo.setText(finalStr);
        if (tvTotalPrice != null) tvTotalPrice.setText(finalStr); // bottom bar
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
                    if (price > 0) detail.append(": ").append(nf.format(price)).append(" đ");
                }
            }

            if (detail.length() == 0) {
                detail.append("Tổng tiền: ").append(nf.format(totalPrice)).append(" đ");
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
                        if (price > 0) msgDetail.append(": ").append(nf.format(price)).append(" đ");
                    }
                } else {
                    msgDetail.append("- Tổng ").append(quantity)
                            .append(" vé: ").append(nf.format(totalPrice)).append(" đ");
                }

                String msg = "Alo mọi người ơi! \n"
                        + "Mình đang đặt vé đi sự kiện: " + eventTitle + "\n"
                        + "Tổng: " + quantity + " vé, tổng tiền: "
                        + nf.format(totalPrice) + " đ\n"
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

    private void loadUserLoyaltyPoints() {
        if (userId == null) return;

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Long pts = doc.getLong("loyaltyPoints");
                    currentLoyaltyPoints = pts != null ? pts : 0L;

                    if (tvPointsSummary != null) {
                        tvPointsSummary.setText("Điểm hiện tại: " + currentLoyaltyPoints);
                    }
                })
                .addOnFailureListener(e -> {
                });
    }

    private void applyLoyaltyPoints() {
        if (totalPrice <= 0) {
            Toast.makeText(this, "Đơn miễn phí không dùng điểm được", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentLoyaltyPoints <= 0) {
            Toast.makeText(this, "Bạn chưa có điểm tích lũy", Toast.LENGTH_SHORT).show();
            return;
        }

        String raw = edtUsePoints.getText().toString().trim();
        long req;
        try {
            req = Long.parseLong(raw.isEmpty() ? "0" : raw);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Số điểm không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (req <= 0) {
            pointsUsed = 0;
            discountPoints = 0;
            if (tvPointsInfo != null) {
                tvPointsInfo.setText("Không sử dụng điểm cho đơn này.");
            }
            updatePriceViews();
            return;
        }

        if (req > currentLoyaltyPoints) {
            Toast.makeText(this,
                    "Bạn chỉ có " + currentLoyaltyPoints + " điểm.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        double remainingAfterPromo = totalPrice - discountPromo;
        if (remainingAfterPromo <= 0) {
            Toast.makeText(this, "Đơn đã được giảm hết bằng mã khuyến mãi", Toast.LENGTH_SHORT).show();
            return;
        }

        long maxByAmount = (long) (remainingAfterPromo / LOYALTY_VND_PER_POINT);
        if (maxByAmount <= 0) {
            Toast.makeText(this, "Số tiền còn lại quá nhỏ để dùng điểm", Toast.LENGTH_SHORT).show();
            return;
        }

        long maxUsable = Math.min(currentLoyaltyPoints, maxByAmount);
        if (maxUsable <= 0) {
            Toast.makeText(this, "Không thể dùng điểm cho đơn này", Toast.LENGTH_SHORT).show();
            return;
        }

        long use = req;
        if (req > maxUsable) {
            use = maxUsable;
            Toast.makeText(this,
                    "Đơn này chỉ dùng tối đa " + maxUsable + " điểm. Đã áp dụng " + maxUsable + " điểm.",
                    Toast.LENGTH_SHORT).show();
        }

        pointsUsed = use;
        discountPoints = pointsUsed * LOYALTY_VND_PER_POINT;

        if (tvPointsInfo != null) {
            tvPointsInfo.setText(
                    "Đã dùng " + pointsUsed + " điểm, giảm "
                            + nf.format(discountPoints) + " đ."
            );
        }

        updatePriceViews();
    }

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
                        discountPromo = 0;
                        tvPromoInfo.setText("Mã không hợp lệ.");
                        updatePriceViews();
                        return;
                    }

                    Boolean active = doc.getBoolean("active");
                    if (active != null && !active) {
                        appliedPromoCode = null;
                        discountPromo = 0;
                        tvPromoInfo.setText("Mã đã bị khoá.");
                        updatePriceViews();
                        return;
                    }

                    Timestamp expiry = doc.getTimestamp("expiry");
                    if (expiry != null && expiry.toDate().before(new Date())) {
                        appliedPromoCode = null;
                        discountPromo = 0;
                        tvPromoInfo.setText("Mã đã hết hạn.");
                        updatePriceViews();
                        return;
                    }

                    Double value = getDoubleField(doc, "value");
                    String type = doc.getString("type");

                    if (value == null || value <= 0) {
                        appliedPromoCode = null;
                        discountPromo = 0;
                        tvPromoInfo.setText("Mã không hợp lệ.");
                        updatePriceViews();
                        return;
                    }

                    double discount;
                    if ("PERCENT".equalsIgnoreCase(type)) {
                        discount = totalPrice * (value / 100d);
                    } else {
                        discount = value;
                    }

                    discountPromo = Math.min(discount, totalPrice);
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

        double payable = finalAmount;
        if (payable <= 0 && totalPrice > 0 && discountAmount <= 0) {
            payable = totalPrice;
        }
        final double finalPayable = payable;

        final long earnedPoints =
                (finalPayable <= 0 || LOYALTY_VND_PER_POINT <= 0)
                        ? 0
                        : (long) ((finalPayable * 0.1d) / LOYALTY_VND_PER_POINT);

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

                    order.put("totalTickets", quantity);
                    order.put("totalAmount", finalPayable);
                    order.put("createdAt", FieldValue.serverTimestamp());
                    order.put("status", "PAID");

                    order.put("originalAmount", totalPrice);
                    order.put("discountAmount", discountAmount);
                    if (appliedPromoCode != null) order.put("promoCode", appliedPromoCode);

                    if (pointsUsed > 0) {
                        order.put("pointsUsed", pointsUsed);
                        order.put("pointsDiscountAmount", discountPoints);
                    }
                    if (earnedPoints > 0) {
                        order.put("earnedPoints", earnedPoints);
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

                    updateUserLoyalty(pointsUsed, earnedPoints);

                    if (selectedSeatIds != null && !selectedSeatIds.isEmpty()) {
                        updateSeatStatusAfterPayment(eventId, selectedSeatIds);
                    }
                    if (selectedTickets != null && !selectedTickets.isEmpty()) {
                        updateTicketSoldAfterPayment(eventId, selectedTickets);
                    }

                    createEventAttendee(orderId);

                    showSuccessDialog(orderId);
                })
                .addOnFailureListener(e -> {
                    resetPaymentUi();
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUserLoyalty(long used, long earned) {
        if (userId == null) return;

        Map<String, Object> updates = new HashMap<>();
        long net = earned - used;

        if (net != 0) {
            updates.put("loyaltyPoints", FieldValue.increment(net));
        }
        if (earned > 0) {
            updates.put("lifetimePoints", FieldValue.increment(earned));
        }

        if (updates.isEmpty()) return;

        db.collection("users")
                .document(userId)
                .update(updates)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không cập nhật được điểm tích lũy", Toast.LENGTH_SHORT).show()
                );
    }

    private void resetPaymentUi() {
        btnConfirmPayment.setText("Thanh toán");
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

    private void createEventAttendee(String orderId) {
        if (userId == null || eventId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("userId", userId);
        data.put("eventId", eventId);
        data.put("quantity", quantity);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("checkedIn", false);

        data.put("eventTitle", eventTitle);
        if (ticketNames != null) {
            data.put("ticketNames", ticketNames);
        } else if (ticketType != null) {
            data.put("ticketType", ticketType);
        }

        FirebaseFirestore.getInstance()
                .collection("eventAttendees")
                .document(orderId)
                .set(data)
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }


    private void showSuccessDialog(String orderId) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_ID", orderId);
        intent.putExtra("TOTAL_QTY", quantity);
        intent.putExtra("TOTAL_PRICE", finalAmount > 0 ? finalAmount : totalPrice);
        startActivity(intent);
        finish();
    }
}
