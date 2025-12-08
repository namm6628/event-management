package com.example.myapplication.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class AdminDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButton btnOrganizer = findViewById(R.id.btnOrganizerRequests);
        MaterialButton btnMember = findViewById(R.id.btnMemberRequests);

        btnOrganizer.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminOrganizerRequestsActivity.class));
        });

        btnMember.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminMemberRequestsActivity.class));
        });
    }
}
