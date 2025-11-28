package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

public class OrderSuccessActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "ORDER_ID";
    public static final String EXTRA_TOTAL_QTY = "TOTAL_QTY";
    public static final String EXTRA_TOTAL_PRICE = "TOTAL_PRICE";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        // 1. Nhận dữ liệu
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        int quantity   = getIntent().getIntExtra(EXTRA_TOTAL_QTY, 0);
        double price   = getIntent().getDoubleExtra(EXTRA_TOTAL_PRICE, 0);

        // 2. View
        TextView tvOrderId    = findViewById(R.id.tvOrderId);
        TextView tvTotalQty   = findViewById(R.id.tvTotalQty);
        TextView tvTotalPrice = findViewById(R.id.tvTotalPrice);
        MaterialButton btnBackHome    = findViewById(R.id.btnBackHome);
        MaterialButton btnViewTickets = findViewById(R.id.btnViewTickets);

        // 3. Hiển thị
        tvOrderId.setText(orderId != null ? orderId : "---");
        tvTotalQty.setText(String.format(Locale.getDefault(), "%02d", quantity));

        String priceStr = NumberFormat
                .getNumberInstance(new Locale("vi", "VN"))
                .format(price) + " ₫";
        if (price == 0) priceStr = "Miễn phí";
        tvTotalPrice.setText(priceStr);

        // 4. Điều hướng

        // 👉 Nút "Về trang chủ"
        btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(MainActivity.EXTRA_START_DEST, R.id.homeFragment);
            startActivity(intent);
            finish();
        });

        // 👉 Nút "Vé của tôi"
        btnViewTickets.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(MainActivity.EXTRA_START_DEST, R.id.ticketsFragment);
            startActivity(intent);
            finish();
        });
    }
}
