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

    public static final String EXTRA_EVENT_ID          = "eventId";
    public static final String EXTRA_TICKET_NAME       = "ticketTypeName";
    public static final String EXTRA_MAX_SEATS         = "maxSeats";
    public static final String EXTRA_TEMPLATE_ID       = "templateId";
    public static final String EXTRA_TOTAL_EVENT_SEATS = "totalEventSeats";

    // ROWS / COLS: tham chiếu, thực tế dùng rows/cols từ template
    public static final int ROWS = 8;
    public static final int COLS = 12;

    // GHẾ TẠM TRONG RAM
    private static final Map<String, List<String>> TEMP_SEATS = new HashMap<>();

    private static String buildKey(String eventId, String ticketName) {
        return eventId + "|" + ticketName;
    }

    public static List<String> getSeatsForTicket(String eventId, String ticketName) {
        String key = buildKey(eventId, ticketName);
        List<String> list = TEMP_SEATS.get(key);
        if (list == null) return new ArrayList<>();
        return new ArrayList<>(list);
    }

    // ✅ thêm setter để màn Edit / Create có thể đẩy ghế cũ vào cache
    public static void setSeatsForTicket(String eventId, String ticketName, List<String> seats) {
        if (eventId == null || ticketName == null) return;
        String key = buildKey(eventId, ticketName);
        TEMP_SEATS.put(key, new ArrayList<>(seats));
    }

    public static void clearSeatsForEvent(String eventId) {
        List<String> removeKeys = new ArrayList<>();
        for (String key : TEMP_SEATS.keySet()) {
            if (key.startsWith(eventId + "|")) {
                removeKeys.add(key);
            }
        }
        for (String k : removeKeys) TEMP_SEATS.remove(k);
    }

    public static void clearSeatsForTicket(String eventId, String ticketName) {
        if (eventId == null || ticketName == null) return;
        TEMP_SEATS.remove(buildKey(eventId, ticketName));
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

        // ===== CHỌN TEMPLATE =====
        template = null;

        // 1. ƯU TIÊN templateId được gửi từ Create/Edit
        if (templateId != null) {
            template = SeatTemplateStore.getTemplate(templateId);
        }

        // 2. Nếu templateId null hoặc không tìm thấy → fallback chọn theo capacity
        if (template == null) {
            if (totalEventSeats <= 0) {
                // nếu không truyền tổng số vé, fallback thêm sang quota của loại vé
                totalEventSeats = maxSeats;
            }
            template = SeatTemplateStore.pickTemplateForCapacity(totalEventSeats);
        }

        // 3. Nếu vẫn null → dùng default
        if (template == null) {
            template = SeatTemplateStore.getDefaultTemplate();
        }

        rows = template.getRows();
        cols = template.getCols();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(template.getName());
        }

        // ghế đã chọn
        List<String> preSelected = getSeatsForTicket(eventId, ticketName);

        // ghế bị khoá do loại vé khác
        Set<String> lockedByOthers = new HashSet<>();
        String myKey = buildKey(eventId, ticketName);
        for (Map.Entry<String, List<String>> entry : TEMP_SEATS.entrySet()) {
            if (!entry.getKey().equals(myKey) && entry.getKey().startsWith(eventId + "|")) {
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

        int index = 0;
        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {

                String label = String.format(Locale.getDefault(), "%c%d", rowChar, c);
                boolean isSeat = activeSeats.contains(label);

                boolean selected = isSeat && preSelected.contains(label);
                boolean locked   = isSeat && lockedByOthers.contains(label);
                String zone      = isSeat ? template.getZoneForSeat(label) : null;

                SeatItem item = new SeatItem(label, selected, locked, !isSeat, zone);
                item.position = index++;

                seatItems.add(item);
            }
        }
    }

    private void onSeatClicked(SeatItem seat) {
        if (seat.isPlaceholder) return;

        if (seat.locked) {
            Toast.makeText(this, "Ghế này đã được chọn bởi loại vé khác", Toast.LENGTH_SHORT).show();
            return;
        }

        // đếm số ghế đang chọn
        int selectedCount = 0;
        for (SeatItem s : seatItems) {
            if (!s.isPlaceholder && s.selected) selectedCount++;
        }

        if (!seat.selected) {
            if (maxSeats > 0 && selectedCount >= maxSeats) {
                Toast.makeText(
                        this,
                        "Loại vé \"" + ticketName + "\" chỉ được chọn tối đa " + maxSeats + " ghế",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            seat.selected = true;
        } else {
            seat.selected = false;
        }

        adapter.notifyItemChanged(seat.position);
    }

    private void onSaveClicked() {
        List<String> selected = new ArrayList<>();
        for (SeatItem s : seatItems) {
            if (!s.isPlaceholder && s.selected) selected.add(s.label);
        }

        if (maxSeats > 0 && selected.size() != maxSeats) {
            Toast.makeText(
                    this,
                    "Loại vé \"" + ticketName + "\" phải chọn đúng "
                            + maxSeats + " ghế (hiện " + selected.size() + ")",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // Lưu tạm
        TEMP_SEATS.put(buildKey(eventId, ticketName), selected);

        Toast.makeText(this,
                "Đã lưu " + selected.size() + " ghế cho \"" + ticketName + "\"",
                Toast.LENGTH_SHORT).show();

        setResult(RESULT_OK);
        finish();
    }

    // ===== MODEL =====

    private static class SeatItem {
        String label;
        boolean selected;
        boolean locked;
        boolean isPlaceholder;
        String zone;
        int position;

        SeatItem(String label, boolean selected, boolean locked, boolean isPlaceholder, String zone) {
            this.label = label;
            this.selected = selected;
            this.locked = locked;
            this.isPlaceholder = isPlaceholder;
            this.zone = zone;
        }
    }

    // ===== ADAPTER =====

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
                    btnSeat.setText("");
                    btnSeat.setEnabled(false);
                    btnSeat.setVisibility(View.INVISIBLE);
                    return;
                }

                btnSeat.setVisibility(View.VISIBLE);
                btnSeat.setText(seat.label);

                int colorRes;
                if (seat.locked) {
                    colorRes = R.color.seat_booked;
                    btnSeat.setEnabled(false);
                } else if (seat.selected) {
                    colorRes = R.color.seat_selected;
                    btnSeat.setEnabled(true);
                } else {
                    colorRes = R.color.seat_available;
                    btnSeat.setEnabled(true);
                }

                btnSeat.setBackgroundTintList(
                        androidx.core.content.ContextCompat
                                .getColorStateList(itemView.getContext(), colorRes)
                );

                if (!seat.locked) {
                    btnSeat.setOnClickListener(v -> listener.onSeatClick(seat));
                }
            }
        }
    }
}
