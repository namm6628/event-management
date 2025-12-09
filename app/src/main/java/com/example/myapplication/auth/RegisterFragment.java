package com.example.myapplication.auth;

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

import java.util.HashMap;
import java.util.Map;

public class RegisterFragment extends Fragment {

    private FirebaseAuth auth;

    // View
    private TextInputEditText edtName, edtEmail, edtPhone, edtPassword, edtRePassword;
    private View btnRegister;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_register, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        auth = FirebaseAuth.getInstance();

        edtName       = v.findViewById(R.id.edtName);
        edtEmail      = v.findViewById(R.id.edtEmail);
        edtPhone      = v.findViewById(R.id.edtPhone);
        edtPassword   = v.findViewById(R.id.edtPassword);
        edtRePassword = v.findViewById(R.id.edtRePassword);
        btnRegister   = v.findViewById(R.id.btnRegister);

        View btnBack = v.findViewById(R.id.btnBackToProfile2);
        if (btnBack != null) {
            btnBack.setOnClickListener(x -> {
                boolean popped = NavHostFragment
                        .findNavController(this)
                        .popBackStack(R.id.profileFragment, false);
                if (!popped) {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.profileFragment);
                }
            });
        }

        btnRegister.setOnClickListener(view -> doRegister());

        v.findViewById(R.id.tvGoLogin).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }

    private void doRegister() {
        String name    = getText(edtName);
        String email   = getText(edtEmail);
        String phone   = getText(edtPhone);
        String pass    = getText(edtPassword);
        String repass  = getText(edtRePassword);

        if (TextUtils.isEmpty(name)) {
            edtName.setError("Nhập tên");
            edtName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Nhập email");
            edtEmail.requestFocus();
            return;
        }

        if (!email.toLowerCase().endsWith("@gmail.com")) {
            edtEmail.setError("Email phải có đuôi @gmail.com");
            edtEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            edtPhone.setError("Nhập số điện thoại");
            edtPhone.requestFocus();
            return;
        }
        if (phone.length() < 9) {
            edtPhone.setError("Số điện thoại không hợp lệ");
            edtPhone.requestFocus();
            return;
        }

        if (pass.length() < 6) {
            edtPassword.setError("Mật khẩu phải ≥ 6 ký tự");
            edtPassword.requestFocus();
            return;
        }
        if (!pass.equals(repass)) {
            edtRePassword.setError("Mật khẩu nhập lại không khớp");
            edtRePassword.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        btnRegister.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Lỗi: user null",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = user.getUid();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("name", name);
                    userDoc.put("email", email);
                    userDoc.put("phone", phone);
                    userDoc.put("createdAt", FieldValue.serverTimestamp());
                    userDoc.put("isOrganizer", false);
                    userDoc.put("isAdmin", false);

                    userDoc.put("membershipTier", "none");
                    userDoc.put("loyaltyPoints", 0L);
                    userDoc.put("lifetimePoints", 0L);

                    db.collection("users")
                            .document(uid)
                            .set(userDoc)
                            .addOnSuccessListener(unused -> {
                                btnRegister.setEnabled(true);

                                FirebaseAuth.getInstance().signOut();
                                AuthManager.logout(requireContext());

                                Toast.makeText(requireContext(),
                                        "Đăng ký thành công! Vui lòng đăng nhập.",
                                        Toast.LENGTH_SHORT).show();

                                NavHostFragment.findNavController(this)
                                        .navigate(R.id.loginFragment);
                            })
                            .addOnFailureListener(e -> {
                                btnRegister.setEnabled(true);
                                Toast.makeText(requireContext(),
                                        "Lỗi lưu user: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Đăng ký thất bại: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private String getText(TextInputEditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
