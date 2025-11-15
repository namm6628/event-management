package com.example.myapplication.attendee.event;

// ImportExportFragment.java

import android.net.Uri;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Collections;

public class ImportExportFragment extends Fragment {

    private FirebaseStorage storage;
    private Button btnImport, btnExport;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_import_export, container, false);

        storage = FirebaseStorage.getInstance();
        btnImport = rootView.findViewById(R.id.btnImport);
        btnExport = rootView.findViewById(R.id.btnExport);

        btnImport.setOnClickListener(v -> {
            // Xử lý nhập CSV
            Uri fileUri = null; // Đọc file từ file picker
                    StorageReference storageRef = storage.getReference().child("imports/" + fileUri.getLastPathSegment());

            storageRef.putFile(fileUri).addOnSuccessListener(taskSnapshot -> {
                // Cloud Function sẽ tự động xử lý khi tệp được tải lên
                FirebaseFunctions.getInstance()
                        .getHttpsCallable("importAttendees")
                        .call(Collections.singletonMap("filePath", storageRef.getPath()));
            });
        });

        btnExport.setOnClickListener(v -> {
            // Xuất CSV
            FirebaseFunctions.getInstance()
                    .getHttpsCallable("exportAttendees")
                    .call(Collections.singletonMap("eventId", "event123"))
                    .addOnSuccessListener(result -> {
                        // Xử lý khi xuất thành công
                    });
        });

        return rootView;
    }
}

