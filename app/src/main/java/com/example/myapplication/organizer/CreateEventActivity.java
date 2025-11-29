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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class CreateEventActivity extends AppCompatActivity {

    private EditText edtTitle, edtArtist, edtCategory,
            edtPlace, edtAddressDetail,
            edtDescription, edtTotalSeats;
    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private MaterialButton btnPickDateTime, btnPickImage, btnSave;

    // 🔹 Marketing
    private SwitchMaterial switchFeatured;
    private EditText edtPromoTag;

    private Timestamp selectedStartTime;
    private Uri selectedImageUri;

    private String editingEventId = null;

    private ActivityResultLauncher<String> pickImageLauncher;

    private LinearLayout layoutTicketContainer;
    private MaterialButton btnAddTicketType;

    // id dùng chung cho event + sơ đồ ghế tạm
    private String eventIdForSeats;

    // lưu các dòng nhập loại vé
    private final List<TicketRow> ticketRows = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        // ánh xạ view đúng với XML
        edtTitle          = findViewById(R.id.edtTitle);
        edtArtist         = findViewById(R.id.edtArtist);
        edtCategory       = findViewById(R.id.edtCategory);
        edtDescription    = findViewById(R.id.edtDescription);
        edtPlace          = findViewById(R.id.edtPlace);
        edtAddressDetail  = findViewById(R.id.edtAddressDetail);
        edtTotalSeats     = findViewById(R.id.edtTotalSeats);

        tvPickedDateTime  = findViewById(R.id.tvPickedDateTime);
        ivPreview         = findViewById(R.id.ivPreview);
        btnPickDateTime   = findViewById(R.id.btnPickDateTime);
        btnPickImage      = findViewById(R.id.btnPickImage);
        btnSave           = findViewById(R.id.btnSave);

        layoutTicketContainer = findViewById(R.id.layoutTicketContainer);
        btnAddTicketType      = findViewById(R.id.btnAddTicketType);

        // 🔹 Marketing views
        switchFeatured = findViewById(R.id.switchFeatured);   // SwitchMaterial trong layout
        edtPromoTag    = findViewById(R.id.edtPromoTag);      // EditText cho text ưu đãi

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tạo sự kiện mới");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Lấy eventId nếu đang ở chế độ EDIT
        editingEventId = getIntent().getStringExtra("EXTRA_EVENT_ID");

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // TẠO eventIdForSeats (dùng cả cho event và sơ đồ ghế tạm)
        if (editingEventId != null) {
            eventIdForSeats = editingEventId;      // đang edit
        } else {
            eventIdForSeats = db.collection("events").document().getId();  // tạo mới
        }

        if (getSupportActionBar() != null) {
            if (editingEventId == null) {
                getSupportActionBar().setTitle("Tạo sự kiện mới");
            } else {
                getSupportActionBar().setTitle("Chỉnh sửa sự kiện");
            }
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (editingEventId != null) {
            loadEventForEdit(editingEventId);
            btnSave.setText("Cập nhật sự kiện");
        } else {
            // TẠO MỚI: auto thêm sẵn 1 dòng loại vé để user thấy UI
            addTicketRow();
        }

        btnAddTicketType.setOnClickListener(v -> addTicketRow());

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

    /* ========= REFRESH THÔNG TIN GHẾ SAU KHI VẼ SƠ ĐỒ ========= */

    @Override
    protected void onResume() {
        super.onResume();
        // Mỗi lần quay lại màn, đọc TEMP_SEATS để cập nhật text "Đã chọn X ghế"
        for (TicketRow row : ticketRows) {
            String name = row.edtName.getText().toString().trim();
            if (name.isEmpty()) continue;

            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);
        }
    }

    private void showDateTimePicker() {
        Calendar now = Calendar.getInstance();

        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
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
        MaterialButton btnSetupSeats = rowView.findViewById(R.id.btnSetupSeats);
        TextView tvSeatInfo = rowView.findViewById(R.id.tvSeatStatus);

        if (name != null)  edtName.setText(name);
        if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
        if (quota != null) edtQuota.setText(String.valueOf(quota.intValue()));

        TicketRow row = new TicketRow(edtName, edtPrice, edtQuota, tvSeatInfo, rowView);
        ticketRows.add(row);

        // nếu đã có tên (trường hợp edit), thử load ghế tạm
        if (name != null) {
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
        }
        updateSeatInfoText(row);

        btnRemove.setOnClickListener(v -> {
            layoutTicketContainer.removeView(rowView);
            ticketRows.remove(row);
        });

        btnSetupSeats.setOnClickListener(v -> {
            String ticketName = edtName.getText().toString().trim();
            String sQuota = edtQuota.getText().toString().trim();

            if (ticketName.isEmpty()) {
                edtName.setError("Nhập tên loại vé trước");
                return;
            }
            if (sQuota.isEmpty()) {
                edtQuota.setError("Nhập số vé (quota) trước");
                return;
            }

            int quotaVal;
            try {
                quotaVal = Integer.parseInt(sQuota);
            } catch (NumberFormatException e) {
                edtQuota.setError("Quota không hợp lệ");
                return;
            }

            if (quotaVal <= 0) {
                edtQuota.setError("Quota phải > 0");
                return;
            }

            // mở màn vẽ sơ đồ ghế – tạm lưu vào TEMP_SEATS, chưa đẩy Firestore
            android.content.Intent i = new android.content.Intent(
                    CreateEventActivity.this,
                    SeatLayoutConfigActivity.class
            );
            i.putExtra(SeatLayoutConfigActivity.EXTRA_EVENT_ID, eventIdForSeats);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_TICKET_NAME, ticketName);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_MAX_SEATS, quotaVal);
            startActivity(i);
        });

        layoutTicketContainer.addView(rowView);
    }

    /* ========= MODEL ========= */

    private static class TicketRow {
        EditText edtName, edtPrice, edtQuota;
        TextView tvSeatInfo;
        View root;
        HashSet<String> seatCodes = new HashSet<>();

        TicketRow(EditText name, EditText price, EditText quota,
                  TextView tvSeatInfo, View root) {
            this.edtName = name;
            this.edtPrice = price;
            this.edtQuota = quota;
            this.tvSeatInfo = tvSeatInfo;
            this.root = root;
        }
    }

    private void updateSeatInfoText(TicketRow row) {
        if (row.tvSeatInfo == null) return;
        int count = row.seatCodes.size();
        if (count == 0) {
            row.tvSeatInfo.setText("Chưa chọn ghế");
        } else {
            row.tvSeatInfo.setText("Đã chọn " + count + " ghế");
        }
    }

    /* ========= SAVE EVENT ========= */

    private void saveEvent() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String title    = text(edtTitle);
        String artist   = text(edtArtist);
        String cat      = text(edtCategory);
        String place    = text(edtPlace);
        String addrDtl  = text(edtAddressDetail);
        String desc     = text(edtDescription);
        String sSeats   = text(edtTotalSeats);

        // 🔹 Marketing
        boolean isFeatured = switchFeatured != null && switchFeatured.isChecked();
        String promoTag    = text(edtPromoTag);

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

        // ====== LẤY DANH SÁCH LOẠI VÉ + GHẾ ======
        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;

        // để đảm bảo không trùng ghế giữa các loại vé
        HashSet<String> allSeatsGlobal = new HashSet<>();

        for (TicketRow row : ticketRows) {
            String name   = row.edtName.getText().toString().trim();
            String sPrice = row.edtPrice.getText().toString().trim();
            String sQuota = row.edtQuota.getText().toString().trim();

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

            // 🔥 Lấy ghế đã vẽ tạm trong SeatLayoutConfigActivity
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);

            // ✅ check: số ghế chọn phải = quota
            if (seats.size() != quota) {
                Toast.makeText(this,
                        "Loại vé \"" + name + "\" phải chọn đúng "
                                + quota + " ghế (hiện đang " + seats.size() + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // ✅ check: không trùng ghế giữa các loại vé
            for (String c : seats) {
                if (!allSeatsGlobal.add(c)) {
                    Toast.makeText(this,
                            "Ghế " + c + " đang bị dùng bởi nhiều loại vé",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", price);
            ticket.put("quota", quota);
            ticket.put("sold", 0);
            ticket.put("seats", seats); // lưu danh sách ghế ngay trong doc ticketTypes

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

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String ownerId = user.getUid();
        String eventId = eventIdForSeats;   // luôn dùng id đã tạo từ trước

        if (selectedImageUri != null) {
            FirebaseStorage.getInstance()
                    .getReference("event_covers/" + ownerId + "/" + eventId + ".jpg")
                    .putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot ->
                            taskSnapshot.getStorage().getDownloadUrl()
                                    .addOnSuccessListener(uri ->
                                            writeEventToFirestore(
                                                    db, eventId, ownerId,
                                                    title, artist, cat,
                                                    place, addrDtl, desc,
                                                    selectedStartTime, price, totalSeats,
                                                    uri.toString(), ticketTypes,
                                                    isFeatured, promoTag
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
                    title, artist, cat,
                    place, addrDtl, desc,
                    selectedStartTime, price, totalSeats,
                    null, ticketTypes,
                    isFeatured, promoTag
            );
        }
    }

    private void writeEventToFirestore(FirebaseFirestore db,
                                       String eventId,
                                       String ownerId,
                                       String title,
                                       String artist,
                                       String cat,
                                       String place,
                                       String addrDtl,
                                       String desc,
                                       Timestamp startTime,
                                       double price,
                                       int totalSeats,
                                       @Nullable String thumbnailUrl,
                                       List<Map<String, Object>> ticketTypes,
                                       boolean isFeatured,
                                       String promoTag) {

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("artist", artist);
        data.put("category", cat);
        data.put("location", place);
        data.put("addressDetail", addrDtl);
        data.put("description", desc);
        data.put("ownerId", ownerId);
        data.put("startTime", startTime);
        data.put("price", price);
        data.put("totalSeats", totalSeats);
        data.put("availableSeats", totalSeats);
        data.put("status", "active");
        data.put("updatedAt", FieldValue.serverTimestamp());

        // 🔹 Marketing fields
        data.put("featured", isFeatured);
        data.put("featuredBoostScore", isFeatured ? 10 : 0);
        if (!TextUtils.isEmpty(promoTag)) {
            data.put("promoTag", promoTag);
        }

        if (editingEventId == null) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }

        if (thumbnailUrl != null) {
            data.put("thumbnail", thumbnailUrl);
        }

        if (editingEventId == null) {
            // tạo mới
            db.collection("events")
                    .document(eventId)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        saveTicketTypes(db, eventId, ticketTypes);
                        // clear ghế tạm sau khi lưu xong
                        SeatLayoutConfigActivity.clearSeatsForEvent(eventIdForSeats);
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
            // cập nhật
            db.collection("events")
                    .document(eventId)
                    .update(data)
                    .addOnSuccessListener(unused -> {
                        saveTicketTypes(db, eventId, ticketTypes);
                        SeatLayoutConfigActivity.clearSeatsForEvent(eventIdForSeats);
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

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        d.getReference().delete();
                    }

                    if (ticketTypes == null || ticketTypes.isEmpty()) {
                        return;
                    }

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

                    // 🔹 Marketing: load lại
                    if (switchFeatured != null) {
                        Boolean featured = doc.getBoolean("featured");
                        switchFeatured.setChecked(featured != null && featured);
                    }
                    if (edtPromoTag != null) {
                        String promoTag = doc.getString("promoTag");
                        if (promoTag != null) edtPromoTag.setText(promoTag);
                    }

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

                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        String name   = d.getString("name");
                        Double price  = d.getDouble("price");
                        Long quota    = d.getLong("quota");

                        addTicketRowWithData(name, price, quota);
                        // Nếu cần, có thể đọc thêm "seats" từ Firestore về và
                        // push vào SeatLayoutConfigActivity, tuỳ bạn triển khai thêm.
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
