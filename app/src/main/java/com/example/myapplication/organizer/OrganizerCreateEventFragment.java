package com.example.myapplication.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OrganizerCreateEventFragment extends Fragment {

    private EditText edtTitle, edtArtist, edtCategory, edtLocation, edtPrice, edtTotalSeats;
    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private View btnSave, btnPickDateTime, btnPickImage;

    private FirebaseFirestore db;

    // DateTime
    private Calendar selectedDateTime;

    // Image
    private ActivityResultLauncher<String> pickImageLauncher;
    private Uri selectedImageUri; // dùng hiển thị
    private String thumbnailStr;  // string lưu vào Firestore

    // Category hợp lệ theo rules
    private static final Set<String> VALID_CATEGORIES = new HashSet<>();

    static {
        VALID_CATEGORIES.add("Âm nhạc");
        VALID_CATEGORIES.add("Sân khấu & nghệ thuật");
        VALID_CATEGORIES.add("Thể thao");
        VALID_CATEGORIES.add("Khác");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        selectedDateTime = null;
        thumbnailStr = null;

        // Đăng ký picker ảnh
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && isAdded()) {
                        selectedImageUri = uri;
                        thumbnailStr = uri.toString(); // lưu string vào Firestore
                        if (ivPreview != null) {
                            ivPreview.setImageURI(uri);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle b) {
        // Dùng layout activity_create_event
        return inflater.inflate(R.layout.activity_create_event, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        edtTitle      = v.findViewById(R.id.edtTitle);
        edtArtist     = v.findViewById(R.id.edtArtist);
        edtCategory   = v.findViewById(R.id.edtCategory);
        edtLocation   = v.findViewById(R.id.edtLocation);
        edtPrice      = v.findViewById(R.id.edtPrice);
        edtTotalSeats = v.findViewById(R.id.edtTotalSeats);

        tvPickedDateTime = v.findViewById(R.id.tvPickedDateTime);
        ivPreview        = v.findViewById(R.id.ivPreview);

        btnSave         = v.findViewById(R.id.btnSave);
        btnPickDateTime = v.findViewById(R.id.btnPickDateTime);
        btnPickImage    = v.findViewById(R.id.btnPickImage);

        // Chọn ngày giờ
        if (btnPickDateTime != null) {
            btnPickDateTime.setOnClickListener(view -> showDateTimePicker());
        }

        // Chọn ảnh cover
        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(view -> pickImageLauncher.launch("image/*"));
        }

        // Lưu event
        if (btnSave != null) {
            btnSave.setOnClickListener(view -> saveEvent());
        }
    }

    private void showDateTimePicker() {
        final Calendar now = Calendar.getInstance();

        DatePickerDialog dateDialog = new DatePickerDialog(
                requireContext(),
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    // Sau khi chọn ngày -> chọn giờ
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog timeDialog = new TimePickerDialog(
                            requireContext(),
                            (TimePicker timeView, int hourOfDay, int minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                picked.set(Calendar.MILLISECOND, 0);

                                selectedDateTime = picked;

                                // Hiển thị text
                                String text = String.format(
                                        "%02d/%02d/%04d • %02d:%02d",
                                        dayOfMonth, month + 1, year, hourOfDay, minute
                                );
                                tvPickedDateTime.setText(text);
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true
                    );

                    timeDialog.show();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );

        dateDialog.show();
    }

    private void saveEvent() {
        String title    = getText(edtTitle);
        String artist   = getText(edtArtist);
        String category = getText(edtCategory);
        String location = getText(edtLocation);
        String priceStr = getText(edtPrice);
        String totalStr = getText(edtTotalSeats);

        // --- Validate cơ bản ---
        if (TextUtils.isEmpty(title)) {
            edtTitle.setError("Nhập tên sự kiện");
            return;
        }
        if (TextUtils.isEmpty(location)) {
            edtLocation.setError("Nhập địa điểm");
            return;
        }
        if (TextUtils.isEmpty(category)) {
            edtCategory.setError("Nhập thể loại");
            return;
        }

        // --- Category phải thuộc 4 loại hợp lệ ---
        if (!VALID_CATEGORIES.contains(category)) {
            edtCategory.setError("Thể loại phải là: Âm nhạc / Sân khấu & nghệ thuật / Thể thao / Khác");
            Toast.makeText(requireContext(),
                    "Thể loại phải là: Âm nhạc, Sân khấu & nghệ thuật, Thể thao hoặc Khác",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (TextUtils.isEmpty(totalStr)) {
            edtTotalSeats.setError("Nhập tổng số vé");
            return;
        }

        // --- Thời gian ---
        if (selectedDateTime == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn ngày & giờ sự kiện",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Ảnh cover ---
        if (thumbnailStr == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn ảnh cover cho sự kiện",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Parse số ---
        double price = 0;
        if (!TextUtils.isEmpty(priceStr)) {
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                edtPrice.setError("Giá không hợp lệ");
                return;
            }
        }

        int totalSeats;
        try {
            totalSeats = Integer.parseInt(totalStr);
        } catch (NumberFormatException e) {
            edtTotalSeats.setError("Tổng vé không hợp lệ");
            return;
        }

        if (totalSeats < 0) {
            edtTotalSeats.setError("Tổng vé phải ≥ 0");
            return;
        }

        // --- Lấy user hiện tại ---
        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Bạn cần đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo Timestamp từ selectedDateTime
        Timestamp startTime = new Timestamp(selectedDateTime.getTime());

        // --- Build data theo đúng isValidEvent() ---
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("location", location);
        data.put("startTime", startTime);
        data.put("thumbnail", thumbnailStr);
        data.put("category", category);
        data.put("price", price);
        data.put("availableSeats", totalSeats); // lúc mới tạo = total
        data.put("totalSeats", totalSeats);
        data.put("ownerUid", user.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());

        // Một số field tuỳ chọn (rules cho phép có/không)
        if (!TextUtils.isEmpty(artist)) {
            data.put("artist", artist);
        }

        // Ghi Firestore
        db.collection("events")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(requireContext(),
                            "Tạo sự kiện thành công",
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).navigateUp();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Tạo sự kiện thất bại: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String getText(EditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
