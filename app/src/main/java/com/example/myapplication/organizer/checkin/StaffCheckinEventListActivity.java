package com.example.myapplication.organizer.checkin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StaffCheckinEventListActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private RecyclerView rvEvents;
    private TextView tvEmpty;
    private StaffEventAdapter adapter;
    private final List<Event> eventList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_checkin_events);

        db = FirebaseFirestore.getInstance();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Sự kiện check-in");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvEvents = findViewById(R.id.rvEvents);
        tvEmpty  = findViewById(R.id.tvEmpty);

        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StaffEventAdapter(eventList, event -> {
            Intent i = new Intent(this, OrganizerCheckinListActivity.class);
            i.putExtra(OrganizerCheckinListActivity.EXTRA_EVENT_ID, event.getId());
            i.putExtra(OrganizerCheckinListActivity.EXTRA_EVENT_TITLE, event.getTitle());
            startActivity(i);
        });
        rvEvents.setAdapter(adapter);

        loadStaffEvents();
    }

    private void loadStaffEvents() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Bạn cần đăng nhập để xem sự kiện check-in.");
            return;
        }

        String email = user.getEmail();   // email staff

        db.collectionGroup("collaborators")
                .whereEqualTo("email", email)
                .whereEqualTo("role", "checkin")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Bạn chưa được phân công check-in sự kiện nào.");
                        eventList.clear();
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    tvEmpty.setVisibility(View.GONE);
                    eventList.clear();

                    for (DocumentSnapshot collabDoc : snap.getDocuments()) {

                        DocumentReference eventRef = collabDoc.getReference()
                                .getParent()
                                .getParent();

                        if (eventRef == null) continue;

                        eventRef.get().addOnSuccessListener(eventDoc -> {
                            if (!eventDoc.exists()) return;

                            Event e = eventDoc.toObject(Event.class);
                            if (e != null) {
                                e.setId(eventDoc.getId());
                                eventList.add(e);
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Lỗi tải dữ liệu: " + e.getMessage());
                });
    }

    /* ================== ADAPTER ================== */

    interface OnEventClick {
        void onClick(Event event);
    }

    static class StaffEventAdapter extends RecyclerView.Adapter<StaffEventAdapter.VH> {

        private final List<Event> data;
        private final OnEventClick listener;
        private final DateFormat df =
                new SimpleDateFormat("dd/MM/yyyy • HH:mm", new Locale("vi", "VN"));

        StaffEventAdapter(List<Event> data, OnEventClick listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_staff_checkin_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position), listener, df);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime, tvLocation;
            MaterialButton btnOpenCheckin;
            FloatingActionButton fabScanQr;
            MaterialCardView card;

            VH(@NonNull android.view.View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                tvTitle = itemView.findViewById(R.id.tvEventTitle);
                tvTime = itemView.findViewById(R.id.tvEventTime);
                tvLocation = itemView.findViewById(R.id.tvEventLocation);
                btnOpenCheckin = itemView.findViewById(R.id.btnOpenCheckin);
                fabScanQr = itemView.findViewById(R.id.fabScanQr);
            }

            void bind(Event e, OnEventClick listener, DateFormat df) {
                tvTitle.setText(e.getTitle() != null ? e.getTitle() : "(Không tên)");

                if (e.getStartTime() != null)
                    tvTime.setText(df.format(e.getStartTime().toDate()));
                else
                    tvTime.setText("Chưa có thời gian");

                tvLocation.setText(e.getLocation() != null ?
                        e.getLocation() : "Chưa có địa điểm");

                // mở màn list check-in
                View.OnClickListener openList = v -> {
                    if (listener != null) listener.onClick(e);
                };
                card.setOnClickListener(openList);
                btnOpenCheckin.setOnClickListener(openList);

                // nút quét QR trong card
                if (fabScanQr != null) {
                    fabScanQr.setOnClickListener(v -> {
                        Intent i = new Intent(v.getContext(), ScanQrActivity.class);
                        i.putExtra("EVENT_ID", e.getId());
                        v.getContext().startActivity(i);
                    });
                }
            }
        }
    }
}
