package com.example.myapplication.organizer;

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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerAttendeesActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private RecyclerView recycler;
    private TextView tvEmpty;
    private ProgressBar progress;

    private AttendeeOrderAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String eventId;

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

        // ----- Lấy eventId -----
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

        adapter = new AttendeeOrderAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        // Load dữ liệu
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
        private Integer quantity;
        private Timestamp createdAt;

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
        public Integer getQuantity() {
            return quantity;
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

            Integer q = o.getQuantity();
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
}
