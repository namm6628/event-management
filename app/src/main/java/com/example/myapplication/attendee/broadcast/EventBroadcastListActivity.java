package com.example.myapplication.attendee.broadcast;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.EventBroadcast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class EventBroadcastListActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private RecyclerView rv;
    private View layoutEmpty;
    private BroadcastAdapter adapter;

    private String eventId;
    private String eventTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_broadcast_list);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);

        if (eventId == null) {
            Toast.makeText(this, "Thiếu eventId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Thông báo từ BTC");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rv = findViewById(R.id.rvBroadcasts);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BroadcastAdapter();
        rv.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        db.collection("eventBroadcasts")
                .whereEqualTo("eventId", eventId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    List<EventBroadcast> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        EventBroadcast b = d.toObject(EventBroadcast.class);
                        if (b != null) {
                            b.setId(d.getId());
                            // nếu chưa có eventTitle trong document thì fallback
                            if (b.getEventTitle() == null || b.getEventTitle().isEmpty()) {
                                b.setEventTitle(eventTitle);
                            }
                            list.add(b);
                        }
                    }

                    if (list.isEmpty()) {
                        rv.setVisibility(View.GONE);
                        layoutEmpty.setVisibility(View.VISIBLE);
                    } else {
                        adapter.submitList(list);
                        layoutEmpty.setVisibility(View.GONE);
                        rv.setVisibility(View.VISIBLE);
                    }
                });
    }
}
