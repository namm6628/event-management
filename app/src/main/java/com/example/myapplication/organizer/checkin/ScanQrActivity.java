package com.example.myapplication.organizer.checkin;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanQrActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeView;
    private FirebaseFirestore db;

    private String expectedEventId;   // EVENT_ID truyền từ organizer
    private boolean isProcessing = false;
    private boolean useFrontCamera = false;

    // UI
    private LinearLayout layoutStatus;
    private TextView tvStatusTitle, tvStatusDetail;
    private Button btnToggleCamera, btnClose;

    private final EventRemoteDataSource remote = new EventRemoteDataSource();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        barcodeView     = findViewById(R.id.barcodeScanner);
        layoutStatus    = findViewById(R.id.layoutStatus);
        tvStatusTitle   = findViewById(R.id.tvStatusTitle);
        tvStatusDetail  = findViewById(R.id.tvStatusDetail);
        btnToggleCamera = findViewById(R.id.btnToggleCamera);
        btnClose        = findViewById(R.id.btnClose);

        db = FirebaseFirestore.getInstance();
        expectedEventId = getIntent().getStringExtra("EVENT_ID");

        if (expectedEventId == null || expectedEventId.isEmpty()) {
            Toast.makeText(this, "Thiếu EVENT_ID, không thể check-in", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // user hiện tại
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            Toast.makeText(this, "Bạn cần đăng nhập bằng email để check-in", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String email = user.getEmail();
        String uid   = user.getUid();

        // 🔐 kiểm tra quyền: owner hoặc collaborator(role = "checkin")
        remote.canUserCheckin(expectedEventId, email, uid)
                .addOnSuccessListener(allowed -> {
                    if (!allowed) {
                        Toast.makeText(this, "Bạn không có quyền check-in sự kiện này", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        // ✅ Có quyền → khởi tạo scanner
                        initScanner();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    /** Chỉ gọi khi đã check quyền OK */
    private void initScanner() {
        applyCameraSettings();

        barcodeView.decodeContinuous(callback);

        btnToggleCamera.setOnClickListener(v -> {
            useFrontCamera = !useFrontCamera;
            applyCameraSettings();
        });

        btnClose.setOnClickListener(v -> finish());

        setIdleStatus();
    }

    private void applyCameraSettings() {
        CameraSettings cs = barcodeView.getBarcodeView().getCameraSettings();
        cs.setRequestedCameraId(useFrontCamera ? 1 : 0); // 0: sau, 1: trước
        barcodeView.getBarcodeView().setCameraSettings(cs);
        barcodeView.pause();
        barcodeView.resume();
    }

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null) return;
            if (isProcessing) return;

            isProcessing = true;
            handleQrContent(result.getText());
        }
    };

    private void handleQrContent(String content) {
        // content dạng: eventId=...;orderId=...;userId=...
        Map<String, String> map = parseQr(content);
        String eventId = map.get("eventId");
        String orderId = map.get("orderId");
        String userId  = map.get("userId");

        if (eventId == null || orderId == null || userId == null) {
            setErrorStatus("Mã QR không hợp lệ", "Không tìm thấy đủ thông tin eventId / orderId / userId.");
            resumeScan();
            return;
        }

        if (expectedEventId != null && !expectedEventId.equals(eventId)) {
            setErrorStatus("Sai sự kiện", "Mã QR này không thuộc sự kiện đang quét.");
            resumeScan();
            return;
        }

        db.collection("orders")
                .document(orderId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        setErrorStatus("Không tìm thấy vé", "Không có đơn hàng tương ứng với mã QR.");
                        resumeScan();
                        return;
                    }
                    verifyAndCheckIn(doc, eventId, userId);
                })
                .addOnFailureListener(e -> {
                    setErrorStatus("Lỗi kết nối", "Lỗi đọc dữ liệu: " + e.getMessage());
                    resumeScan();
                });
    }

    private void verifyAndCheckIn(DocumentSnapshot doc, String eventIdFromQr, String userIdFromQr) {

        String dbEventId = doc.getString("eventId");
        String dbUserId  = doc.getString("userId");
        String status    = doc.getString("status");
        Boolean checkedIn = doc.getBoolean("checkedIn");

        boolean isPaid = status != null && status.equalsIgnoreCase("paid");

        if (!isPaid) {
            setErrorStatus("Vé chưa thanh toán", "Trạng thái đơn hàng: " + (status == null ? "null" : status));
            resumeScan();
            return;
        }

        if (dbEventId == null || !dbEventId.equals(eventIdFromQr)) {
            setErrorStatus("Mã QR sai sự kiện", "eventId trong đơn không khớp với QR.");
            resumeScan();
            return;
        }

        if (dbUserId == null || !dbUserId.equals(userIdFromQr)) {
            setErrorStatus("Mã QR sai tài khoản", "userId trong đơn không khớp với QR.");
            resumeScan();
            return;
        }

        if (checkedIn != null && checkedIn) {
            // ĐÃ CHECK-IN RỒI → vẫn show info vé + ghế
            String detail = buildTicketDetail(doc);
            setErrorStatus("Vé đã check-in", detail);
            resumeScan();
            return;
        }

        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (currentUid == null) {
            setErrorStatus("Bạn chưa đăng nhập", "Hãy đăng nhập tài khoản organizer để check-in.");
            resumeScan();
            return;
        }

        // ✅ Tiến hành check-in
        Map<String, Object> updates = new HashMap<>();
        updates.put("checkedIn", true);
        updates.put("checkedInAt", Timestamp.now());

        doc.getReference().update(updates)
                .addOnSuccessListener(unused -> {
                    String detail = buildTicketDetail(doc);
                    setSuccessStatus("Vé hợp lệ – Check-in OK", detail);
                    // quét người tiếp theo
                    resumeScan();
                })
                .addOnFailureListener(e -> {
                    setErrorStatus("Lỗi khi lưu check-in", e.getMessage());
                    resumeScan();
                });
    }

    /**
     * Đọc ghế từ doc.orders:
     *  - Nếu có field "seats" (array string) → hiển thị danh sách ghế
     *  - Nếu có "tickets" (array map) → ghép type + seat label (nếu có)
     */
    private String buildTicketDetail(DocumentSnapshot doc) {
        StringBuilder sb = new StringBuilder();

        String eventTitle = doc.getString("eventTitle");
        Double totalAmount = doc.getDouble("totalAmount");
        Long totalTickets = doc.getLong("totalTickets");

        if (eventTitle != null) {
            sb.append(eventTitle).append("\n");
        }

        if (totalTickets != null) {
            sb.append("Số vé: ").append(totalTickets).append("\n");
        }

        if (totalAmount != null) {
            sb.append("Tổng tiền: ").append(totalAmount.longValue()).append(" đ\n");
        }

        // Ghế: từ field seats: [ "A1", "B1", ... ]
        Object seatsObj = doc.get("seats");
        if (seatsObj instanceof List) {
            List<?> seats = (List<?>) seatsObj;
            if (!seats.isEmpty()) {
                sb.append("Ghế: ");
                for (int i = 0; i < seats.size(); i++) {
                    Object s = seats.get(i);
                    if (s != null) {
                        sb.append(s.toString());
                        if (i < seats.size() - 1) sb.append(", ");
                    }
                }
                sb.append("\n");
            }
        }

        // Nếu muốn chi tiết hơn theo tickets[]
        Object ticketsObj = doc.get("tickets");
        if (ticketsObj instanceof List) {
            List<?> tickets = (List<?>) ticketsObj;
            if (!tickets.isEmpty()) {
                sb.append("Chi tiết vé:\n");
                for (Object t : tickets) {
                    if (!(t instanceof Map)) continue;
                    Map<?,?> map = (Map<?,?>) t;

                    Object type = map.get("type");
                    Object label = map.get("label");
                    Object price = map.get("price");

                    sb.append("- ");
                    if (type != null) sb.append(type.toString());
                    if (label != null) sb.append(" (").append(label.toString()).append(")");
                    if (price instanceof Number) {
                        sb.append(" – ").append(((Number) price).longValue()).append(" đ");
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private Map<String, String> parseQr(String content) {
        Map<String, String> map = new HashMap<>();
        String[] parts = content.split(";");
        for (String p : parts) {
            String[] kv = p.split("=");
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    private void setIdleStatus() {
        layoutStatus.setBackgroundColor(0xFF222222);
        tvStatusTitle.setText("Đang chờ quét...");
        tvStatusDetail.setText("Đưa mã QR vé vào khung camera để check-in.");
    }

    private void setSuccessStatus(String title, String detail) {
        layoutStatus.setBackgroundColor(0xFF1B5E20); // xanh đậm
        tvStatusTitle.setText(title);
        tvStatusDetail.setText(detail);
    }

    private void setErrorStatus(String title, String detail) {
        layoutStatus.setBackgroundColor(0xFFB71C1C); // đỏ đậm
        tvStatusTitle.setText(title);
        tvStatusDetail.setText(detail);
    }

    private void resumeScan() {
        isProcessing = false;
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null) barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) barcodeView.pause();
    }
}
