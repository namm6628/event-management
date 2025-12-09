package com.example.myapplication.attendee.event;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Locale;

public class ImportExportFragment extends Fragment {

    private FirebaseStorage storage;
    private Button btnImport;
    private Button btnExport;

    private String eventId;

    private ActivityResultLauncher<String> pickCsvLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_import_export, container, false);

        storage = FirebaseStorage.getInstance();
        btnImport = rootView.findViewById(R.id.btnImport);
        btnExport = rootView.findViewById(R.id.btnExport);

        if (getArguments() != null) {
            eventId = getArguments().getString("eventId");
        }
        if (eventId == null) {
            eventId = "demoEventId";
        }

        setupPickCsvLauncher();

        btnImport.setOnClickListener(v -> pickCsvLauncher.launch("text/*"));

        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                Toast.makeText(getContext(),
                        "Export Excel/PDF đang dùng ở màn Export riêng.",
                        Toast.LENGTH_SHORT).show();
            });
        }

        return rootView;
    }
    private void setupPickCsvLauncher() {
        pickCsvLauncher =
                registerForActivityResult(new ActivityResultContracts.GetContent(),
                        uri -> {
                            if (uri == null) {
                                Toast.makeText(getContext(),
                                        "Chưa chọn file CSV",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            uploadCsvToStorage(uri);
                        });
    }

    private void uploadCsvToStorage(@NonNull Uri fileUri) {
        if (eventId == null) {
            Toast.makeText(getContext(),
                    "Thiếu eventId để import.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = String.format(
                Locale.getDefault(),
                "attendees-%d.csv",
                System.currentTimeMillis()
        );

        StorageReference storageRef = storage.getReference()
                .child("imports")
                .child(eventId)
                .child(fileName);

        Toast.makeText(getContext(),
                "Đang upload CSV...",
                Toast.LENGTH_SHORT).show();

        storageRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Toast.makeText(getContext(),
                            "Upload CSV thành công. Server đang xử lý import.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            "Upload CSV thất bại: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
