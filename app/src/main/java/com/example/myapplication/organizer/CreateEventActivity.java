package com.example.myapplication.organizer;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

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

    private SwitchMaterial switchFeatured;
    private EditText edtPromoTag;

    private Timestamp selectedStartTime;
    private Uri selectedImageUri;

    private String editingEventId = null;

    private LinearLayout layoutTicketContainer;
    private MaterialButton btnAddTicketType;

    private String eventIdForSeats;

    private final List<TicketRow> ticketRows = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

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

        switchFeatured = findViewById(R.id.switchFeatured);
        edtPromoTag    = findViewById(R.id.edtPromoTag);

        editingEventId = getIntent().getStringExtra("EXTRA_EVENT_ID");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (editingEventId != null) {
            eventIdForSeats = editingEventId;
        } else {
            eventIdForSeats = db.collection("events").document().getId();
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
            addTicketRow();
        }

        btnAddTicketType.setOnClickListener(v -> addTicketRow());

        // chọn ngày giờ
        btnPickDateTime.setOnClickListener(v -> showDateTimePicker());

        // chọn ảnh
        androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher =
                registerForActivityResult(
                        new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                        uri -> {
                            if (uri != null) {
                                selectedImageUri = uri;
                                ivPreview.setImageURI(uri);
                            }
                        }
                );
        btnPickImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // lưu
        btnSave.setOnClickListener(v -> saveEvent());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /* ========= REFRESH GHẾ KHI QUAY LẠI ========= */

    @Override
    protected void onResume() {
        super.onResume();
        for (TicketRow row : ticketRows) {
            String name = row.edtName.getText().toString().trim();
            if (name.isEmpty()) continue;

            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);
        }
    }

    /* ========= CHỌN NGÀY GIỜ ========= */

    private void showDateTimePicker() {
        Calendar now = Calendar.getInstance();

        android.app.DatePickerDialog dp = new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    android.app.TimePickerDialog tp = new android.app.TimePickerDialog(
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

    /* ========= DÒNG LOẠI VÉ ========= */

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

        tvSeatInfo.setVisibility(View.VISIBLE);
        tvSeatInfo.setText("Chưa chọn ghế");

        if (name != null)  edtName.setText(name);
        if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
        if (quota != null) edtQuota.setText(String.valueOf(quota.intValue()));

        TicketRow row = new TicketRow(edtName, edtPrice, edtQuota, tvSeatInfo, rowView);
        ticketRows.add(row);

        if (name != null) {
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
        }
        updateSeatInfoText(row);

        btnRemove.setOnClickListener(v -> {
            layoutTicketContainer.removeView(rowView);
            ticketRows.remove(row);
            SeatLayoutConfigActivity.clearSeatsForTicket(eventIdForSeats, name);
        });

        btnSetupSeats.setOnClickListener(v -> {
            String ticketName = edtName.getText().toString().trim();
            String sQuota = edtQuota.getText().toString().trim();

            if (ticketName.isEmpty()) {
                edtName.setError("Nhập loại vé trước");
                return;
            }
            if (sQuota.isEmpty()) {
                edtQuota.setError("Nhập số vé trước");
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

            int totalEventSeats = 0;
            String sTotal = edtTotalSeats.getText() != null
                    ? edtTotalSeats.getText().toString().trim()
                    : "";
            try {
                totalEventSeats = Integer.parseInt(sTotal);
            } catch (NumberFormatException ignored) {}

            if (totalEventSeats <= 0) {
                totalEventSeats = quotaVal;  // fallback
            }

            android.content.Intent i = new android.content.Intent(
                    CreateEventActivity.this,
                    SeatLayoutConfigActivity.class
            );
            i.putExtra(SeatLayoutConfigActivity.EXTRA_EVENT_ID, eventIdForSeats);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_TICKET_NAME, ticketName);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_MAX_SEATS, quotaVal);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_TOTAL_EVENT_SEATS, totalEventSeats);
            startActivity(i);
        });

        layoutTicketContainer.addView(rowView);
    }

    /* ========= MODEL TICKET ROW ========= */

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

        row.tvSeatInfo.setVisibility(View.VISIBLE);

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

        boolean isFeatured = switchFeatured != null && switchFeatured.isChecked();
        String promoTag = edtPromoTag != null
                ? edtPromoTag.getText().toString().trim()
                : "";
        int featuredBoostScore = isFeatured ? 100 : 0;

        // ====== LOẠI VÉ + GHẾ ======
        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;

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

            // Lấy ghế đã vẽ tạm
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);

            if (!seats.isEmpty()) {
                // quota == số ghế
                if (seats.size() != quota) {
                    Toast.makeText(this,
                            "Loại vé \"" + name + "\" phải chọn đúng "
                                    + quota + " ghế (hiện đang " + seats.size() + ")",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // không trùng ghế giữa các loại vé
                for (String c : seats) {
                    if (!allSeatsGlobal.add(c)) {
                        Toast.makeText(this,
                                "Ghế " + c + " đang bị dùng bởi nhiều loại vé",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", price);
            ticket.put("quota", quota);
            ticket.put("sold", 0);
            if (!seats.isEmpty()) {
                ticket.put("seats", seats);
            }

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
        String eventId = eventIdForSeats;

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
                                                    isFeatured, featuredBoostScore, promoTag
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
                    isFeatured, featuredBoostScore, promoTag
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
                                       boolean featured,
                                       int featuredBoostScore,
                                       @Nullable String promoTag) {

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

        data.put("featured", featured);
        data.put("featuredBoostScore", featuredBoostScore);
        if (promoTag != null && !promoTag.isEmpty()) {
            data.put("promoTag", promoTag);
        } else {
            data.remove("promoTag");
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

        if (ticketTypes == null || ticketTypes.isEmpty()) {
            return;
        }

        if (editingEventId != null) {
            db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            d.getReference().delete();
                        }
                    });

            db.collection("events")
                    .document(eventId)
                    .collection("seats")
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            d.getReference().delete();
                        }
                    });
        }

        for (Map<String, Object> ticket : ticketTypes) {
            String ticketName  = (String) ticket.get("name");

            Double priceObj    = (Double) ticket.get("price");
            long seatPrice     = priceObj == null ? 0L : priceObj.longValue();

            @SuppressWarnings("unchecked")
            List<String> seatCodes =
                    (List<String>) ticket.get("seats");

            // Lưu ticketType
            db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .add(ticket)
                    .addOnSuccessListener(ticketDoc -> {

                        // Loại vé không cấu hình sơ đồ ghế → không tạo seats
                        if (seatCodes == null || seatCodes.isEmpty()) {
                            return;
                        }

                        // Tạo từng document ghế cho collection seats
                        for (String code : seatCodes) {
                            Map<String, Object> seat = new HashMap<>();

                            String row = "";
                            int number = 0;
                            try {
                                if (code != null && code.length() > 0) {
                                    row = code.substring(0, 1);
                                    if (code.length() > 1) {
                                        number = Integer.parseInt(code.substring(1));
                                    }
                                }
                            } catch (Exception ignored) {}

                            seat.put("label", code);
                            seat.put("row", row);
                            seat.put("number", number);
                            seat.put("type", ticketName);
                            seat.put("price", seatPrice);
                            seat.put("status", "available");
                            seat.put("eventId", eventId);

                            db.collection("events")
                                    .document(eventId)
                                    .collection("seats")
                                    .document(code)
                                    .set(seat);
                        }

                    });
        }
    }

    /* ========= LOAD EDIT ========= */

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

                    Boolean featured = doc.getBoolean("featured");
                    if (featured != null && switchFeatured != null) {
                        switchFeatured.setChecked(featured);
                    }

                    String promo = doc.getString("promoTag");
                    if (promo != null && edtPromoTag != null) {
                        edtPromoTag.setText(promo);
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
