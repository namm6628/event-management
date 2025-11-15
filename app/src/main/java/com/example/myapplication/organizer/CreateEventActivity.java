package com.example.myapplication.organizer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.ActivityCreateEventBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CreateEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ActivityCreateEventBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String eventId;              // null -> tạo mới, khác null -> edit
    private boolean isEditMode = false;

    private Calendar selectedStartTime;  // ngày/giờ đã chọn
    private Uri imageUri;                // ảnh mới chọn
    private String existingThumbnailUrl; // ảnh cũ (nếu edit)
    private String existingOwnerId;      // ownerId cũ (nếu edit)

    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        isEditMode = (eventId != null);

        if (isEditMode) {
            setTitle("Chỉnh sửa sự kiện");
            loadEvent();
        } else {
            setTitle("Tạo sự kiện mới");
        }

        selectedStartTime = null;
        imageUri = null;
        existingThumbnailUrl = null;
        existingOwnerId = null;

        // Chọn ảnh cover
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            imageUri = uri;
                            binding.ivPreview.setImageURI(uri);
                        }
                    }
                }
        );

        binding.btnPickImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        // Chọn ngày giờ
        binding.btnPickDateTime.setOnClickListener(v -> pickDateTime());

        // Lưu sự kiện
        binding.btnSave.setOnClickListener(v -> saveEvent());
    }

    private void pickDateTime() {
        final Calendar cal = Calendar.getInstance();

        new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    new android.app.TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minute);
                                cal.set(Calendar.SECOND, 0);

                                selectedStartTime = cal;
                                SimpleDateFormat sdf =
                                        new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());
                                binding.tvPickedDateTime.setText(sdf.format(cal.getTime()));
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                    ).show();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    /**
     * Nếu ở chế độ edit, load event từ Firestore và fill vào UI.
     */
    private void loadEvent() {
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(this::bindEvent)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không tải được sự kiện: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void bindEvent(DocumentSnapshot snap) {
        Event e = snap.toObject(Event.class);
        if (e == null) {
            Toast.makeText(this, "Dữ liệu sự kiện không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        existingOwnerId = e.getOwnerId();
        existingThumbnailUrl = e.getThumbnail();

        binding.edtTitle.setText(e.getTitle());
        binding.edtArtist.setText(e.getArtist());   // có thể null, OK
        binding.edtCategory.setText(e.getCategory());
        binding.edtLocation.setText(e.getLocation());

        if (e.getPrice() != null) {
            binding.edtPrice.setText(String.valueOf(e.getPrice().intValue()));
        }
        if (e.getTotalSeats() != null) {
            binding.edtTotalSeats.setText(String.valueOf(e.getTotalSeats()));
        }

        // Start time
        if (e.getStartTime() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(e.getStartTime().toDate());
            selectedStartTime = cal;

            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());
            binding.tvPickedDateTime.setText(sdf.format(cal.getTime()));
        }

        // Thumbnail preview (nếu có)
        if (!TextUtils.isEmpty(e.getThumbnail())) {
            Glide.with(this)
                    .load(e.getThumbnail())
                    .into(binding.ivPreview);
        }
    }

    private void saveEvent() {
        String title = text(binding.edtTitle);
        String artist = text(binding.edtArtist);
        String category = text(binding.edtCategory);
        String location = text(binding.edtLocation);
        String priceStr = text(binding.edtPrice);
        String seatsStr = text(binding.edtTotalSeats);

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(location)) {
            Toast.makeText(this, "Nhập ít nhất Tên sự kiện và Địa điểm", Toast.LENGTH_SHORT).show();
            return;
        }

        Double price = null;
        if (!TextUtils.isEmpty(priceStr)) {
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Giá vé không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Integer totalSeats = null;
        if (!TextUtils.isEmpty(seatsStr)) {
            try {
                totalSeats = Integer.parseInt(seatsStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Tổng vé không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để tạo/chỉnh sửa sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        Event e = new Event();
        e.setTitle(title);
        // Nghệ sĩ có thể để trống -> cho phép null
        e.setArtist(TextUtils.isEmpty(artist) ? null : artist);
        e.setCategory(category);
        e.setLocation(location);
        e.setPrice(price);
        e.setTotalSeats(totalSeats);
        e.setAvailableSeats(totalSeats); // nếu muốn giữ availableSeats cũ khi edit phức tạp hơn
        e.setOwnerId(isEditMode && existingOwnerId != null ? existingOwnerId : uid);

        if (selectedStartTime != null) {
            e.setStartTime(new Timestamp(selectedStartTime.getTime()));
        } else {
            e.setStartTime(Timestamp.now());
        }

        // Tạo docRef
        final var docRef = (isEditMode
                ? db.collection("events").document(eventId)
                : db.collection("events").document());

        e.setId(docRef.getId());

        if (imageUri == null) {
            // Không chọn ảnh mới:
            // - Nếu edit: giữ thumbnail cũ
            // - Nếu create: để null
            e.setThumbnail(existingThumbnailUrl);

            docRef.set(e)
                    .addOnSuccessListener(r -> {
                        Toast.makeText(this,
                                isEditMode ? "Đã cập nhật sự kiện" : "Đã tạo sự kiện",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(err ->
                            Toast.makeText(this, "Lỗi lưu sự kiện: " + err.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            // Có ảnh mới -> upload Storage trước
            StorageReference ref = FirebaseStorage.getInstance()
                    .getReference("event-covers/" + docRef.getId() + ".jpg");

            ref.putFile(imageUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> {
                        String url = uri.toString();
                        e.setThumbnail(url);
                        docRef.set(e)
                                .addOnSuccessListener(r -> {
                                    Toast.makeText(this,
                                            isEditMode ? "Đã cập nhật sự kiện" : "Đã tạo sự kiện",
                                            Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(err ->
                                        Toast.makeText(this, "Lỗi lưu sự kiện: " + err.getMessage(), Toast.LENGTH_SHORT).show()
                                );
                    })
                    .addOnFailureListener(err ->
                            Toast.makeText(this, "Lỗi upload ảnh: " + err.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }

    private String text(com.google.android.material.textfield.TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString().trim();
    }
}
