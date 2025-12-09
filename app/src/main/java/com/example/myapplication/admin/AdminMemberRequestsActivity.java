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
import com.example.myapplication.admin.model.MemberRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;

public class AdminMemberRequestsActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private RecyclerView recyclerView;
    private MemberAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_member_request);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Duyệt Member");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerRequests);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter();
        recyclerView.setAdapter(adapter);

        loadRequests();
    }

    private void loadRequests() {
        db.collection("memberRequests")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Item> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        MemberRequest r = doc.toObject(MemberRequest.class);
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
        MemberRequest req;

        Item(String docId, MemberRequest req) {
            this.docId = docId;
            this.req = req;
        }
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.VH> {

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
                    R.layout.item_admin_member_request, parent, false);
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
            TextView tvUserId, tvReason, tvCreatedAt, tvStatus;
            Button btnApproveMember, btnApproveVip, btnReject;

            VH(@NonNull View itemView) {
                super(itemView);
                tvUserId = itemView.findViewById(R.id.tvUserId);
                tvReason = itemView.findViewById(R.id.tvReason);
                tvCreatedAt = itemView.findViewById(R.id.tvCreatedAt);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                btnApproveMember = itemView.findViewById(R.id.btnApproveMember);
                btnApproveVip = itemView.findViewById(R.id.btnApproveVip);
                btnReject = itemView.findViewById(R.id.btnReject);
            }

            void bind(Item item) {
                MemberRequest r = item.req;

                tvUserId.setText("User: " + item.docId);
                tvReason.setText("Lý do: " + (r.getReason() != null ? r.getReason() : "-"));

                if (r.getCreatedAt() != null) {
                    tvCreatedAt.setText("Gửi lúc: " + sdf.format(r.getCreatedAt().toDate()));
                } else {
                    tvCreatedAt.setText("Gửi lúc: -");
                }

                String status = r.getStatus() != null ? r.getStatus() : "pending";
                tvStatus.setText("Trạng thái: " + status);

                btnApproveMember.setOnClickListener(v -> approve(item, "member"));
                btnApproveVip.setOnClickListener(v -> approve(item, "vip"));
                btnReject.setOnClickListener(v -> reject(item));
            }

            private void approve(Item item, String tier) {
                String adminUid = FirebaseAuth.getInstance().getUid();
                if (adminUid == null) {
                    Toast.makeText(itemView.getContext(),
                            "Bạn chưa đăng nhập",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = item.docId;

                HashMap<String, Object> updatesReq = new HashMap<>();
                updatesReq.put("status", "approved");
                updatesReq.put("processedAt", FieldValue.serverTimestamp());
                updatesReq.put("processedBy", adminUid);

                db.collection("memberRequests")
                        .document(uid)
                        .update(updatesReq)
                        .addOnSuccessListener(unused -> {
                            // update user membershipTier
                            HashMap<String, Object> updatesUser = new HashMap<>();
                            updatesUser.put("membershipTier", tier);

                            db.collection("users")
                                    .document(uid)
                                    .update(updatesUser)
                                    .addOnSuccessListener(u2 -> {
                                        Toast.makeText(itemView.getContext(),
                                                "Đã set " + tier + " cho " + uid,
                                                Toast.LENGTH_SHORT).show();
                                        removeItem(getBindingAdapterPosition());
                                    })
                                    .addOnFailureListener(e2 ->
                                            Toast.makeText(itemView.getContext(),
                                                    "Lỗi set tier: " + e2.getMessage(),
                                                    Toast.LENGTH_LONG).show()
                                    );
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

                HashMap<String, Object> updatesReq = new HashMap<>();
                updatesReq.put("status", "rejected");
                updatesReq.put("processedAt", FieldValue.serverTimestamp());
                updatesReq.put("processedBy", adminUid);

                db.collection("memberRequests")
                        .document(uid)
                        .update(updatesReq)
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
