package com.example.myapplication.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrganizerCreateEventFragment extends Fragment {

    private EditText edtTitle, edtArtist, edtCategory,
            edtPlace, edtAddressDetail,
            edtDescription, edtTotalSeats;

    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private View btnSave, btnPickDateTime, btnPickImage;

    private FirebaseFirestore db;

    private Calendar selectedStartDateTime;
    private Calendar selectedEndDateTime;

    private ActivityResultLauncher<String> pickImageLauncher;
    private Uri selectedImageUri;
    private String thumbnailStr;

    private View btnAddTicketType;
    private ViewGroup layoutTicketContainer;

    private String eventIdForSeats;

    private final List<TicketRow> ticketRows = new ArrayList<>();

    // Category hợp lệ
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
        selectedStartDateTime = null;
        selectedEndDateTime   = null;
        thumbnailStr = null;
        selectedImageUri = null;

        // eventId tạm dùng cho seat layout
        eventIdForSeats = db.collection("events").document().getId();

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && isAdded()) {
                        selectedImageUri = uri;
                        if (ivPreview != null) ivPreview.setImageURI(uri);
                        Toast.makeText(requireContext(),
                                "Đã chọn ảnh cover",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle b) {
        return inflater.inflate(R.layout.activity_create_event, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        edtTitle         = v.findViewById(R.id.edtTitle);
        edtArtist        = v.findViewById(R.id.edtArtist);
        edtCategory      = v.findViewById(R.id.edtCategory);
        edtDescription   = v.findViewById(R.id.edtDescription);
        edtPlace         = v.findViewById(R.id.edtPlace);
        edtAddressDetail = v.findViewById(R.id.edtAddressDetail);
        edtTotalSeats    = v.findViewById(R.id.edtTotalSeats);

        tvPickedDateTime = v.findViewById(R.id.tvPickedDateTime);
        ivPreview        = v.findViewById(R.id.ivPreview);

        btnSave          = v.findViewById(R.id.btnSave);
        btnPickDateTime  = v.findViewById(R.id.btnPickDateTime);
        btnPickImage     = v.findViewById(R.id.btnPickImage);

        layoutTicketContainer = v.findViewById(R.id.layoutTicketContainer);
        btnAddTicketType      = v.findViewById(R.id.btnAddTicketType);

        if (btnPickDateTime != null) {
            btnPickDateTime.setOnClickListener(view -> showDateTimePicker());
        }
        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(view -> pickImageLauncher.launch("image/*"));
        }
        if (btnSave != null) {
            btnSave.setOnClickListener(view -> saveEvent());
        }
        if (btnAddTicketType != null) {
            btnAddTicketType.setOnClickListener(view -> addTicketRow());
        }
    }

    // ===== REFRESH GHẾ KHI QUAY LẠI =====
    @Override
    public void onResume() {
        super.onResume();
        for (TicketRow row : ticketRows) {
            String name = getText(row.edtName);
            if (name.isEmpty()) continue;

            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);
        }
    }

    private void showDateTimePicker() {
        final Calendar now = Calendar.getInstance();

        DatePickerDialog dateDialog = new DatePickerDialog(
                requireContext(),
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar pickedDate = Calendar.getInstance();
                    pickedDate.set(Calendar.YEAR, year);
                    pickedDate.set(Calendar.MONTH, month);
                    pickedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    pickedDate.set(Calendar.SECOND, 0);
                    pickedDate.set(Calendar.MILLISECOND, 0);

                    TimePickerDialog startDialog = new TimePickerDialog(
                            requireContext(),
                            (TimePicker startView, int startHour, int startMinute) -> {
                                Calendar startCal = (Calendar) pickedDate.clone();
                                startCal.set(Calendar.HOUR_OF_DAY, startHour);
                                startCal.set(Calendar.MINUTE, startMinute);
                                selectedStartDateTime = startCal;

                                int defaultEndHour = startHour + 1;
                                if (defaultEndHour >= 24) defaultEndHour = startHour;

                                TimePickerDialog endDialog = new TimePickerDialog(
                                        requireContext(),
                                        (TimePicker endView, int endHour, int endMinute) -> {
                                            Calendar endCal = (Calendar) pickedDate.clone();
                                            endCal.set(Calendar.HOUR_OF_DAY, endHour);
                                            endCal.set(Calendar.MINUTE, endMinute);

                                            if (endCal.before(startCal)) {
                                                Toast.makeText(
                                                        requireContext(),
                                                        "Giờ kết thúc phải sau giờ bắt đầu",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                                endCal = (Calendar) startCal.clone();
                                            }

                                            selectedEndDateTime = endCal;

                                            String text = String.format(
                                                    "%02d:%02d - %02d:%02d, %02d/%02d/%04d",
                                                    startCal.get(Calendar.HOUR_OF_DAY),
                                                    startCal.get(Calendar.MINUTE),
                                                    endCal.get(Calendar.HOUR_OF_DAY),
                                                    endCal.get(Calendar.MINUTE),
                                                    dayOfMonth, month + 1, year
                                            );
                                            tvPickedDateTime.setText(text);
                                        },
                                        defaultEndHour,
                                        startMinute,
                                        true
                                );
                                endDialog.setTitle("Chọn giờ kết thúc");
                                endDialog.show();

                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true
                    );
                    startDialog.setTitle("Chọn giờ bắt đầu");
                    startDialog.show();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );

        dateDialog.show();
    }

    // ===== DÒNG LOẠI VÉ =====

    private void addTicketRow() {
        addTicketRowWithData(null, null, null);
    }

    private void addTicketRowWithData(@Nullable String name,
                                      @Nullable Double price,
                                      @Nullable Long quota) {

        View rowView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_ticket_type_input, layoutTicketContainer, false);

        EditText edtName  = rowView.findViewById(R.id.edtTicketName);
        EditText edtPrice = rowView.findViewById(R.id.edtTicketPrice);
        EditText edtQuota = rowView.findViewById(R.id.edtTicketQuota);
        TextView tvSeatInfo = rowView.findViewById(R.id.tvSeatStatus);
        TextView btnRemove = rowView.findViewById(R.id.btnRemoveRow);
        com.google.android.material.button.MaterialButton btnSetupSeats =
                rowView.findViewById(R.id.btnSetupSeats);

        if (name != null)  edtName.setText(name);
        if (price != null) edtPrice.setText(String.valueOf(price.intValue()));
        if (quota != null) edtQuota.setText(String.valueOf(quota.intValue()));

        // ✅ set text mặc định
        tvSeatInfo.setVisibility(View.VISIBLE);
        tvSeatInfo.setText("Chưa chọn ghế");

        TicketRow row = new TicketRow(edtName, edtPrice, edtQuota, rowView, tvSeatInfo);
        ticketRows.add(row);

        // Nếu đang edit / có sẵn tên → load ghế tạm
        if (name != null) {
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
        }
        updateSeatInfoText(row);

        btnRemove.setOnClickListener(v -> {
            layoutTicketContainer.removeView(rowView);
            ticketRows.remove(row);

            // xóa luôn ghế tạm cho loại vé này
            String ticketName = getText(edtName);
            SeatLayoutConfigActivity.clearSeatsForTicket(eventIdForSeats, ticketName);
        });

        btnSetupSeats.setOnClickListener(v -> {
            String ticketName = getText(edtName);
            String sQuota     = getText(edtQuota);

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
                edtQuota.setError("Số vé không hợp lệ");
                return;
            }
            if (quotaVal <= 0) {
                edtQuota.setError("Số vé phải > 0");
                return;
            }

            int totalEventSeats = 0;
            String totalStr = getText(edtTotalSeats);
            try {
                totalEventSeats = Integer.parseInt(totalStr);
            } catch (NumberFormatException ignored) {}
            if (totalEventSeats <= 0) {
                totalEventSeats = quotaVal;
            }

            Intent i = new Intent(requireContext(), SeatLayoutConfigActivity.class);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_EVENT_ID, eventIdForSeats);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_TICKET_NAME, ticketName);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_MAX_SEATS, quotaVal);
            i.putExtra(SeatLayoutConfigActivity.EXTRA_TOTAL_EVENT_SEATS, totalEventSeats);
            startActivity(i);
        });

        layoutTicketContainer.addView(rowView);
    }

    // ===== MODEL TICKET ROW =====

    private static class TicketRow {
        EditText edtName, edtPrice, edtQuota;
        TextView tvSeatInfo;
        View root;
        HashSet<String> seatCodes = new HashSet<>();

        TicketRow(EditText name, EditText price, EditText quota,
                  View root, TextView tvSeatInfo) {
            this.edtName = name;
            this.edtPrice = price;
            this.edtQuota = quota;
            this.root = root;
            this.tvSeatInfo = tvSeatInfo;
        }
    }

    private void updateSeatInfoText(TicketRow row) {
        if (row.tvSeatInfo == null) return;
        row.tvSeatInfo.setVisibility(View.VISIBLE);

        int count = row.seatCodes != null ? row.seatCodes.size() : 0;
        if (count == 0) {
            row.tvSeatInfo.setText("Chưa chọn ghế");
        } else {
            row.tvSeatInfo.setText("Đã chọn " + count + " ghế");
        }
    }

    // ===== SAVE EVENT =====

    private void saveEvent() {
        String title    = getText(edtTitle);
        String artist   = getText(edtArtist);
        String category = getText(edtCategory);
        String place    = getText(edtPlace);
        String addrDtl  = getText(edtAddressDetail);
        String totalStr = getText(edtTotalSeats);
        String desc     = getText(edtDescription);

        if (TextUtils.isEmpty(title)) {
            edtTitle.setError("Nhập tên sự kiện");
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
        if (TextUtils.isEmpty(category)) {
            edtCategory.setError("Nhập thể loại");
            return;
        }
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
        if (selectedStartDateTime == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn thời gian bắt đầu",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedEndDateTime == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn thời gian kết thúc",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn ảnh cover cho sự kiện",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;
        HashSet<String> usedSeats = new HashSet<>();

        for (TicketRow row : ticketRows) {
            String name   = getText(row.edtName);
            String sPrice = getText(row.edtPrice);
            String sQuota = getText(row.edtQuota);

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

            double priceRow;
            int quota;
            try {
                priceRow = Double.parseDouble(sPrice);
                quota = Integer.parseInt(sQuota);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(),
                        "Giá / số vé không hợp lệ",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (quota <= 0) {
                row.edtQuota.setError("Số vé phải > 0");
                return;
            }

            // Lấy ghế đã vẽ tạm & cập nhật text
            List<String> seats = SeatLayoutConfigActivity.getSeatsForTicket(eventIdForSeats, name);
            row.seatCodes.clear();
            row.seatCodes.addAll(seats);
            updateSeatInfoText(row);

            if (!seats.isEmpty()) {
                // nếu có cấu hình sơ đồ ghế thì check chặt quota
                if (seats.size() != quota) {
                    Toast.makeText(requireContext(),
                            "Loại vé \"" + name + "\" phải chọn đúng " + quota +
                                    " ghế (hiện đang " + seats.size() + ")",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                for (String s : seats) {
                    if (!usedSeats.add(s)) {
                        Toast.makeText(requireContext(),
                                "Ghế " + s + " đang bị dùng trùng giữa các loại vé",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", priceRow);
            ticket.put("quota", quota);
            ticket.put("sold", 0);
            if (!seats.isEmpty()) {
                ticket.put("seats", seats);
            }

            ticketTypes.add(ticket);
            totalSeatsFromTickets += quota;
            if (priceRow > 0 && priceRow < minPriceFromTickets) {
                minPriceFromTickets = priceRow;
            }
        }

        int totalSeats;
        try {
            totalSeats = Integer.parseInt(totalStr);
        } catch (NumberFormatException e) {
            edtTotalSeats.setError("Tổng vé không hợp lệ");
            return;
        }
        if (totalSeats <= 0) {
            edtTotalSeats.setError("Tổng vé phải > 0");
            return;
        }

        double price;
        if (ticketTypes.isEmpty()) {
            price = 0d;
        } else {
            if (totalSeatsFromTickets > totalSeats) {
                Toast.makeText(requireContext(),
                        "Tổng số vé các loại (" + totalSeatsFromTickets +
                                ") lớn hơn số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (totalSeatsFromTickets != totalSeats) {
                Toast.makeText(requireContext(),
                        "Tổng số vé các loại phải bằng tổng số ghế (" + totalSeats + ")",
                        Toast.LENGTH_LONG).show();
                return;
            }
            price = (minPriceFromTickets == Double.MAX_VALUE) ? 0d : minPriceFromTickets;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Bạn cần đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp startTime = new Timestamp(selectedStartDateTime.getTime());
        Timestamp endTime   = new Timestamp(selectedEndDateTime.getTime());

        uploadImageThenSaveEvent(
                user.getUid(),
                title,
                artist,
                category,
                place,
                addrDtl,
                desc,
                totalSeats,
                price,
                startTime,
                endTime,
                ticketTypes
        );
    }

    // ==== upload & save, saveSeatsForEvent giữ nguyên như cũ ====

    private void uploadImageThenSaveEvent(
            String ownerId,
            String title,
            String artist,
            String category,
            String place,
            String addrDtl,
            String desc,
            int totalSeats,
            double price,
            Timestamp startTime,
            Timestamp endTime,
            List<Map<String, Object>> ticketTypes
    ) {
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(),
                    "Chưa chọn ảnh cover",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = "event_" + System.currentTimeMillis() + ".jpg";

        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("event_covers/" + ownerId + "/" + fileName);

        ref.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            thumbnailStr = downloadUri.toString();

                            Map<String, Object> data = new HashMap<>();
                            data.put("title", title);
                            data.put("location", place);
                            data.put("addressDetail", addrDtl);
                            data.put("startTime", startTime);
                            data.put("endTime", endTime);
                            data.put("thumbnail", thumbnailStr);
                            data.put("category", category);
                            data.put("price", price);
                            data.put("availableSeats", totalSeats);
                            data.put("totalSeats", totalSeats);
                            data.put("ownerId", ownerId);
                            data.put("status", "active");
                            data.put("createdAt", FieldValue.serverTimestamp());
                            data.put("updatedAt", FieldValue.serverTimestamp());

                            if (!TextUtils.isEmpty(artist)) {
                                data.put("artist", artist);
                            }
                            if (!TextUtils.isEmpty(desc)) {
                                data.put("description", desc);
                            }

                            db.collection("events")
                                    .document(eventIdForSeats)
                                    .set(data)
                                    .addOnSuccessListener(unused -> {
                                        if (!ticketTypes.isEmpty()) {
                                            saveTicketTypes(eventIdForSeats, ticketTypes);
                                            saveSeatsForEvent(eventIdForSeats, ticketTypes);
                                        }
                                        SeatLayoutConfigActivity.clearSeatsForEvent(eventIdForSeats);

                                        Toast.makeText(requireContext(),
                                                "Tạo sự kiện thành công",
                                                Toast.LENGTH_SHORT).show();
                                        NavHostFragment.findNavController(this).navigateUp();
                                    })

                                    .addOnFailureListener(e -> Toast.makeText(
                                            requireContext(),
                                            "Tạo sự kiện thất bại: " + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show());

                        })
                )
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(
                                requireContext(),
                                "Upload ảnh lỗi: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                    android.util.Log.e("CreateEvent", "Upload image error", e);
                });
    }

    private void saveTicketTypes(String eventId, List<Map<String, Object>> ticketTypes) {
        for (Map<String, Object> ticket : ticketTypes) {
            db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .add(ticket);
        }
    }

    private void saveSeatsForEvent(String eventId, List<Map<String, Object>> ticketTypes) {
        java.util.HashSet<String> assigned = new java.util.HashSet<>();

        for (Map<String, Object> ticket : ticketTypes) {
            String ticketName = (String) ticket.get("name");
            Object priceObj = ticket.get("price");
            long seatPrice = 0L;
            if (priceObj instanceof Number) {
                seatPrice = ((Number) priceObj).longValue();
            }

            @SuppressWarnings("unchecked")
            List<String> seats = (List<String>) ticket.get("seats");
            if (seats == null || seats.isEmpty()) continue;

            for (String label : seats) {
                if (label == null || label.trim().isEmpty()) continue;

                String trimmed = label.trim();
                assigned.add(trimmed);

                String row = trimmed.substring(0, 1);
                int number = 0;
                try {
                    number = Integer.parseInt(trimmed.substring(1));
                } catch (Exception ignored) {}

                Map<String, Object> seatDoc = new HashMap<>();
                seatDoc.put("row", row);
                seatDoc.put("number", number);
                seatDoc.put("type", ticketName);
                seatDoc.put("status", "available");
                seatDoc.put("price", seatPrice);

                db.collection("events")
                        .document(eventId)
                        .collection("seats")
                        .document(trimmed)
                        .set(seatDoc);
            }
        }

        int rows = SeatLayoutConfigActivity.ROWS;
        int cols = SeatLayoutConfigActivity.COLS;

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                String label = rowChar + String.valueOf(c);
                if (assigned.contains(label)) continue;

                Map<String, Object> seatDoc = new HashMap<>();
                seatDoc.put("row", String.valueOf(rowChar));
                seatDoc.put("number", c);
                seatDoc.put("type", null);
                seatDoc.put("status", "blocked");
                seatDoc.put("price", 0L);

                db.collection("events")
                        .document(eventId)
                        .collection("seats")
                        .document(label)
                        .set(seatDoc);
            }
        }
    }

    private String getText(EditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
