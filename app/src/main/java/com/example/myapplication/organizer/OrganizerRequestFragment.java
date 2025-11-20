package com.example.myapplication.organizer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.myapplication.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class OrganizerRequestFragment extends Fragment {

    private TextInputEditText edtOrgName, edtOrgType, edtOrgDesc;
    private View btnSubmit;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_organizer_request, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        edtOrgName = v.findViewById(R.id.edtOrgName);
        edtOrgType = v.findViewById(R.id.edtOrgType);
        edtOrgDesc = v.findViewById(R.id.edtOrgDesc);
        btnSubmit  = v.findViewById(R.id.btnSubmitOrganizerRequest);

        btnSubmit.setOnClickListener(view -> submitRequest());
    }

    private void submitRequest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Bạn cần đăng nhập trước",
                    Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        String orgName = text(edtOrgName);
        String orgType = text(edtOrgType);
        String orgDesc = text(edtOrgDesc);

        if (TextUtils.isEmpty(orgName)) {
            edtOrgName.setError("Nhập tên đơn vị / nhóm");
            return;
        }

        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("orgName", orgName);
        data.put("orgType", orgType);
        data.put("description", orgDesc);
        data.put("status", "pending"); // pending / approved / rejected
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("organizerRequests")
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(),
                            "Đã gửi yêu cầu. Admin sẽ xem xét và cấp quyền Organizer cho bạn.",
                            Toast.LENGTH_LONG).show();
                    NavHostFragment.findNavController(this).navigateUp();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Gửi yêu cầu thất bại: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String text(TextInputEditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}

