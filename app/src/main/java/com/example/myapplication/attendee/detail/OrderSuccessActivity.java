package com.example.myapplication.attendee.detail;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        int totalQty = getIntent().getIntExtra(EXTRA_TOTAL_QTY, 0);
        double totalPrice = getIntent().getDoubleExtra(EXTRA_TOTAL_PRICE, 0d);

        TextView tvOrderSummary = findViewById(R.id.tvOrderSummary);
        MaterialButton btnDone = findViewById(R.id.btnDone);

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        String priceStr = totalPrice <= 0 ? "Miễn phí" : nf.format(totalPrice) + " đ";

        String summary = "Mã đơn: " + orderId
                + "\nSố vé: " + totalQty
                + "\nTổng tiền: " + priceStr;

        tvOrderSummary.setText(summary);

        btnDone.setOnClickListener(v -> {
            // Đóng hết stack attendee nếu muốn
            finish();
        });
    }
}
