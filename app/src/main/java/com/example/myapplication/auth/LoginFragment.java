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
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginFragment extends Fragment {

    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_login, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        auth = FirebaseAuth.getInstance();

        TextInputEditText edtEmail = v.findViewById(R.id.edtEmail);
        TextInputEditText edtPass  = v.findViewById(R.id.edtPassword);

        View btnBack = v.findViewById(R.id.btnBackToProfile);
        if (btnBack != null) {
            btnBack.setOnClickListener(x ->
                    NavHostFragment.findNavController(this).popBackStack()
            );
        }

        v.findViewById(R.id.btnLogin).setOnClickListener(x -> {
            String email = text(edtEmail), pass = text(edtPass);
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(requireContext(),
                        "Nhập email & mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user == null) {
                            Toast.makeText(requireContext(),
                                    "Lỗi: user null", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String uid = user.getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        db.collection("users").document(uid)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    String nameFromDoc = null;
                                    String phoneFromDoc = null;
                                    if (doc.exists()) {
                                        nameFromDoc = doc.getString("displayName");
                                        phoneFromDoc = doc.getString("phone");
                                    }

                                    String finalName;
                                    if (!TextUtils.isEmpty(nameFromDoc)) {
                                        finalName = nameFromDoc;
                                    } else if (user.getDisplayName() != null
                                            && !user.getDisplayName().isEmpty()) {
                                        finalName = user.getDisplayName();
                                    } else {
                                        finalName = email.split("@")[0];
                                    }

                                    AuthManager.login(requireContext(),
                                            finalName,
                                            email,
                                            phoneFromDoc);

                                    NavHostFragment.findNavController(this)
                                            .navigate(R.id.homeFragment);
                                })
                                .addOnFailureListener(e2 -> {
                                    String name;
                                    if (user.getDisplayName() != null
                                            && !user.getDisplayName().isEmpty()) {
                                        name = user.getDisplayName();
                                    } else {
                                        name = email.split("@")[0];
                                    }

                                    AuthManager.login(requireContext(), name, email);
                                    NavHostFragment.findNavController(this)
                                            .navigate(R.id.homeFragment);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(),
                                "Đăng nhập thất bại: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        });

        v.findViewById(R.id.tvGoRegister).setOnClickListener(x ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_register)
        );
    }

    private String text(TextInputEditText e) {
        return e == null ? "" : String.valueOf(e.getText()).trim();
    }
}
