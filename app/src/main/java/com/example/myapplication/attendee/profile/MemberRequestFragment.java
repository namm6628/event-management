package com.example.myapplication.attendee.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MemberRequestFragment extends Fragment {

    private EditText edtReason;
    private MaterialButton btnSubmit;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_member_request, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        edtReason = view.findViewById(R.id.edtReason);
        btnSubmit = view.findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> submitRequest());
    }

    private void submitRequest() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(getContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String reason = edtReason.getText().toString().trim();
        if (TextUtils.isEmpty(reason)) {
            edtReason.setError("Vui lòng nhập lý do");
            return;
        }

        btnSubmit.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("reason", reason);
        data.put("status", "pending");
        data.put("createdAt", Timestamp.now());
        data.put("processedAt", null);
        data.put("processedBy", null);

        db.collection("memberRequests")
                .document(uid)
                .set(data)
                .addOnSuccessListener(unused -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Đã gửi yêu cầu, vui lòng đợi admin duyệt",
                            Toast.LENGTH_LONG).show();
                    requireActivity().onBackPressed();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Lỗi: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
