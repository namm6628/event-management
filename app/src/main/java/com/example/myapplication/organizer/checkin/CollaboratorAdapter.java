package com.example.myapplication.organizer.checkin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CollaboratorAdapter extends RecyclerView.Adapter<CollaboratorAdapter.VH> {

    private final List<DocumentSnapshot> data = new ArrayList<>();
    private final String eventId;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public CollaboratorAdapter(String eventId) {
        this.eventId = eventId;
    }

    public void submit(List<DocumentSnapshot> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collaborator, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(data.get(position), eventId, db);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvEmail, tvRole;
        ImageButton btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole  = itemView.findViewById(R.id.tvRole);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(DocumentSnapshot doc, String eventId, FirebaseFirestore db) {
            String email = doc.getString("email");
            String role  = doc.getString("role");

            tvEmail.setText(email != null ? email : "(không có email)");
            tvRole.setText(role != null ? "Quyền: " + role : "Quyền: (không rõ)");

            btnDelete.setOnClickListener(v -> {
                if (eventId == null || email == null) return;

                String docId = email;

                db.collection("events")
                        .document(eventId)
                        .collection("collaborators")
                        .document(docId)
                        .delete()
                        .addOnSuccessListener(unused ->
                                Toast.makeText(v.getContext(), "Đã xóa " + email, Toast.LENGTH_SHORT).show()
                        )
                        .addOnFailureListener(e ->
                                Toast.makeText(v.getContext(), "Lỗi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
            });
        }
    }
}
