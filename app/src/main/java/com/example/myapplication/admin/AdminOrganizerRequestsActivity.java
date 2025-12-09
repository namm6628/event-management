package com.example.myapplication.admin;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.admin.model.OrganizerRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class AdminOrganizerRequestsActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private RecyclerView recyclerView;
    private RequestsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_organizer_request);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Duyệt Organizer");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerRequests);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestsAdapter();
        recyclerView.setAdapter(adapter);

        loadRequests();
    }

    private void loadRequests() {
        db.collection("organizerRequests")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Item> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        OrganizerRequest r = doc.toObject(OrganizerRequest.class);
                        if (r == null) continue;
                        String uid = doc.getId();
                        list.add(new Item(uid, r));
                    }
                    adapter.submit(list);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Lỗi tải dữ liệu: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }


    private static class Item {
        String docId;
        OrganizerRequest req;

        Item(String docId, OrganizerRequest req) {
            this.docId = docId;
            this.req = req;
        }
    }

    private class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {

        private final List<Item> data = new ArrayList<>();
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

        void submit(List<Item> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(
                    R.layout.item_admin_organizer_request, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {

            TextView tvName, tvOrgType, tvReason, tvCreatedAt, tvStatus;
            Button btnApprove, btnReject;

            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvOrgType = itemView.findViewById(R.id.tvOrgType);
                tvReason = itemView.findViewById(R.id.tvReason);
                tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                btnApprove = itemView.findViewById(R.id.btnApprove);
                btnReject = itemView.findViewById(R.id.btnReject);
            }

            void bind(Item item) {
                OrganizerRequest r = item.req;

                tvName.setText(r.getOrgName() != null ? r.getOrgName() : "Không tên");
                tvOrgType.setText("Loại: " + (r.getOrgType() != null ? r.getOrgType() : "-"));
                tvReason.setText("Lý do: " + (r.getDescription() != null ? r.getDescription() : "-"));

                if (r.getCreatedAt() != null) {
                    tvCreatedAt.setText("Gửi lúc: " + sdf.format(r.getCreatedAt().toDate()));
                } else {
                    tvCreatedAt.setText("Gửi lúc: -");
                }

                String status = r.getStatus() != null ? r.getStatus() : "pending";
                tvStatus.setText("Trạng thái: " + status);

                btnApprove.setOnClickListener(v -> approve(item));
                btnReject.setOnClickListener(v -> reject(item));
            }

            private void approve(Item item) {
                String adminUid = FirebaseAuth.getInstance().getUid();
                if (adminUid == null) {
                    Toast.makeText(itemView.getContext(),
                            "Bạn chưa đăng nhập",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = item.docId;

                WriteBatch batch = db.batch();

                // update organizerRequests
                batch.update(
                        db.collection("organizerRequests").document(uid),
                        new HashMap<String, Object>() {{
                            put("status", "approved");
                            put("processedAt", FieldValue.serverTimestamp());
                            put("processedBy", adminUid);
                        }}
                );

                // update users/{uid}.isOrganizer = true
                batch.update(
                        db.collection("users").document(uid),
                        new HashMap<String, Object>() {{
                            put("isOrganizer", true);
                        }}
                );

                // set organizers/{uid}
                batch.set(
                        db.collection("organizers").document(uid),
                        new HashMap<String, Object>() {{
                            put("uid", uid);
                            put("createdAt", FieldValue.serverTimestamp());
                            put("approvedBy", adminUid);
                        }},
                        com.google.firebase.firestore.SetOptions.merge()
                );

                batch.commit()
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(itemView.getContext(),
                                    "Đã duyệt organizer",
                                    Toast.LENGTH_SHORT).show();
                            removeItem(getBindingAdapterPosition());
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(itemView.getContext(),
                                        "Lỗi duyệt: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show()
                        );
            }

            private void reject(Item item) {
                String adminUid = FirebaseAuth.getInstance().getUid();
                if (adminUid == null) {
                    Toast.makeText(itemView.getContext(),
                            "Bạn chưa đăng nhập",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = item.docId;

                db.collection("organizerRequests")
                        .document(uid)
                        .update(new HashMap<String, Object>() {{
                            put("status", "rejected");
                            put("processedAt", FieldValue.serverTimestamp());
                            put("processedBy", adminUid);
                        }})
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(itemView.getContext(),
                                    "Đã từ chối",
                                    Toast.LENGTH_SHORT).show();
                            removeItem(getBindingAdapterPosition());
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(itemView.getContext(),
                                        "Lỗi từ chối: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show()
                        );
            }

            private void removeItem(int pos) {
                if (pos == RecyclerView.NO_POSITION) return;
                data.remove(pos);
                notifyItemRemoved(pos);
            }
        }
    }
}
