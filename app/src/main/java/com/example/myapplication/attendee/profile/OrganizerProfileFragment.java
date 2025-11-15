package com.example.myapplication.attendee.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class OrganizerProfileFragment extends Fragment {

    private EditText etOrganizerName;
    private ImageView ivOrganizerLogo;
    private Button btnSaveProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_organizer_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        etOrganizerName = rootView.findViewById(R.id.etOrganizerName);
        ivOrganizerLogo = rootView.findViewById(R.id.ivOrganizerLogo);
        btnSaveProfile = rootView.findViewById(R.id.btnSaveProfile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String name = etOrganizerName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Nhập tên đơn vị tổ chức", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getUid();
        if (uid == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("organizers").document(uid)
                .set(new OrganizerProfile(name))
                .addOnSuccessListener(unused ->
                        Toast.makeText(requireContext(), "Đã lưu hồ sơ tổ chức", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // model nhỏ để lưu Firestore
    public static class OrganizerProfile {
        public String name;

        public OrganizerProfile() {}
        public OrganizerProfile(String name) {
            this.name = name;
        }
    }
}
