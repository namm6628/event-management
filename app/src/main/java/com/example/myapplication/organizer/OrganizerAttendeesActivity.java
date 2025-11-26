package com.example.myapplication.organizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrganizerAttendeesActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private RecyclerView recycler;
    private TextView tvEmpty;
    private ProgressBar progress;

    private View btnExportCsv;

    private AttendeeOrderAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String eventId;

    private View btnExportExcel;

    private View btnImportDemo;


    // 🔹 giữ list hiện tại để export
    private final List<AttendeeOrder> currentList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_attendees);

        // ----- Toolbar + back -----
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Danh sách tham dự");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        


        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ----- Ánh xạ view -----
        recycler = findViewById(R.id.recyclerAttendees);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

// Nút export CSV
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnExportCsv.setOnClickListener(v -> exportCsv());

// Nút export Excel (Cloud)
        btnExportExcel = findViewById(R.id.btnExportExcel);
        btnExportExcel.setOnClickListener(v -> exportExcelFromCloud());

// Nút import demo người tham dự (Cloud Function)
        btnImportDemo = findViewById(R.id.btnImportDemo);
        btnImportDemo.setOnClickListener(v -> importAttendeesDemo());

        adapter = new AttendeeOrderAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadAttendees();

    }


    private void loadAttendees() {
        // Nếu dùng rules cho phép organizer xem orders, nhớ đã đăng nhập
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showLoading(true);
        tvEmpty.setVisibility(View.GONE);

        db.collection("orders")
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(snap -> {
                    showLoading(false);

                    List<AttendeeOrder> list = new ArrayList<>();
                    if (snap != null && !snap.isEmpty()) {
                        for (var doc : snap.getDocuments()) {
                            AttendeeOrder o = doc.toObject(AttendeeOrder.class);
                            if (o == null) continue;
                            list.add(o);
                        }
                    }

                    // 🔹 cập nhật list hiện tại để export
                    currentList.clear();
                    currentList.addAll(list);

                    adapter.submit(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Lỗi tải danh sách tham dự: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                });
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Nút back trên toolbar
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ================== Model ==================
    public static class AttendeeOrder {
        private String userId;
        private String eventId;

        private Timestamp createdAt;

        private Integer totalTickets;


        public AttendeeOrder() {
        }

        @Nullable
        public String getUserId() {
            return userId;
        }

        public String getEventId() {
            return eventId;
        }



        @Nullable
        public Integer getTotalTickets() {   // ĐỔI getter
            return totalTickets;
        }

        @Nullable
        public Timestamp getCreatedAt() {
            return createdAt;
        }
    }

    // ================== Adapter ==================
    private static class AttendeeOrderAdapter extends RecyclerView.Adapter<AttendeeOrderAdapter.VH> {

        private final List<AttendeeOrder> data = new ArrayList<>();
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());

        void submit(List<AttendeeOrder> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_attendee_order, null);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AttendeeOrder o = data.get(position);

            String user = o.getUserId() == null ? "Người dùng ẩn danh" : o.getUserId();
            h.tvUserId.setText(user);

            Integer q = o.getTotalTickets();
            h.tvQuantity.setText("Số vé: " + (q == null ? 0 : q));

            String timeText = "Thời gian không xác định";
            if (o.getCreatedAt() != null) {
                try {
                    timeText = sdf.format(o.getCreatedAt().toDate());
                } catch (Exception ignored) {}
            }
            h.tvTime.setText(timeText);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvUserId, tvQuantity, tvTime;

            VH(@NonNull View itemView) {
                super(itemView);
                tvUserId = itemView.findViewById(R.id.tvUserId);
                tvQuantity = itemView.findViewById(R.id.tvQuantity);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }

    // ================== EXPORT CSV ==================
    private void exportCsv() {
        if (currentList.isEmpty()) {
            Toast.makeText(this,
                    "Chưa có dữ liệu để xuất",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("UserId,Số vé,Thời gian đặt\n");

        SimpleDateFormat sdf =
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        for (AttendeeOrder o : currentList) {
            String userId = o.getUserId() == null ? "" : o.getUserId();
            int quantity = (o.getTotalTickets() == null) ? 0 : o.getTotalTickets();
            String timeStr = "";
            if (o.getCreatedAt() != null) {
                try {
                    timeStr = sdf.format(o.getCreatedAt().toDate());
                } catch (Exception ignored) {}
            }

            // Escape dấu phẩy đơn giản: nếu cần
            sb.append(userId.replace(",", " ")).append(",");
            sb.append(quantity).append(",");
            sb.append(timeStr.replace(",", " ")).append("\n");
        }

        String csvText = sb.toString();

        // Share dưới dạng text (copy qua Excel / Google Sheets)
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Danh sách người tham dự – Sự kiện " + eventId);
        intent.putExtra(Intent.EXTRA_TEXT, csvText);

        startActivity(Intent.createChooser(intent, "Chia sẻ CSV"));
    }

    private void exportExcelFromCloud() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);

        btnExportExcel.setEnabled(false);

        FirebaseFunctions.getInstance("asia-southeast1")
                .getHttpsCallable("exportAttendees")
                .call(data)
                .addOnSuccessListener(result -> {
                    btnExportExcel.setEnabled(true);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> res = (Map<String, Object>) result.getData();
                    String url = (String) res.get("downloadUrl");
                    if (url == null) {
                        Toast.makeText(this, "Không lấy được link file", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                })
                .addOnFailureListener(e -> {
                    btnExportExcel.setEnabled(true);
                    Toast.makeText(this, "Lỗi export: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ================== IMPORT ATTENDEES DEMO (Cloud) ==================
    private void importAttendeesDemo() {
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo danh sách attendee demo
        List<Map<String, Object>> attendees = new ArrayList<>();

        Map<String, Object> a1 = new HashMap<>();
        a1.put("name", "Nguyễn Văn A");
        a1.put("email", "vana@example.com");
        a1.put("phone", "0900123456");
        // có thể truyền ticketTypeId nếu thích
        attendees.add(a1);

        Map<String, Object> a2 = new HashMap<>();
        a2.put("name", "Trần Thị B");
        a2.put("email", "tranb@example.com");
        a2.put("phone", "0900987654");
        attendees.add(a2);

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("attendees", attendees);

        btnImportDemo.setEnabled(false);

        FirebaseFunctions.getInstance("asia-southeast1")
                .getHttpsCallable("importAttendeesFromApp")
                .call(data)
                .addOnSuccessListener(result -> {
                    btnImportDemo.setEnabled(true);
                    Toast.makeText(this, "Import demo thành công!", Toast.LENGTH_SHORT).show();
                    // Nếu sau này bạn có màn xem attendees riêng, có thể reload ở đây
                })
                .addOnFailureListener(e -> {
                    btnImportDemo.setEnabled(true);
                    Toast.makeText(this,
                            "Lỗi import: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }


}
