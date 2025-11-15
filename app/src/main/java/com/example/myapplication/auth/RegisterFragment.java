package com.example.myapplication.auth;

import android.os.Bundle;
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

    // Khai báo view
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

        // Ánh xạ view
        edtName       = v.findViewById(R.id.edtName);
        edtEmail      = v.findViewById(R.id.edtEmail);
        edtPhone      = v.findViewById(R.id.edtPhone);       // cần có trong XML
        edtPassword   = v.findViewById(R.id.edtPassword);
        edtRePassword = v.findViewById(R.id.edtRePassword);  // cần có trong XML
        btnRegister   = v.findViewById(R.id.btnRegister);

        // Nút back về Profile (như cũ)
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

        // Đăng ký
        btnRegister.setOnClickListener(view -> doRegister());

        // Text "Đã có tài khoản? Đăng nhập"
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

        // --- Validate cơ bản ---
        if (name.isEmpty()) {
            edtName.setError("Nhập tên");
            return;
        }
        if (email.isEmpty()) {
            edtEmail.setError("Nhập email");
            return;
        }

        // --- Email phải @gmail.com ---
        if (!email.toLowerCase().endsWith("@gmail.com")) {
            edtEmail.setError("Email phải có đuôi @gmail.com");
            return;
        }

        // --- Password ---
        if (pass.length() < 6) {
            edtPassword.setError("Mật khẩu phải ≥ 6 ký tự");
            return;
        }
        if (!pass.equals(repass)) {
            edtRePassword.setError("Mật khẩu nhập lại không khớp");
            return;
        }

        // Phone: cho phép rỗng -> lưu null
        String phoneToStore = phone.isEmpty() ? null : phone;

        // Tạo account FirebaseAuth
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        Toast.makeText(requireContext(),
                                "Lỗi: user null",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = user.getUid();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> userDoc = new HashMap<>();
                    // PHẢI KHỚP RULE isValidUser()
                    userDoc.put("name", name);
                    userDoc.put("email", email);
                    userDoc.put("phone", phoneToStore);
                    userDoc.put("createdAt", FieldValue.serverTimestamp());
                    // Extra
                    userDoc.put("isOrganizer", false);

                    db.collection("users")
                            .document(uid)
                            .set(userDoc)
                            .addOnSuccessListener(unused -> {
                                // Đăng ký xong -> signOut, yêu cầu login lại
                                FirebaseAuth.getInstance().signOut();
                                AuthManager.logout(requireContext());

                                Toast.makeText(requireContext(),
                                        "Đăng ký thành công! Vui lòng đăng nhập.",
                                        Toast.LENGTH_SHORT).show();

                                NavHostFragment.findNavController(this)
                                        .navigate(R.id.loginFragment);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(requireContext(),
                                        "Lỗi lưu user: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
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
