package com.example.myapplication.organizer.checkin;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class OrganizerCheckinListActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";
    public static final String EXTRA_EVENT_TITLE = "EXTRA_EVENT_TITLE";

    private FirebaseFirestore db;
    private OrganizerCheckinAdapter adapter;
    private TextView tvSummary, tvEmpty;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_checkin_list);

        db = FirebaseFirestore.getInstance();

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        String eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);

        // Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String title = "Check-in";
            if (eventTitle != null && !eventTitle.isEmpty()) {
                title = "Check-in: " + eventTitle;
            }
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvCheckins);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerCheckinAdapter();
        rv.setAdapter(adapter);

        if (eventId == null || eventId.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Thiếu EVENT_ID, không thể tải danh sách check-in");
            return;
        }

        listenCheckins(eventId);
    }

    private void listenCheckins(String eventId) {
        Query q = db.collection("orders")
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("checkedIn", true)
                .orderBy("checkedInAt", Query.Direction.DESCENDING);

        registration = q.addSnapshotListener((snap, e) -> {
            if (e != null) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("Lỗi tải dữ liệu: " + e.getMessage());
                return;
            }

            if (snap == null || snap.isEmpty()) {
                adapter.submit(new ArrayList<>());
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("Chưa có vé nào được check-in");
                tvSummary.setText("Đã check-in: 0 đơn • 0 vé");
                return;
            }

            List<DocumentSnapshot> docs = snap.getDocuments();
            adapter.submit(docs);

            tvEmpty.setVisibility(View.GONE);

            // Tính tổng đơn / tổng vé
            long totalOrders = docs.size();
            long totalTickets = 0;
            for (DocumentSnapshot d : docs) {
                Long t = d.getLong("totalTickets");
                if (t != null) totalTickets += t;
            }

            tvSummary.setText("Đã check-in: " + totalOrders + " đơn • " + totalTickets + " vé");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) {
            registration.remove();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
