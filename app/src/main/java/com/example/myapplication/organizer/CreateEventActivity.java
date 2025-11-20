package com.example.myapplication.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;


public class CreateEventActivity extends AppCompatActivity {

    private EditText edtTitle, edtArtist, edtCategory, edtLocation,
            edtDescription, edtPrice, edtTotalSeats;
    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private MaterialButton btnPickDateTime, btnPickImage, btnSave;

    private Timestamp selectedStartTime;
    private Uri selectedImageUri;

    private String editingEventId = null;

    private ActivityResultLauncher<String> pickImageLauncher;

    private LinearLayout layoutTicketContainer;
    private MaterialButton btnAddTicketType;

    // lưu các dòng nhập loại vé
    private final List<TicketRow> ticketRows = new ArrayList<>();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        // ánh xạ view
        edtTitle       = findViewById(R.id.edtTitle);
        edtArtist      = findViewById(R.id.edtArtist);
        edtCategory    = findViewById(R.id.edtCategory);
        edtLocation    = findViewById(R.id.edtLocation);
        edtDescription = findViewById(R.id.edtDescription);
        edtPrice       = findViewById(R.id.edtPrice);
        edtTotalSeats  = findViewById(R.id.edtTotalSeats);
        tvPickedDateTime = findViewById(R.id.tvPickedDateTime);
        ivPreview      = findViewById(R.id.ivPreview);
        btnPickDateTime = findViewById(R.id.btnPickDateTime);
        btnPickImage   = findViewById(R.id.btnPickImage);
        btnSave        = findViewById(R.id.btnSave);


        layoutTicketContainer = findViewById(R.id.layoutTicketContainer);
        btnAddTicketType     = findViewById(R.id.btnAddTicketType);



        btnAddTicketType.setOnClickListener(v -> {
            addTicketRow();
        });


        // nút back trên action bar (nếu đang dùng)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tạo sự kiện mới");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Lấy eventId nếu đang ở chế độ EDIT
        editingEventId = getIntent().getStringExtra("EXTRA_EVENT_ID");

