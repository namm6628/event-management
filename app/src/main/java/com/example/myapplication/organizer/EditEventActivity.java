package com.example.myapplication.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class EditEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    // Input chính
    private EditText edtTitle, edtArtist, edtCategory,
            edtPlace, edtAddressDetail,
            edtDescription, edtTotalSeats;
    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private MaterialButton btnPickDateTime, btnPickImage, btnSave;

    // Ticket container
    private android.view.ViewGroup layoutTicketContainer;
    private MaterialButton btnAddTicketType;

    private final List<TicketRow> ticketRows = new ArrayList<>();

    private FirebaseFirestore db;
    private String eventId;
    private Timestamp selectedStartTime;
    private Uri selectedImageUri;
    private String currentThumbnailUrl;
    private String ownerId; // từ doc event

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        db = FirebaseFirestore.getInstance();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (TextUtils.isEmpty(eventId)) {
            Toast.makeText(this, "Thiếu EVENT_ID để chỉnh sửa", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chỉnh sửa sự kiện");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Ánh xạ view
        edtTitle         = findViewById(R.id.edtTitle);
        edtArtist        = findViewById(R.id.edtArtist);
        edtCategory      = findViewById(R.id.edtCategory);
        edtDescription   = findViewById(R.id.edtDescription);
        edtPlace         = findViewById(R.id.edtPlace);
        edtAddressDetail = findViewById(R.id.edtAddressDetail);
        edtTotalSeats    = findViewById(R.id.edtTotalSeats);

        tvPickedDateTime = findViewById(R.id.tvPickedDateTime);
        ivPreview        = findViewById(R.id.ivPreview);

        btnPickDateTime  = findViewById(R.id.btnPickDateTime);
        btnPickImage     = findViewById(R.id.btnPickImage);
        btnSave          = findViewById(R.id.btnSave);

        layoutTicketContainer = findViewById(R.id.layoutTicketContainer);
        btnAddTicketType      = findViewById(R.id.btnAddTicketType);

        // Chọn thời gian
        btnPickDateTime.setOnClickListener(v -> showDateTimePicker());

        // Chọn ảnh mới (không bắt buộc)
        btnPickImage.setOnClickListener(v -> {
            androidx.activity.result.ActivityResultLauncher<String> launcher =
                    registerForActivityResult(
                            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                            uri -> {
                                if (uri != null) {
                                    selectedImageUri = uri;
                                    ivPreview.setImageURI(uri);
                                }
                            }
                    );
            launcher.launch("image/*");
        });

        // 🔒 KHÔNG CHO THÊM LOẠI VÉ MỚI KHI EDIT (nếu vẫn muốn cho thêm thì giữ lại)
        btnAddTicketType.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Không thể thêm / sửa sơ đồ ghế khi chỉnh sửa sự kiện",
                    Toast.LENGTH_SHORT).show();
        });

        // Lưu
        btnSave.setOnClickListener(v -> saveChanges());

        // ❌ BỎ clearSeatsForEvent, để nguyên ghế cũ
        // SeatLayoutConfigActivity.clearSeatsForEvent(eventId);

        // Load dữ liệu event + ticketTypes
        loadEventAndTickets();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Không cần sync TEMP_SEATS nữa vì không cho sửa ghế
    @Override
    protected void onResume() {
        super.onResume();
    }

    /* ================== LOAD DATA ================== */

    private void loadEventAndTickets() {
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    ownerId = doc.getString("ownerId");

                    edtTitle.setText(doc.getString("title"));
                    edtArtist.setText(doc.getString("artist"));
                    edtCategory.setText(doc.getString("category"));
                    edtPlace.setText(doc.getString("location"));
                    edtAddressDetail.setText(doc.getString("addressDetail"));
                    edtDescription.setText(doc.getString("description"));

                    Long totalSeats = doc.getLong("totalSeats");
                    if (totalSeats != null) {
                        edtTotalSeats.setText(String.valueOf(totalSeats));
                    }

                    Timestamp ts = doc.getTimestamp("startTime");
                    if (ts != null) {
                        selectedStartTime = ts;
                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm");
                        tvPickedDateTime.setText(sdf.format(ts.toDate()));
                    }

                    currentThumbnailUrl = doc.getString("thumbnail");
                    if (!TextUtils.isEmpty(currentThumbnailUrl)) {
                        Glide.with(this)
                                .load(currentThumbnailUrl)
                                .centerCrop()
                                .placeholder(R.drawable.sample_event)
                                .into(ivPreview);
                    }

                    // Load ticketTypes + seatCodes
                    loadTicketTypes();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Lỗi tải sự kiện: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void loadTicketTypes() {
        layoutTicketContainer.removeAllViews();
        ticketRows.clear();

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        return;
                    }

                    for (QueryDocumentSnapshot d : snap) {
                        String name  = d.getString("name");
                        Double price = d.getDouble("price");
                        Long quota   = d.getLong("quota");

                        @SuppressWarnings("unchecked")
                        List<String> seatList = (List<String>) d.get("seats");
                        HashSet<String> seatSet = new HashSet<>();
                        if (seatList != null) seatSet.addAll(seatList);

                        addTicketRow(name, price, quota, seatSet);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Lỗi tải loại vé: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    /* ================== DATE TIME ================== */

    private void showDateTimePicker() {
        Calendar now = Calendar.getInstance();

        DatePickerDialog dp = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog tp = new TimePickerDialog(
                            this,
                            (TimePicker timePicker, int hourOfDay, int minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                selectedStartTime = new Timestamp(picked.getTime());
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
                    tp.show();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    /* ================== TICKET ROW + SEATS ================== */

    private static class TicketRow {
        EditText edtName, edtPrice, edtQuota;
        TextView tvSeatInfo;
        android.view.View root;
        HashSet<String> seatCodes = new HashSet<>();

        TicketRow(EditText name, EditText price, EditText quota,
                  TextView seatInfo, android.view.View root) {
            this.edtName = name;
            this.edtPrice = price;
            this.edtQuota = quota;
            this.tvSeatInfo = seatInfo;
            this.root = root;
        }
    }

    private void addTicketRow(@Nullable String name,
                              @Nullable Double price,
                              @Nullable Long quota,
                              @Nullable HashSet<String> seatCodes) {

        android.view.View rowView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.item_ticket_type_input, layoutTicketContainer, false);

        EditText edtName  = rowView.findViewById(R.id.edtTicketName);
        EditText edtPrice = rowView.findViewById(R.id.edtTicketPrice);
        EditText edtQuota = rowView.findViewById(R.id.edtTicketQuota);
        TextView btnRemove = rowView.findViewById(R.id.btnRemoveRow);
        MaterialButton btnSetupSeats = rowView.findViewById(R.id.btnSetupSeats);
        TextView tvSeatInfo = rowView.findViewById(R.id.tvSeatStatus);

        if (name != null)  edtName.setText(name);
        if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
        if (quota != null) edtQuota.setText(String.valueOf(quota.intValue()));

        // 🔒 KHÔNG CHO SỬA GHẾ KHI EDIT
        btnSetupSeats.setEnabled(false);
        btnSetupSeats.setAlpha(0.4f);
        btnSetupSeats.setText("Không sửa ghế");
        // nếu muốn ẩn hẳn:
        // btnSetupSeats.setVisibility(View.GONE);

        // cũng nên khóa quota để không lệch với số ghế
        edtQuota.setEnabled(false);

        TicketRow row = new TicketRow(edtName, edtPrice, edtQuota, tvSeatInfo, rowView);
        if (seatCodes != null) {
            row.seatCodes.clear();
            row.seatCodes.addAll(seatCodes);
        }
        updateSeatInfoText(row);

        ticketRows.add(row);

        // cho phép xoá cả loại vé nếu muốn
        btnRemove.setOnClickListener(v -> {
            layoutTicketContainer.removeView(rowView);
            ticketRows.remove(row);
        });

        layoutTicketContainer.addView(rowView);
    }

    private void updateSeatInfoText(TicketRow row) {
        if (row.tvSeatInfo != null) {
            int count = row.seatCodes.size();
            if (count == 0) {
                row.tvSeatInfo.setText("Chưa có ghế (đã khoá)");
            } else {
                row.tvSeatInfo.setText("Đã chọn " + count + " ghế (không chỉnh sửa)");
            }
        }
    }

    /* ================== SAVE ================== */

    private void saveChanges() {
        String title    = text(edtTitle);
        String artist   = text(edtArtist);
        String cat      = text(edtCategory);
        String place    = text(edtPlace);
        String addrDtl  = text(edtAddressDetail);
        String desc     = text(edtDescription);
        String sSeats   = text(edtTotalSeats);

        if (TextUtils.isEmpty(title)) {
            edtTitle.setError("Nhập tên sự kiện");
            return;
        }
        if (TextUtils.isEmpty(cat)) {
            edtCategory.setError("Nhập thể loại");
            return;
        }
        if (TextUtils.isEmpty(place)) {
            edtPlace.setError("Nhập địa điểm tổ chức");
            return;
        }
        if (TextUtils.isEmpty(addrDtl)) {
            edtAddressDetail.setError("Nhập địa chỉ chi tiết");
            return;
        }
        if (selectedStartTime == null) {
            Toast.makeText(this, "Chọn ngày & giờ diễn ra", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(sSeats)) {
            edtTotalSeats.setError("Nhập tổng số vé");
            return;
        }

        int totalSeats;
        try {
            totalSeats = Integer.parseInt(sSeats);
        } catch (NumberFormatException e) {
            edtTotalSeats.setError("Số vé không hợp lệ");
            return;
        }
        if (totalSeats <= 0) {
            edtTotalSeats.setError("Tổng vé phải > 0");
            return;
        }

        // LẤY DANH SÁCH LOẠI VÉ (ghế giữ nguyên, quota đã khóa)
        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;

        HashSet<String> allSeatsGlobal = new HashSet<>();

        for (TicketRow row : ticketRows) {
            String name   = text(row.edtName);
            String sPrice = text(row.edtPrice);
            String sQuota = text(row.edtQuota);

            if (name.isEmpty() && sPrice.isEmpty() && sQuota.isEmpty()) {
                continue;
            }

            if (name.isEmpty()) {
                row.edtName.setError("Nhập tên loại vé");
                return;
            }
            if (sPrice.isEmpty()) {
                row.edtPrice.setError("Nhập giá vé");
                return;
            }
            if (sQuota.isEmpty()) {
                row.edtQuota.setError("Nhập số vé");
                return;
            }

            double price;
            int quota;
            try {
                price = Double.parseDouble(sPrice);
                quota = Integer.parseInt(sQuota);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Giá / số vé không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            if (quota <= 0) {
                row.edtQuota.setError("Số vé phải > 0");
                return;
            }

            // Vẫn check consistency (ghế cũ không đổi)
            if (row.seatCodes.size() != quota) {
                Toast.makeText(this,
                        "Loại vé \"" + name + "\" có số ghế và quota không khớp. Kiểm tra lại trong DB.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            for (String c : row.seatCodes) {
                if (!allSeatsGlobal.add(c)) {
                    Toast.makeText(this,
                            "Ghế " + c + " bị trùng giữa các loại vé. Kiểm tra lại trong DB.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", price);
            ticket.put("quota", quota);
            // ❗ KHÔNG reset sold về 0, giữ nguyên
            // (nếu muốn, bạn có thể đọc "sold" từ snapshot, ở đây tạm để 0)
            ticket.put("sold", 0);
            ticket.put("seats", new ArrayList<>(row.seatCodes));

            ticketTypes.add(ticket);

            totalSeatsFromTickets += quota;
            if (price > 0 && price < minPriceFromTickets) {
                minPriceFromTickets = price;
            }
        }

        double price;
        if (ticketTypes.isEmpty()) {
            price = 0d;
        } else {
            if (totalSeatsFromTickets > totalSeats) {
                Toast.makeText(this,
                        "Tổng số vé các loại (" + totalSeatsFromTickets +
                                ") lớn hơn số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (totalSeatsFromTickets != totalSeats) {
                Toast.makeText(this,
                        "Tổng số vé các loại phải bằng tổng số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }
            price = (minPriceFromTickets == Double.MAX_VALUE) ? 0d : minPriceFromTickets;
        }

        btnSave.setEnabled(false);

        // Nếu có ảnh mới → upload rồi update
        if (selectedImageUri != null && ownerId != null) {
            StorageReference ref = FirebaseStorage.getInstance()
                    .getReference("event_covers/" + ownerId + "/" + eventId + ".jpg");

            ref.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot ->
                            ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                                currentThumbnailUrl = downloadUri.toString();
                                updateEventInFirestore(title, artist, cat, place, addrDtl, desc,
                                        selectedStartTime, price, totalSeats, currentThumbnailUrl, ticketTypes);
                            })
                    )
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Upload ảnh thất bại: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            // giữ nguyên thumbnail cũ
            updateEventInFirestore(title, artist, cat, place, addrDtl, desc,
                    selectedStartTime, price, totalSeats, currentThumbnailUrl, ticketTypes);
        }
    }

    private void updateEventInFirestore(String title,
                                        String artist,
                                        String cat,
                                        String place,
                                        String addrDtl,
                                        String desc,
                                        Timestamp startTime,
                                        double price,
                                        int totalSeats,
                                        @Nullable String thumbnailUrl,
                                        List<Map<String, Object>> ticketTypes) {

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("artist", artist);
        data.put("category", cat);
        data.put("location", place);
        data.put("addressDetail", addrDtl);
        data.put("description", desc);
        data.put("startTime", startTime);
        data.put("price", price);
        data.put("totalSeats", totalSeats);
        // ❗ KHÔNG reset availableSeats nếu muốn giữ số ghế còn lại
        // data.put("availableSeats", totalSeats);
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (!TextUtils.isEmpty(thumbnailUrl)) {
            data.put("thumbnail", thumbnailUrl);
        }

        db.collection("events")
                .document(eventId)
                .update(data)
                .addOnSuccessListener(unused -> {
                    // Cập nhật ticketTypes: xoá cũ, thêm mới (ghế giữ nguyên như trên)
                    db.collection("events").document(eventId)
                            .collection("ticketTypes")
                            .get()
                            .addOnSuccessListener(snap -> {
                                for (QueryDocumentSnapshot d : snap) {
                                    d.getReference().delete();
                                }
                                for (Map<String, Object> ticket : ticketTypes) {
                                    db.collection("events")
                                            .document(eventId)
                                            .collection("ticketTypes")
                                            .add(ticket);
                                }

                                Toast.makeText(this,
                                        "Cập nhật sự kiện thành công",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSave.setEnabled(true);
                                Toast.makeText(this,
                                        "Lỗi cập nhật loại vé: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Lỗi cập nhật sự kiện: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /* ================== HELPER ================== */

    private String text(EditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
