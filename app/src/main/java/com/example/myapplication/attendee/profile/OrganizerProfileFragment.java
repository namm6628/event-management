package com.example.myapplication.attendee.profile;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class OrganizerProfileFragment extends Fragment {

    private ImageView ivOrganizerLogo;
    private TextView tvOrganizerTitle;
    private EditText etOrganizerName, etDisplayName, etPhone, etAddress, etWebsite;
    private MaterialButton btnChangeLogo, btnSaveProfile;

    private FirebaseFirestore db;
    private ActivityResultLauncher<String> pickLogoLauncher;

    private String logoUrl = null;
    private Uri localLogoUri = null;

    // Loading dialog
    private androidx.appcompat.app.AlertDialog loadingDialog;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();

        pickLogoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && isAdded()) {
                        localLogoUri = uri;
                        uploadLogoToStorage(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_organizer_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        ivOrganizerLogo   = rootView.findViewById(R.id.ivOrganizerLogo);
        tvOrganizerTitle  = rootView.findViewById(R.id.tvOrganizerTitle);
        etOrganizerName   = rootView.findViewById(R.id.etOrganizerName);
        etDisplayName     = rootView.findViewById(R.id.etDisplayName);
        etPhone           = rootView.findViewById(R.id.etPhone);
        etAddress         = rootView.findViewById(R.id.etAddress);
        etWebsite         = rootView.findViewById(R.id.etWebsite);
        btnChangeLogo     = rootView.findViewById(R.id.btnChangeLogo);
        btnSaveProfile    = rootView.findViewById(R.id.btnSaveProfile);

        btnChangeLogo.setOnClickListener(v -> pickLogoLauncher.launch("image/*"));
        ivOrganizerLogo.setOnClickListener(v -> pickLogoLauncher.launch("image/*"));

        btnSaveProfile.setOnClickListener(v -> saveProfile());

        loadProfile();
    }

    // ========== LOADING DIALOG ==========

    private void showLoading(@Nullable String message, boolean show) {
        if (!show) {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
            btnSaveProfile.setEnabled(true);
            btnChangeLogo.setEnabled(true);
            return;
        }

        if (!isAdded()) return;

        if (loadingDialog == null) {
            View v = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_loading, null, false);

            loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(v)
                    .setCancelable(false)
                    .create();
        }

        TextView tvMsg = loadingDialog.findViewById(R.id.tvMessage);
        if (tvMsg != null && message != null) {
            tvMsg.setText(message);
        }

        btnSaveProfile.setEnabled(false);
        btnChangeLogo.setEnabled(false);
        loadingDialog.show();
    }

    // ========== LOAD PROFILE ==========

    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Đang tải hồ sơ...", true);

        db.collection("organizers")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    showLoading(null, false);
                    applyProfile(doc);
                })
                .addOnFailureListener(e -> {
                    showLoading(null, false);
                    Toast.makeText(requireContext(),
                            "Lỗi tải hồ sơ: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void applyProfile(DocumentSnapshot doc) {
        if (!doc.exists()) return;

        String organizerName = doc.getString("organizerName");
        String displayName   = doc.getString("displayName");
        String phone         = doc.getString("phone");
        String address       = doc.getString("address");
        String website       = doc.getString("website");
        logoUrl              = doc.getString("logoUrl");

        if (!TextUtils.isEmpty(organizerName)) {
            etOrganizerName.setText(organizerName);
            tvOrganizerTitle.setText(organizerName);
        }

        if (!TextUtils.isEmpty(displayName)) {
            etDisplayName.setText(displayName);
        }

        if (!TextUtils.isEmpty(phone)) {
            etPhone.setText(phone);
        }

        if (!TextUtils.isEmpty(address)) {
            etAddress.setText(address);
        }

        if (!TextUtils.isEmpty(website)) {
            etWebsite.setText(website);
        }

        if (!TextUtils.isEmpty(logoUrl) && isAdded()) {
            Glide.with(this)
                    .load(logoUrl)
                    .placeholder(R.drawable.baseline_account_circle_24)
                    .error(R.drawable.baseline_account_circle_24)
                    .into(ivOrganizerLogo);
        }
    }

    // ========== UPLOAD LOGO ==========

    private void uploadLogoToStorage(@NonNull Uri uri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Bạn cần đăng nhập để upload logo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        String fileName = "logo_" + uid + ".jpg";

        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("organizer_logos/" + uid + "/" + fileName);

        showLoading("Đang upload logo...", true);

        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            logoUrl = downloadUri.toString();

                            if (isAdded()) {
                                Glide.with(this)
                                        .load(logoUrl)
                                        .placeholder(R.drawable.baseline_account_circle_24)
                                        .error(R.drawable.baseline_account_circle_24)
                                        .into(ivOrganizerLogo);

                                Toast.makeText(
                                        requireContext(),
                                        "Upload logo thành công",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                            showLoading(null, false);
                        })
                )
                .addOnFailureListener(e -> {
                    showLoading(null, false);
                    if (!isAdded()) return;
                    Toast.makeText(
                            requireContext(),
                            "Upload logo lỗi: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    // ========== LƯU HỒ SƠ ==========

    private void saveProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String organizerName = text(etOrganizerName);
        String displayName   = text(etDisplayName);
        String phone         = text(etPhone);
        String address       = text(etAddress);
        String website       = text(etWebsite);

        // Validate cơ bản
        if (TextUtils.isEmpty(organizerName)) {
            etOrganizerName.setError("Nhập tên tổ chức");
            return;
        }

        if (!TextUtils.isEmpty(phone) && phone.length() < 8) {
            etPhone.setError("Số điện thoại không hợp lệ");
            return;
        }

        if (!TextUtils.isEmpty(website) && !Patterns.WEB_URL.matcher(website).matches()) {
            etWebsite.setError("Địa chỉ website không hợp lệ");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("organizerName", organizerName);
        data.put("displayName", displayName);
        data.put("phone", phone);
        data.put("address", address);
        data.put("website", website);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        if (!TextUtils.isEmpty(logoUrl)) {
            data.put("logoUrl", logoUrl);
        }

        tvOrganizerTitle.setText(organizerName);

        showLoading("Đang lưu hồ sơ...", true);

        db.collection("organizers")
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    showLoading(null, false);
                    Toast.makeText(requireContext(),
                            "Đã lưu hồ sơ tổ chức",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(null, false);
                    Toast.makeText(requireContext(),
                            "Lưu hồ sơ lỗi: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String text(EditText et) {
        return et == null || et.getText() == null
                ? ""
                : et.getText().toString().trim();
    }
}
