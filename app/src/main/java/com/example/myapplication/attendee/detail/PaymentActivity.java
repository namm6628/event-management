package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

        // 4. Sự kiện nút Thanh toán
        btnConfirmPayment.setOnClickListener(v -> processPayment());
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
        new AlertDialog.Builder(this)
                .setTitle("Thanh toán thành công!")
                .setMessage("Bạn đã đặt vé thành công. Vé đã được gửi vào mục 'Vé của tôi'.")
                .setPositiveButton("OK", (d, w) -> {
                    // Quay về màn hình trước
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}