        if (getSupportActionBar() != null) {
            if (editingEventId == null) {
                getSupportActionBar().setTitle("Tạo sự kiện mới");
            } else {
                getSupportActionBar().setTitle("Chỉnh sửa sự kiện");
            }
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (editingEventId != null) {
            // 👉 Load event từ Firestore + fill form
            loadEventForEdit(editingEventId);
            btnSave.setText("Cập nhật sự kiện");
        }


        // chọn ngày giờ
        btnPickDateTime.setOnClickListener(v -> showDateTimePicker());

        // chọn ảnh
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivPreview.setImageURI(uri);
                    }
                }
        );
        btnPickImage.setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        // lưu sự kiện
        btnSave.setOnClickListener(v -> saveEvent());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void showDateTimePicker() {
        Calendar now = Calendar.getInstance();

        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // sau khi chọn ngày -> chọn giờ
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog tp = new TimePickerDialog(
                            this,
                            (timePicker, hourOfDay, minute) -> {
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

    private void addTicketRow() {
        addTicketRowWithData(null, null, null);
    }

    private void addTicketRowWithData(@Nullable String name,
                                      @Nullable Double price,
                                      @Nullable Long quota) {
        View rowView = LayoutInflater.from(this)
                .inflate(R.layout.item_ticket_type_input, layoutTicketContainer, false);

        EditText edtName  = rowView.findViewById(R.id.edtTicketName);
        EditText edtPrice = rowView.findViewById(R.id.edtTicketPrice);
        EditText edtQuota = rowView.findViewById(R.id.edtTicketQuota);
        TextView btnRemove = rowView.findViewById(R.id.btnRemoveRow);

        if (name != null)  edtName.setText(name);
        if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
        if (quota != null) edtQuota.setText(String.valueOf(quota.intValue()));

        TicketRow row = new TicketRow(edtName, edtPrice, edtQuota, rowView);
        ticketRows.add(row);

        btnRemove.setOnClickListener(v -> {
            layoutTicketContainer.removeView(rowView);
            ticketRows.remove(row);
        });

        layoutTicketContainer.addView(rowView);
    }


    private static class TicketRow {
        EditText edtName, edtPrice, edtQuota;
        View root;

        TicketRow(EditText name, EditText price, EditText quota, View root) {
            this.edtName = name;
            this.edtPrice = price;
            this.edtQuota = quota;
            this.root = root;
        }
    }


    private void saveEvent() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String title   = text(edtTitle);
        String artist  = text(edtArtist);
        String cat     = text(edtCategory);
        String loc     = text(edtLocation);
        String desc    = text(edtDescription);

        String sSeats  = text(edtTotalSeats);  // tổng số ghế


        if (TextUtils.isEmpty(title)) {
            edtTitle.setError("Nhập tên sự kiện");
            return;
        }
        if (TextUtils.isEmpty(cat)) {
            edtCategory.setError("Nhập thể loại");
            return;
        }
        if (TextUtils.isEmpty(loc)) {
            edtLocation.setError("Nhập địa điểm");
            return;
        }
        if (selectedStartTime == null) {
            Toast.makeText(this, "Chọn ngày & giờ diễn ra", Toast.LENGTH_SHORT).show();
            return;
        }

        // ====== LẤY DANH SÁCH LOẠI VÉ TỪ CÁC DÒNG INPUT ======
        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;

        for (TicketRow row : ticketRows) {
            String name  = row.edtName.getText().toString().trim();
            String sPrice = row.edtPrice.getText().toString().trim();
            String sQuota = row.edtQuota.getText().toString().trim();

            // nếu cả 3 ô đều trống thì bỏ qua dòng này
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

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", price);
            ticket.put("quota", quota);
            ticket.put("sold", 0);
            ticketTypes.add(ticket);

            totalSeatsFromTickets += quota;
            if (price > 0 && price < minPriceFromTickets) {
                minPriceFromTickets = price;
            }
        }

        double price;
        int totalSeats;

// ===== Trường hợp KHÔNG CÓ loại vé → sự kiện miễn phí =====
        if (ticketTypes.isEmpty()) {

            // Bắt buộc vẫn phải nhập tổng số vé
            if (TextUtils.isEmpty(sSeats)) {
                edtTotalSeats.setError("Nhập tổng số vé");
                return;
            }

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

            // Không có loại vé ⇒ FREE
            price = 0d;

// ===== Có loại vé ⇒ kiểm tra quota & tính giá min =====
        } else {
            if (TextUtils.isEmpty(sSeats)) {
                edtTotalSeats.setError("Nhập tổng số vé");
                return;
            }

            try {
                totalSeats = Integer.parseInt(sSeats);
            } catch (NumberFormatException e) {
                edtTotalSeats.setError("Số vé không hợp lệ");
                return;
            }

            // Tổng quota không được > tổng ghế
            if (totalSeatsFromTickets > totalSeats) {
                Toast.makeText(this,
                        "Tổng số vé các loại (" + totalSeatsFromTickets +
                                ") lớn hơn số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Bạn muốn đúng bằng nhau
            if (totalSeatsFromTickets != totalSeats) {
                Toast.makeText(this,
                        "Tổng số vé các loại phải bằng tổng số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Giá event = giá thấp nhất trong các loại vé
            price = (minPriceFromTickets == Double.MAX_VALUE) ? 0d : minPriceFromTickets;
        }


        btnSave.setEnabled(false);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String ownerId = user.getUid();
        String eventId = (editingEventId != null)
                ? editingEventId
                : UUID.randomUUID().toString();

        if (selectedImageUri != null) {
            FirebaseStorage.getInstance()
                    .getReference("event_covers/" + ownerId + "/" + eventId + ".jpg")
                    .putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot ->
                            taskSnapshot.getStorage().getDownloadUrl()
                                    .addOnSuccessListener(uri ->
                                            writeEventToFirestore(
                                                    db, eventId, ownerId,
                                                    title, artist, cat, loc, desc,
                                                    selectedStartTime, price, totalSeats,
                                                    uri.toString(), ticketTypes
                                            )
                                    )
                                    .addOnFailureListener(e -> {
                                        btnSave.setEnabled(true);
                                        Toast.makeText(this,
                                                "Lỗi lấy URL ảnh: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    })
                    )
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Lỗi upload ảnh: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            writeEventToFirestore(
                    db, eventId, ownerId,
                    title, artist, cat, loc, desc,
                    selectedStartTime, price, totalSeats,
                    null, ticketTypes
            );
        }
    }


    private void writeEventToFirestore(FirebaseFirestore db,
                                       String eventId,
                                       String ownerId,
                                       String title,
                                       String artist,
                                       String cat,
                                       String loc,
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
        data.put("location", loc);
        data.put("description", desc);
        data.put("ownerId", ownerId);
        data.put("startTime", startTime);
        data.put("price", price);
        data.put("totalSeats", totalSeats);
        data.put("availableSeats", totalSeats); // lúc mới tạo = tổng vé
        data.put("status", "active");           // hoặc "draft" tuỳ ý bạn
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());


        if (editingEventId == null) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }

        if (thumbnailUrl != null) {
            data.put("thumbnail", thumbnailUrl);
        }

        if (editingEventId == null) {
            db.collection("events")
                    .document(eventId)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        saveTicketTypes(db, eventId, ticketTypes);
                        Toast.makeText(this,
                                "Tạo sự kiện thành công!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Lỗi lưu sự kiện: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            db.collection("events")
                    .document(eventId)
                    .update(data)
                    .addOnSuccessListener(unused -> {
                        saveTicketTypes(db, eventId, ticketTypes);
                        Toast.makeText(this,
                                "Cập nhật sự kiện thành công!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Lỗi cập nhật sự kiện: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        }

    }

    private void saveTicketTypes(FirebaseFirestore db,
                                 String eventId,
                                 List<Map<String, Object>> ticketTypes) {

        // Xoá hết ticketTypes cũ trước
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        d.getReference().delete();
                    }

                    // Không có loại vé mới → event free, chỉ cần xoá là xong
                    if (ticketTypes == null || ticketTypes.isEmpty()) {
                        return;
                    }

                    // Ghi lại toàn bộ loại vé mới
                    for (Map<String, Object> ticket : ticketTypes) {
                        db.collection("events")
                                .document(eventId)
                                .collection("ticketTypes")
                                .add(ticket);
                    }
                });
    }




    private void loadEventForEdit(String eventId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }



                    edtTitle.setText(doc.getString("title"));
                    edtArtist.setText(doc.getString("artist"));
                    edtCategory.setText(doc.getString("category"));
                    edtLocation.setText(doc.getString("location"));
                    edtDescription.setText(doc.getString("description"));

                    Double price = doc.getDouble("price");
                    Long totalSeats = doc.getLong("totalSeats");
                    if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
                    if (totalSeats != null) edtTotalSeats.setText(String.valueOf(totalSeats));

                    Timestamp ts = doc.getTimestamp("startTime");
                    if (ts != null) {
                        selectedStartTime = ts;
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm");
                        tvPickedDateTime.setText(sdf.format(ts.toDate()));
                    }

                    // thumbnail: nếu muốn, bạn có thể dùng Glide để load vào ivPreview

                    loadTicketTypesForEdit(eventId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Lỗi tải sự kiện: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void loadTicketTypesForEdit(String eventId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Xoá các dòng cũ trên UI (tránh bị trùng khi vào màn nhiều lần)
        layoutTicketContainer.removeAllViews();
        ticketRows.clear();

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        // Không có loại vé → sự kiện free, không cần auto thêm dòng
                        return;
                    }

                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        String name   = d.getString("name");
                        Double price  = d.getDouble("price");
                        Long quota    = d.getLong("quota");

                        addTicketRowWithData(name, price, quota);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Lỗi tải loại vé: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }



    private String text(EditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
