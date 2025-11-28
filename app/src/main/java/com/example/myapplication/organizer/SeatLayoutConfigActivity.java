package com.example.myapplication.organizer;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SeatLayoutConfigActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID           = "eventId";
    public static final String EXTRA_TICKET_NAME        = "ticketTypeName";
    public static final String EXTRA_MAX_SEATS          = "maxSeats";
    public static final String EXTRA_TEMPLATE_ID        = "templateId";
    public static final String EXTRA_TOTAL_EVENT_SEATS  = "totalEventSeats";

    public static final int ROWS = 8;   // hoặc giá trị bạn đang dùng
    public static final int COLS = 12;


    // Lưu sơ đồ ghế tạm cho mọi loại vé (trong RAM, chưa lên Firestore)
    // key = eventId + "|" + ticketTypeName
    private static final Map<String, List<String>> TEMP_SEATS = new HashMap<>();

    private static String buildKey(String eventId, String ticketName) {
        return eventId + "|" + ticketName;
    }

    /** Lấy danh sách ghế đã chọn cho 1 loại vé */
    public static List<String> getSeatsForTicket(String eventId, String ticketName) {
        String key = buildKey(eventId, ticketName);
        List<String> list = TEMP_SEATS.get(key);
        if (list == null) return new ArrayList<>();
        return new ArrayList<>(list); // clone ra để bên ngoài không sửa trực tiếp
    }

    /** Xoá toàn bộ ghế tạm cho 1 event (sau khi tạo xong event) */
    public static void clearSeatsForEvent(String eventId) {
        List<String> removeKeys = new ArrayList<>();
        for (String key : TEMP_SEATS.keySet()) {
            if (key.startsWith(eventId + "|")) {
                removeKeys.add(key);
            }
        }
        for (String k : removeKeys) {
            TEMP_SEATS.remove(k);
        }
    }

    /** Xoá ghế của 1 loại vé khi bị xoá dòng ở màn tạo sự kiện */
    public static void clearSeatsForTicket(String eventId, String ticketName) {
        if (eventId == null || ticketName == null) return;
        String key = buildKey(eventId, ticketName);
        TEMP_SEATS.remove(key);
    }

    private RecyclerView recyclerView;
    private MaterialButton btnSaveSeats;

    private String eventId;
    private String ticketName;
    private int maxSeats;

    private SeatTemplate template;
    private int rows;
    private int cols;

    private final List<SeatItem> seatItems = new ArrayList<>();
    private SeatConfigAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_layout_config);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rvSeatGrid);
        btnSaveSeats = findViewById(R.id.btnSaveSeatLayout);

        eventId    = getIntent().getStringExtra(EXTRA_EVENT_ID);
        ticketName = getIntent().getStringExtra(EXTRA_TICKET_NAME);
        maxSeats   = getIntent().getIntExtra(EXTRA_MAX_SEATS, 0);

        if (eventId == null) eventId = "";
        if (ticketName == null) ticketName = "";

        int totalEventSeats = getIntent().getIntExtra(EXTRA_TOTAL_EVENT_SEATS, 0);
        String templateId   = getIntent().getStringExtra(EXTRA_TEMPLATE_ID);

        // Chọn template phù hợp
        template = null;
        if (templateId != null) {
            template = SeatTemplateStore.getTemplate(templateId);
        }
        if (template == null) {
            template = SeatTemplateStore.pickTemplateForCapacity(totalEventSeats);
        }
        if (template == null) {
            template = SeatTemplateStore.getDefaultTemplate();
        }

        rows = template.getRows();
        cols = template.getCols();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(template.getName());
        }

        // Ghế đã chọn trước đó của loại vé này
        List<String> preSelected = getSeatsForTicket(eventId, ticketName);

        // Ghế đã dùng bởi các loại vé khác của cùng event → khoá
        Set<String> lockedByOthers = new HashSet<>();
        String myKey = buildKey(eventId, ticketName);
        for (Map.Entry<String, List<String>> entry : TEMP_SEATS.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(eventId + "|") && !key.equals(myKey)) {
                lockedByOthers.addAll(entry.getValue());
            }
        }

        buildSeatGrid(preSelected, lockedByOthers);

        adapter = new SeatConfigAdapter(seatItems, this::onSeatClicked);
        recyclerView.setLayoutManager(new GridLayoutManager(this, cols));
        recyclerView.setAdapter(adapter);

        btnSaveSeats.setOnClickListener(v -> onSaveClicked());
    }

    private void buildSeatGrid(List<String> preSelected, Set<String> lockedByOthers) {
        seatItems.clear();

        Set<String> activeSeats = template.getActiveSeats();

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                String label = String.format(Locale.getDefault(), "%c%d", rowChar, c);

                boolean isSeat = activeSeats.contains(label);
                boolean selected = isSeat && preSelected.contains(label);
                boolean locked = isSeat && lockedByOthers.contains(label);
                String zone = isSeat ? template.getZoneForSeat(label) : null;

                SeatItem item = new SeatItem(label, selected, locked, !isSeat, zone);
                seatItems.add(item);
            }
        }
    }

    private void onSeatClicked(SeatItem seat) {
        if (seat.isPlaceholder) {
            return; // Ô trống (lối đi)
        }

        if (seat.locked) {
            Toast.makeText(this,
                    "Ghế này đã được gán cho loại vé khác",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Đếm số ghế đang bật
        int currentSelected = 0;
        for (SeatItem s : seatItems) {
            if (!s.isPlaceholder && s.selected) currentSelected++;
        }

        if (!seat.selected) {
            // bật thêm ghế → check quota
            if (maxSeats > 0 && currentSelected >= maxSeats) {
                Toast.makeText(
                        this,
                        "Loại vé \"" + ticketName + "\" chỉ được chọn tối đa " + maxSeats + " ghế",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            seat.selected = true;
        } else {
            // tắt ghế
            seat.selected = false;
        }

        adapter.notifyItemChanged(seat.position);
    }

    private void onSaveClicked() {
        List<String> selected = new ArrayList<>();
        for (SeatItem s : seatItems) {
            if (!s.isPlaceholder && s.selected) selected.add(s.label);
        }

        if (maxSeats > 0) {
            if (selected.size() != maxSeats) {
                Toast.makeText(
                        this,
                        "Loại vé \"" + ticketName + "\" phải chọn đúng "
                                + maxSeats + " ghế (hiện đang " + selected.size() + ")",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        } else if (selected.isEmpty()) {
            Toast.makeText(this,
                    "Chưa chọn ghế nào cho loại vé \"" + ticketName + "\"",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String key = buildKey(eventId, ticketName);
        TEMP_SEATS.put(key, selected);

        Toast.makeText(this,
                "Đã lưu " + selected.size() + " ghế cho loại vé \"" + ticketName + "\"",
                Toast.LENGTH_SHORT).show();

        setResult(RESULT_OK);
        finish();
    }

    /* ===== MODEL & ADAPTER ===== */

    private static class SeatItem {
        String label;
        boolean selected;
        boolean locked;
        boolean isPlaceholder; // true = ô trống (lối đi)
        String zone;           // hiện tại chưa dùng để đổi màu
        int position;

        SeatItem(String label, boolean selected, boolean locked,
                 boolean isPlaceholder, String zone) {
            this.label = label;
            this.selected = selected;
            this.locked = locked;
            this.isPlaceholder = isPlaceholder;
            this.zone = zone;
        }
    }

    interface OnSeatClickListener {
        void onSeatClick(SeatItem seat);
    }

    private static class SeatConfigAdapter
            extends RecyclerView.Adapter<SeatConfigAdapter.SeatViewHolder> {

        private final List<SeatItem> data;
        private final OnSeatClickListener listener;

        SeatConfigAdapter(List<SeatItem> data, OnSeatClickListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public SeatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_seat_config, parent, false);
            return new SeatViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SeatViewHolder holder, int position) {
            SeatItem seat = data.get(position);
            seat.position = position;
            holder.bind(seat, listener);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class SeatViewHolder extends RecyclerView.ViewHolder {
            MaterialButton btnSeat;
            SeatViewHolder(@NonNull View itemView) {
                super(itemView);
                btnSeat = itemView.findViewById(R.id.btnSeat);
            }

            void bind(SeatItem seat, OnSeatClickListener listener) {

                if (seat.isPlaceholder) {
                    // Ô trống (lối đi): ẩn nút nhưng vẫn giữ chỗ → tạo hành lang rõ
                    btnSeat.setText("");
                    btnSeat.setEnabled(false);
                    btnSeat.setClickable(false);
                    btnSeat.setVisibility(View.INVISIBLE);
                    return;
                }

                btnSeat.setVisibility(View.VISIBLE);
                btnSeat.setText(seat.label);  // luôn là A1, A2,...

                int colorRes;
                if (seat.locked) {
                    // ghế đã gán cho loại vé khác
                    colorRes = R.color.seat_booked;
                    btnSeat.setEnabled(false);
                } else if (seat.selected) {
                    // ghế đang chọn cho loại vé hiện tại
                    colorRes = R.color.seat_selected;
                    btnSeat.setEnabled(true);
                } else {
                    // ghế trống bình thường
                    colorRes = R.color.seat_available;
                    btnSeat.setEnabled(true);
                }

                btnSeat.setBackgroundTintList(
                        androidx.core.content.ContextCompat.getColorStateList(
                                itemView.getContext(), colorRes
                        )
                );

                if (!seat.locked) {
                    btnSeat.setOnClickListener(v -> listener.onSeatClick(seat));
                } else {
                    btnSeat.setOnClickListener(null);
                }
            }
        }
    }

}
