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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


import com.example.myapplication.R;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private FirebaseAuth auth;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_login, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        auth = FirebaseAuth.getInstance();

        TextInputEditText edtEmail = v.findViewById(R.id.edtEmail);
        TextInputEditText edtPass  = v.findViewById(R.id.edtPassword);

        // nút back trên cùng
        View btnBack = v.findViewById(R.id.btnBackToProfile);
        if (btnBack != null) {
            btnBack.setOnClickListener(x -> {
                // quay về (đóng fragment hiện tại)
                NavHostFragment.findNavController(this).popBackStack();
            });
        }

        v.findViewById(R.id.btnLogin).setOnClickListener(x -> {
            String email = text(edtEmail), pass = text(edtPass);
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(requireContext(), "Nhập email & mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        String name = (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                                ? user.getDisplayName()
                                : email.split("@")[0];

                        // Lưu info vào SharedPreferences như cũ (để ProfileFragment dùng)
                        AuthManager.login(requireContext(), name, email);

                        // ✅ Luôn đi thẳng tới Home sau khi đăng nhập
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.homeFragment);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(),
                                "Đăng nhập thất bại: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });


        });

        v.findViewById(R.id.tvGoRegister).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_register)
        );
    }

    private String text(TextInputEditText e){ return e == null ? "" : String.valueOf(e.getText()).trim(); }
}
