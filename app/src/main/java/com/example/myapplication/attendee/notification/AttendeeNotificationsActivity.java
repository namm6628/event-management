package com.example.myapplication.attendee.notification;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.attendee.broadcast.BroadcastAdapter;
import com.example.myapplication.common.model.EventBroadcast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttendeeNotificationsActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private RecyclerView rv;
    private View layoutEmpty;
    private BroadcastAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_broadcast_list); // reuse layout

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Thông báo");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rv = findViewById(R.id.rvBroadcasts);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BroadcastAdapter();
        rv.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        if (auth.getCurrentUser() == null) {
            showEmpty();
            Toast.makeText(this, "Bạn cần đăng nhập để xem thông báo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // TODO: nếu field userId / status khác thì sửa ở đây
        db.collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "paid")
                .get()
                .addOnSuccessListener(orderSnap -> {
                    if (orderSnap.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    Set<String> eventIds = new HashSet<>();
                    for (DocumentSnapshot d : orderSnap) {
                        String eventId = d.getString("eventId"); // TODO: đổi nếu field khác
                        if (eventId != null) eventIds.add(eventId);
                    }

                    if (eventIds.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    List<String> idsList = new ArrayList<>(eventIds);
                    List<Task<QuerySnapshot>> tasks = new ArrayList<>();

                    // whereIn tối đa 10 phần tử → chia batch
                    for (int i = 0; i < idsList.size(); i += 10) {
                        List<String> sub = idsList.subList(i, Math.min(i + 10, idsList.size()));
                        Task<QuerySnapshot> t = db.collection("eventBroadcasts")
                                .whereIn("eventId", sub)
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .get();
                        tasks.add(t);
                    }

                    Tasks.whenAllSuccess(tasks)
                            .addOnSuccessListener(results -> {
                                List<EventBroadcast> list = new ArrayList<>();
                                for (Object o : results) {
                                    QuerySnapshot qs = (QuerySnapshot) o;
                                    for (DocumentSnapshot d : qs) {
                                        EventBroadcast b = d.toObject(EventBroadcast.class);
                                        if (b != null) {
                                            b.setId(d.getId());
                                            b.setEventTitle(d.getString("eventTitle"));
                                            list.add(b);
                                        }
                                    }
                                }

                                if (list.isEmpty()) {
                                    showEmpty();
                                } else {
                                    adapter.submitList(list);
                                    layoutEmpty.setVisibility(View.GONE);
                                    rv.setVisibility(View.VISIBLE);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Lỗi: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                showEmpty();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmpty();
                });
    }

    private void showEmpty() {
        rv.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }
}
