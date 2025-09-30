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

public class RegisterFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_register, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        TextInputEditText edtName  = v.findViewById(R.id.edtName);
        TextInputEditText edtEmail = v.findViewById(R.id.edtEmail);
        TextInputEditText edtPass  = v.findViewById(R.id.edtPassword);

        View btnBack = v.findViewById(R.id.btnBackToProfile2);
        if (btnBack != null) {
            btnBack.setOnClickListener(x -> {
                boolean popped = NavHostFragment
                        .findNavController(this)
                        .popBackStack(R.id.profileFragment, false); // pop tới profileFragment nếu có
                if (!popped) {
                    // nếu profile không có trên back stack -> điều hướng trực tiếp tới profile
                    NavHostFragment.findNavController(this).navigate(R.id.profileFragment);
                }
            });
        }


        v.findViewById(R.id.btnRegister).setOnClickListener(x -> {
            String name = text(edtName), email = text(edtEmail), pass = text(edtPass);
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(requireContext(), "Nhập đủ Họ tên, Email, Mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            // DEMO: coi như đăng ký & đăng nhập luôn
            AuthManager.login(requireContext(), name, email);
            NavHostFragment.findNavController(this).popBackStack(); // quay về Profile
        });

        v.findViewById(R.id.tvGoLogin).setOnClickListener(x ->
                NavHostFragment.findNavController(this).navigateUp() // quay về Login
        );
    }

    private String text(TextInputEditText e){ return e == null ? "" : String.valueOf(e.getText()).trim(); }
}
