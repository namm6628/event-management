package com.example.myapplication.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.attendee.detail.EventDetailActivity;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.ActivityOrganizerDashboardBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class OrganizerDashboardActivity extends AppCompatActivity {

    private ActivityOrganizerDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private OrganizerEventAdapter adapter;
    private ListenerRegistration eventListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrganizerDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardSettings.setOnClickListener(v -> {
            Intent it = new Intent(this, OrganizerProfileActivity.class);
            startActivity(it);
        });


        setupToolbar();
        setupRecyclerView();
        setupActions();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Organizer Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupRecyclerView() {
        adapter = new OrganizerEventAdapter(new OrganizerEventAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Event e) {
                Intent it = new Intent(OrganizerDashboardActivity.this, CreateEventActivity.class);
                it.putExtra("EXTRA_EVENT_ID", e.getId());
                startActivity(it);
            }

            @Override
            public void onViewAttendees(@NonNull Event e) {
                Intent it = new Intent(OrganizerDashboardActivity.this, OrganizerAttendeesActivity.class);
                it.putExtra("EXTRA_EVENT_ID", e.getId());
                startActivity(it);
            }
            @Override
            public void onBroadcast(@NonNull Event e) {
                Intent it = new Intent(OrganizerDashboardActivity.this, OrganizerBroadcastActivity.class);
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_ID, e.getId());
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_TITLE, e.getTitle());
                startActivity(it);
            }

        });


        binding.recyclerEvents.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        );
        binding.recyclerEvents.setAdapter(adapter);
    }

    private void setupActions() {
        // Tạo event mới
        binding.fabCreateEvent.setOnClickListener(v -> {
            // TODO: mở màn CreateEventActivity (sau này)
        });

        // Scan vé
        binding.cardScanTicket.setOnClickListener(v -> {
            // TODO: mở màn ScanTicketActivity
        });

        // Danh sách người tham dự
        binding.cardAttendees.setOnClickListener(v -> {
            // TODO: mở màn AttendeeListActivity
        });

        binding.cardSettings.setOnClickListener(v -> {
            startActivity(new Intent(
                    OrganizerDashboardActivity.this,
                    OrganizerProfileActivity.class
            ));
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        listenMyEvents();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventListener != null) {
            eventListener.remove();
            eventListener = null;
        }
    }

    private void listenMyEvents() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showError("Bạn chưa đăng nhập");
            showEmpty(true);
            return;
        }

        showLoading(true);
        showEmpty(false);

        eventListener = db.collection("events")
                .whereEqualTo("ownerId", user.getUid())
                .orderBy("startTime", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    showLoading(false);

                    if (error != null) {
                        error.printStackTrace();
                        showError("Lỗi tải danh sách sự kiện");
                        return;
                    }

                    if (value == null || value.isEmpty()) {
                        adapter.submit(new ArrayList<>());
                        showEmpty(true);
                        return;
                    }

                    List<Event> list = new ArrayList<>();
                    for (var doc : value.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e == null) continue;
                        e.setId(doc.getId());
                        list.add(e);
                    }

                    adapter.submit(list);
                    showEmpty(list.isEmpty());
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showEmpty(boolean isEmpty) {
        binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
