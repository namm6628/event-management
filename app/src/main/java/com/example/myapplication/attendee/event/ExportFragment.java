package com.example.myapplication.attendee.event;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.Collections;
import java.util.Map;

public class ExportFragment extends Fragment {

    private static final String TAG = "ExportFragment";

    private Button btnExportPdf, btnExportExcel;
    private String eventId;

    private FirebaseFunctions functions;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_export, container, false);

        btnExportPdf = rootView.findViewById(R.id.btnExportPdf);
        btnExportExcel = rootView.findViewById(R.id.btnExportExcel);

        if (getArguments() != null) {
            eventId = getArguments().getString("eventId");
        }
        if (eventId == null) {
            eventId = "demoEventId";
        }

        functions = FirebaseFunctions.getInstance("asia-southeast1");

        btnExportPdf.setOnClickListener(v -> exportPdf());
        btnExportExcel.setOnClickListener(v -> exportExcel());

        return rootView;
    }

    private void exportExcel() {
        Map<String, Object> data = Collections.singletonMap("eventId", eventId);

        functions
                .getHttpsCallable("exportAttendeesExcel")
                .call(data)
                .addOnSuccessListener((HttpsCallableResult result) -> {
                    Object raw = result.getData();
                    if (!(raw instanceof Map)) {
                        Toast.makeText(getContext(),
                                "Không đọc được dữ liệu export.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) raw;

                    String url = map.get("downloadUrl") instanceof String
                            ? (String) map.get("downloadUrl")
                            : null;

                    if (url == null) {
                        String msg = map.get("message") instanceof String
                                ? (String) map.get("message")
                                : "Không có attendee hoặc export lỗi.";
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    String code = "";
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException fe = (FirebaseFunctionsException) e;
                        code = fe.getCode().name();
                    }
                    Log.e(TAG, "exportExcel error", e);
                    Toast.makeText(
                            getContext(),
                            "Export Excel thất bại: " + code + " - " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void exportPdf() {
        Map<String, Object> data = Collections.singletonMap("eventId", eventId);

        functions
                .getHttpsCallable("exportAttendeesPdf")
                .call(data)
                .addOnSuccessListener((HttpsCallableResult result) -> {
                    Object raw = result.getData();
                    if (!(raw instanceof Map)) {
                        Toast.makeText(getContext(),
                                "Không đọc được dữ liệu PDF.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) raw;

                    boolean ok = map.get("ok") instanceof Boolean
                            ? (Boolean) map.get("ok")
                            : false;
                    String message = map.get("message") instanceof String
                            ? (String) map.get("message")
                            : (ok ? "Export PDF thành công" : "Export PDF");

                    Toast.makeText(getContext(),
                            message,
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    String code = "";
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException fe = (FirebaseFunctionsException) e;
                        code = fe.getCode().name();
                    }
                    Log.e(TAG, "exportPdf error", e);
                    Toast.makeText(
                            getContext(),
                            "Export PDF thất bại: " + code + " - " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}
