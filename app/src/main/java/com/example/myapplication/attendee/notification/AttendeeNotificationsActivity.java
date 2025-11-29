package com.example.myapplication.attendee.notification;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

public class AttendeeNotificationsActivity extends AppCompatActivity
        implements BroadcastAdapter.Listener {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private RecyclerView rv;
    private View layoutEmpty;
    private BroadcastAdapter adapter;

    // giữ list hiện tại để cập nhật lại khi ẩn
    private final List<EventBroadcast> currentList = new ArrayList<>();

    private static final String PREF_NAME = "attendee_notifications_prefs";
    private static final String KEY_HIDDEN_IDS = "hidden_broadcast_ids";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // reuse layout hiện tại
        setContentView(R.layout.activity_event_broadcast_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Thông báo");
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rv = findViewById(R.id.rvBroadcasts);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BroadcastAdapter();
        adapter.setListener(this);     // bật nút xoá cho người nhận
        rv.setAdapter(adapter);

        loadNotifications();
    }

    // ===== SharedPreferences: lưu ID thông báo đã ẩn =====

    private Set<String> getHiddenIds() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN_IDS, new HashSet<>()));
    }

    private void addHiddenId(@NonNull String id) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_HIDDEN_IDS, new HashSet<>()));
        set.add(id);
        prefs.edit().putStringSet(KEY_HIDDEN_IDS, set).apply();
    }

    // ===== Load thông báo =====

    private void loadNotifications() {
        if (auth.getCurrentUser() == null) {
            showEmpty();
            Toast.makeText(this, "Bạn cần đăng nhập để xem thông báo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        Set<String> hiddenIds = getHiddenIds();

        db.collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "paid")
                .get()
                .addOnSuccessListener(orderSnap -> {
                    if (orderSnap.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    // lấy list eventId của user đã mua vé
                    Set<String> eventIds = new HashSet<>();
                    for (DocumentSnapshot d : orderSnap) {
                        String eventId = d.getString("eventId");
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
                                        // nếu user đã ẩn thông báo này thì bỏ qua
                                        if (hiddenIds.contains(d.getId())) continue;

                                        EventBroadcast b = d.toObject(EventBroadcast.class);
                                        if (b != null) {
                                            b.setId(d.getId());
                                            b.setEventTitle(d.getString("eventTitle"));
                                            list.add(b);
                                        }
                                    }
                                }

                                currentList.clear();
                                currentList.addAll(list);

                                if (list.isEmpty()) {
                                    showEmpty();
                                } else {
                                    adapter.submitList(new ArrayList<>(list));
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

    // ===== Xử lý nút xoá trên từng card =====

    @Override
    public void onDismiss(@NonNull EventBroadcast b) {
        String id = b.getId();
        if (id == null || id.isEmpty()) {
            Toast.makeText(this, "Thiếu ID thông báo", Toast.LENGTH_SHORT).show();
            return;
        }

        // lưu lại là đã ẩn
        addHiddenId(id);

        // xoá khỏi currentList theo id
        for (int i = 0; i < currentList.size(); i++) {
            EventBroadcast item = currentList.get(i);
            if (id.equals(item.getId())) {
                currentList.remove(i);
                break;
            }
        }

        // cập nhật lại adapter bằng list mới (ListAdapter sẽ diff)
        adapter.submitList(new ArrayList<>(currentList));

        if (currentList.isEmpty()) {
            showEmpty();
        }

        Toast.makeText(this, "Đã ẩn thông báo", Toast.LENGTH_SHORT).show();
    }
}
