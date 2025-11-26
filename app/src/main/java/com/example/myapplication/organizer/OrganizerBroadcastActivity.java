package com.example.myapplication.organizer;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class OrganizerBroadcastActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String eventId;
    private String eventTitle;

    private TextInputEditText edtTitle;
    private TextInputEditText edtMessage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_broadcast);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);

        if (eventId == null) {
            Toast.makeText(this, "Thiếu eventId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        edtTitle = findViewById(R.id.edtTitle);
        edtMessage = findViewById(R.id.edtMessage);
        MaterialButton btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendBroadcast());
    }

    private void sendBroadcast() {

        String title = edtTitle.getText() != null ? edtTitle.getText().toString().trim() : "";
        String message = edtMessage.getText() != null ? edtMessage.getText().toString().trim() : "";

        if (title.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đủ tiêu đề và nội dung",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("eventTitle", eventTitle);
        data.put("title", title);
        data.put("message", message);
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("eventBroadcasts")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Đã gửi thông báo", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

}
