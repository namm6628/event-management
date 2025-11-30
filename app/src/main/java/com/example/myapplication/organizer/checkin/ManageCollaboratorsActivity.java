package com.example.myapplication.organizer.checkin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;              // 🔥 thêm import này
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageCollaboratorsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EVENT_ID";

    private FirebaseFirestore db;
    private String eventId;

    private EditText etEmail;
    private Spinner spRole;
    private Button btnAdd;
    private TextView tvEmpty;
    private RecyclerView rvList;

    private CollaboratorAdapter adapter;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_collaborators);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        // Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Nhân viên / CTV");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etEmail = findViewById(R.id.etEmail);
        spRole  = findViewById(R.id.spRole);
        btnAdd  = findViewById(R.id.btnAdd);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvList  = findViewById(R.id.rvCollaborators);

        // Spinner role (tạm thời chỉ cần "checkin")
        String[] roles = new String[] { "checkin" };
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                roles
        );
        spRole.setAdapter(roleAdapter);

        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CollaboratorAdapter(eventId);
        rvList.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addCollaborator());

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu EVENT_ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        listenCollaborators();
    }

    private void addCollaborator() {
        String email = etEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String role  = (String) spRole.getSelectedItem();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Nhập email nhân viên", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!email.contains("@")) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // ❌ KHÔNG còn normalize: docId = email nguyên bản
        String docId = email;

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("role", role);
        data.put("createdAt", Timestamp.now());

        db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .document(docId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Đã thêm nhân viên", Toast.LENGTH_SHORT).show();
                    etEmail.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void listenCollaborators() {
        registration = db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        tvEmpty.setText("Lỗi tải danh sách: " + e.getMessage());
                        tvEmpty.setVisibility(View.VISIBLE);     // 🔥
                        return;
                    }

                    if (snap == null || snap.isEmpty()) {
                        tvEmpty.setText("Chưa có nhân viên / CTV");
                        tvEmpty.setVisibility(View.VISIBLE);     // 🔥
                        adapter.submit(null);
                        return;
                    }

                    List<DocumentSnapshot> docs = snap.getDocuments();
                    tvEmpty.setVisibility(View.GONE);
                    adapter.submit(docs);
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
