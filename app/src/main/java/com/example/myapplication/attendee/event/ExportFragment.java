package com.example.myapplication.attendee.event;

// ExportFragment.java

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;

public class ExportFragment extends Fragment {

    private Button btnExportPdf, btnExportExcel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_export, container, false);

        btnExportPdf = rootView.findViewById(R.id.btnExportPdf);
        btnExportExcel = rootView.findViewById(R.id.btnExportExcel);

        btnExportPdf.setOnClickListener(v -> {
            // Xuất PDF
            FirebaseFunctions.getInstance()
                    .getHttpsCallable("exportAttendeesPdf")
                    .call(Collections.singletonMap("eventId", "event123"))
                    .addOnSuccessListener(result -> {
                        // Tải về hoặc xử lý PDF
                    });
        });

        btnExportExcel.setOnClickListener(v -> {
            // Xuất Excel
            FirebaseFunctions.getInstance()
                    .getHttpsCallable("exportAttendeesExcel")
                    .call(Collections.singletonMap("eventId", "event123"))
                    .addOnSuccessListener(result -> {
                        // Tải về hoặc xử lý Excel
                    });
        });

        return rootView;
    }
}

