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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private TextView tvEventName, tvQuantity, tvTotalPrice, tvTotalPriceInfo, tvTicketType;
    private RadioGroup rgPaymentMethods;
    private MaterialButton btnConfirmPayment;

    private String eventId, eventTitle, userId, ticketNames;
    private int quantity;
    private double totalPrice;
    private ArrayList<HashMap<String, Object>> selectedTickets;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private View cardSplitBill;
    private TextView tvSplitInfo;
    private View btnShareBill;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        // 1. Nhận dữ liệu truyền sang
        Intent intent = getIntent();
        eventId = intent.getStringExtra("eventId");
        eventTitle = intent.getStringExtra("eventTitle");
        quantity = intent.getIntExtra("quantity", 1);
        totalPrice = intent.getDoubleExtra("totalPrice", 0);
        userId = FirebaseAuth.getInstance().getUid();
        ticketNames = intent.getStringExtra("ticketNames");
        selectedTickets = (ArrayList<HashMap<String, Object>>) intent.getSerializableExtra("selectedTickets");

        // 2. Ánh xạ View
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvTotalPriceInfo = findViewById(R.id.tvTotalPriceInfo); // Ánh xạ
        tvTicketType = findViewById(R.id.tvTicketType);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        cardSplitBill = findViewById(R.id.cardSplitBill);
        tvSplitInfo = findViewById(R.id.tvSplitInfo);
        btnShareBill = findViewById(R.id.btnShareBill);

        tvEventName = findViewById(R.id.tvEventName);
        tvQuantity = findViewById(R.id.tvQuantity);
        rgPaymentMethods = findViewById(R.id.rgPaymentMethods);
        btnConfirmPayment = findViewById(R.id.btnConfirmPayment);

        // 3. Hiển thị
        tvEventName.setText(eventTitle);
        tvQuantity.setText(quantity + " vé");
        if (ticketNames != null && !ticketNames.isEmpty()) {
            tvTicketType.setText(ticketNames);
        } else {
            tvTicketType.setText("Vé Tham Dự");
        }

        String priceStr = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(totalPrice) + " ₫";
        if (totalPrice == 0) priceStr = "Miễn phí";
        tvTotalPrice.setText(priceStr);
        tvTotalPriceInfo.setText(priceStr);
        setupSplitBill();

        // 4. Sự kiện nút Thanh toán
        btnConfirmPayment.setOnClickListener(v -> processPayment());
    }

    private void setupSplitBill() {
        // Chỉ hiện nếu mua > 1 vé và có tiền
        if (quantity > 1 && totalPrice > 0) {
            cardSplitBill.setVisibility(View.VISIBLE);

            // 1. Tính tiền mỗi người
            double pricePerPerson = totalPrice / quantity;
            String priceStr = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(pricePerPerson) + " ₫";

            tvSplitInfo.setText("Tổng: " + quantity + " người. Mỗi người: " + priceStr);

            // 2. Xử lý nút Share
            btnShareBill.setOnClickListener(v -> {
                String msg = "Alo mọi người ơi! 📢\n" +
                        "Mình đang đặt vé đi sự kiện: " + eventTitle + "\n" +
                        "Tổng tiền: " + NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(totalPrice) + "đ (" + quantity + " vé)\n" +
                        "👉 Chia ra mỗi người: " + priceStr + "\n" +
                        "Mọi người chuyển khoản cho mình sớm nhé! 💸";

                // Copy vào clipboard
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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

    private void processPayment() {
        // Kiểm tra xem đã chọn phương thức chưa
        int selectedId = rgPaymentMethods.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Vui lòng chọn phương thức thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rb = findViewById(selectedId);
        String method = rb.getText().toString();

        // Giả lập loading (Mô phỏng gọi Momo/ZaloPay...)
        btnConfirmPayment.setText("Đang xử lý...");
        btnConfirmPayment.setEnabled(false);
        rgPaymentMethods.setEnabled(false);

        new Handler().postDelayed(() -> {
            // Sau 1.5 giây -> Gọi hàm lưu Database
            saveOrderToFirestore(method);
        }, 1500);
    }

    private void saveOrderToFirestore(String method) {
        // Dùng Transaction để đảm bảo vé không bị âm
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            var eventRef = db.collection("events").document(eventId);
            var snapshot = transaction.get(eventRef);

            Long availableLong = snapshot.getLong("availableSeats");
            long available = (availableLong == null) ? 0 : availableLong;

            if (available < quantity) {
                throw new RuntimeException("Rất tiếc, vé vừa bán hết!");
            }

            // 1. Trừ vé
            transaction.update(eventRef, "availableSeats", available - quantity);

            // 2. Tạo đơn hàng
            var ordersRef = db.collection("orders").document();
            Map<String, Object> order = new HashMap<>();
            order.put("userId", userId);
            order.put("eventId", eventId);
            order.put("eventTitle", eventTitle);
            order.put("quantity", quantity);
            order.put("totalPrice", totalPrice);
            order.put("paymentMethod", method);
            order.put("status", "PAID");
            order.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(ordersRef, order);
            return null;

        }).addOnSuccessListener(unused -> {
            showSuccessDialog();
        }).addOnFailureListener(e -> {
            btnConfirmPayment.setText("Thanh toán ngay");
            btnConfirmPayment.setEnabled(true);
            rgPaymentMethods.setEnabled(true);
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void showSuccessDialog() {
        // [THAY ĐỔI] - Chuyển sang màn hình OrderSuccessActivity
        Intent intent = new Intent(this, OrderSuccessActivity.class);

        // Truyền dữ liệu cần thiết để hiển thị
        // Lưu ý: orderId lấy ở đâu?
        // Trong code saveOrderToFirestore cũ, bạn chưa lấy được ID của order vừa tạo.
        // Hãy sửa lại saveOrderToFirestore một chút để lấy ID.

        // Ở đây tạm thời mình truyền ID giả hoặc để trống nếu chưa lấy được
        intent.putExtra("ORDER_ID", "ORDER_" + System.currentTimeMillis());
        intent.putExtra("TOTAL_QTY", quantity);
        intent.putExtra("TOTAL_PRICE", totalPrice);

        startActivity(intent);
        finish(); // Đóng PaymentActivity để không back lại được
    }
}
