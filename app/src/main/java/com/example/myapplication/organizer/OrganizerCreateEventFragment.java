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

    // ====== INPUT CHÍNH ======
    private EditText edtTitle, edtArtist, edtCategory,
            edtPlace, edtAddressDetail,
            edtDescription, edtTotalSeats;

    private TextView tvPickedDateTime;
    private ImageView ivPreview;
    private View btnSave, btnPickDateTime, btnPickImage;

    private FirebaseFirestore db;

    // DateTime
    private Calendar selectedStartDateTime;
    private Calendar selectedEndDateTime;

    // Image
    private ActivityResultLauncher<String> pickImageLauncher;
    private Uri selectedImageUri;     // chỉ lưu lại để upload khi bấm Tạo sự kiện
    private String thumbnailStr;      // URL public sau khi upload lên Storage

    // Ticket types
    private View btnAddTicketType;
    private ViewGroup layoutTicketContainer;
    private final List<TicketRow> ticketRows = new ArrayList<>();

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
        selectedStartDateTime = null;
        selectedEndDateTime   = null;
        thumbnailStr = null;
        selectedImageUri = null;

        // Đăng ký picker ảnh – CHỈ preview, chưa upload
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && isAdded()) {
                        selectedImageUri = uri;
                        if (ivPreview != null) {
                            ivPreview.setImageURI(uri);
                        }
                        Toast.makeText(
                                requireContext(),
                                "Đã chọn ảnh cover",
                                Toast.LENGTH_SHORT
                        ).show();
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

        // ÁNH XẠ VIEW
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

        // ====== BUTTONS ======
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
        // ❌ KHÔNG auto add dòng loại vé – chỉ hiện khi user bấm "Thêm loại vé"
    }

    // ====== PICK DATE TIME (CÓ GIỜ KẾT THÚC) ======
    private void showDateTimePicker() {
        final Calendar now = Calendar.getInstance();

        DatePickerDialog dateDialog = new DatePickerDialog(
                requireContext(),
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    // Ngày chung cho cả start & end
                    Calendar pickedDate = Calendar.getInstance();
                    pickedDate.set(Calendar.YEAR, year);
                    pickedDate.set(Calendar.MONTH, month);
                    pickedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    pickedDate.set(Calendar.SECOND, 0);
                    pickedDate.set(Calendar.MILLISECOND, 0);

                    // Chọn giờ bắt đầu
                    TimePickerDialog startDialog = new TimePickerDialog(
                            requireContext(),
                            (TimePicker startView, int startHour, int startMinute) -> {
                                Calendar startCal = (Calendar) pickedDate.clone();
                                startCal.set(Calendar.HOUR_OF_DAY, startHour);
                                startCal.set(Calendar.MINUTE, startMinute);
                                selectedStartDateTime = startCal;

                                // Sau khi chọn giờ bắt đầu → chọn giờ kết thúc
                                int defaultEndHour = startHour + 1;
                                if (defaultEndHour >= 24) defaultEndHour = startHour;

                                TimePickerDialog endDialog = new TimePickerDialog(
                                        requireContext(),
                                        (TimePicker endView, int endHour, int endMinute) -> {
                                            Calendar endCal = (Calendar) pickedDate.clone();
                                            endCal.set(Calendar.HOUR_OF_DAY, endHour);
                                            endCal.set(Calendar.MINUTE, endMinute);

                                            // Giờ kết thúc phải sau hoặc bằng giờ bắt đầu
                                            if (endCal.before(startCal)) {
                                                Toast.makeText(
                                                        requireContext(),
                                                        "Giờ kết thúc phải sau giờ bắt đầu",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                                // Nếu sai thì set = start
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

    // ====== TICKET ROW UI ======
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

    // ====== SAVE EVENT ======
    private void saveEvent() {
        String title    = getText(edtTitle);
        String artist   = getText(edtArtist);
        String category = getText(edtCategory);
        String place    = getText(edtPlace);
        String addrDtl  = getText(edtAddressDetail);
        String totalStr = getText(edtTotalSeats);
        String desc     = getText(edtDescription);

        // --- Validate cơ bản ---
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

        // Thời gian
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

        // Ảnh cover – chỉ check đã chọn, chưa upload
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(),
                    "Vui lòng chọn ảnh cover cho sự kiện",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // ====== LẤY DANH SÁCH LOẠI VÉ ======
        List<Map<String, Object>> ticketTypes = new ArrayList<>();
        int totalSeatsFromTickets = 0;
        double minPriceFromTickets = Double.MAX_VALUE;

        for (TicketRow row : ticketRows) {
            String name   = getText(row.edtName);
            String sPrice = getText(row.edtPrice);
            String sQuota = getText(row.edtQuota);

            // Cả 3 đều trống → bỏ qua dòng
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

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("name", name);
            ticket.put("price", priceRow);
            ticket.put("quota", quota);
            ticket.put("sold", 0);

            ticketTypes.add(ticket);

            totalSeatsFromTickets += quota;
            if (priceRow > 0 && priceRow < minPriceFromTickets) {
                minPriceFromTickets = priceRow;
            }
        }

        double price;
        int totalSeats;

        // Tổng số ghế
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

        // Nếu KHÔNG có loại vé → sự kiện free
        if (ticketTypes.isEmpty()) {
            price = 0d;
        } else {
            // Kiểm tra quota tổng = totalSeats
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

        // Lấy user hiện tại
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "Bạn cần đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp startTime = new Timestamp(selectedStartDateTime.getTime());
        Timestamp endTime   = new Timestamp(selectedEndDateTime.getTime());

        // Upload ảnh xong mới lưu event
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
                                    .add(data)
                                    .addOnSuccessListener(refEvent -> {
                                        if (!ticketTypes.isEmpty()) {
                                            saveTicketTypes(refEvent.getId(), ticketTypes);
                                        }
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

    private String getText(EditText e) {
        return e == null || e.getText() == null
                ? ""
                : e.getText().toString().trim();
    }
}
