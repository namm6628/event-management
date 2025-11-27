package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity; // Đổi thành MainActivity của bạn
import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

public class OrderSuccessActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "EXTRA_ORDER_ID";
    public static final String EXTRA_TOTAL_QTY = "EXTRA_TOTAL_QTY";
    public static final String EXTRA_TOTAL_PRICE = "EXTRA_TOTAL_PRICE";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        // 1. Nhận dữ liệu
        String orderId = getIntent().getStringExtra("ORDER_ID");
        int quantity = getIntent().getIntExtra("TOTAL_QTY", 0);
        double price = getIntent().getDoubleExtra("TOTAL_PRICE", 0);

        // 2. Ánh xạ View
        TextView tvOrderId = findViewById(R.id.tvOrderId);
        TextView tvTotalQty = findViewById(R.id.tvTotalQty);
        TextView tvTotalPrice = findViewById(R.id.tvTotalPrice);
        MaterialButton btnBackHome = findViewById(R.id.btnBackHome);
        MaterialButton btnViewTickets = findViewById(R.id.btnViewTickets);

        // 3. Hiển thị
        tvOrderId.setText(orderId != null ? orderId : "---");
        tvTotalQty.setText(String.format(Locale.getDefault(), "%02d", quantity));

        String priceStr = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + " ₫";
        if (price == 0) priceStr = "Miễn phí";
        tvTotalPrice.setText(priceStr);

        // 4. Xử lý nút bấm
        btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnViewTickets.setOnClickListener(v -> {
            // TODO: Chuyển sang tab "Vé của tôi" trong Profile (Sẽ làm sau)
            // Tạm thời về Home
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
