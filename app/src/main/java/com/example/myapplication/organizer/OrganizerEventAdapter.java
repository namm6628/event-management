package com.example.myapplication.organizer;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerEventAdapter extends RecyclerView.Adapter<OrganizerEventAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Event e);
        void onViewAttendees(@NonNull Event e);
    }

    private final List<Event> data = new ArrayList<>();
    private final Listener listener;

    public OrganizerEventAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Event> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_event, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Event e = data.get(position);
        h.bind(e, listener);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvTime, tvVenue, tvTicketInfo, tvTicketTypesDetail;
        final Button btnEdit, btnViewAttendees;
        private final FirebaseFirestore db = FirebaseFirestore.getInstance();

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvVenue = itemView.findViewById(R.id.tvVenue);
            tvTicketInfo = itemView.findViewById(R.id.tvTicketInfo);
            tvTicketTypesDetail = itemView.findViewById(R.id.tvTicketTypesDetail); // NEW
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnViewAttendees = itemView.findViewById(R.id.btnViewAttendees);
        }

        void bind(Event e, Listener listener) {

            // ===== TITLE =====
            tvTitle.setText(e.getTitle() == null ? "(Không tên)" : e.getTitle());

            // ===== LOCATION =====
            tvVenue.setText(
                    e.getLocation() == null
                            ? "Chưa cập nhật địa điểm"
                            : e.getLocation()
            );

            // ===== TIME =====
            String timeText = "Chưa đặt thời gian";
            Timestamp ts = e.getStartTime();
            if (ts != null) {
                try {
                    timeText = DateFormat.format(
                            "dd/MM/yyyy • HH:mm",
                            ts.toDate()
                    ).toString();
                } catch (Exception ignore) {}
            }
            tvTime.setText(timeText);



            // ===== BASIC TICKET INFO =====
            Integer total = e.getTotalSeats();
            Integer avail = e.getAvailableSeats();
            Double price = e.getPrice();

            int sold = (total != null && avail != null) ? (total - avail) : 0;

            String priceStr = (price != null && price > 0)
                    ? NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + "₫"
                    : "Miễn phí";

            String status = e.getStatus() == null ? "active" : e.getStatus();
            String statusLabel;
            long now = System.currentTimeMillis();
            long eventTime = ts != null ? ts.toDate().getTime() : 0;

// ===== STATUS LOGIC =====
            if (sold >= (total != null ? total : 0)) {
                statusLabel = "Hết vé";
            } else if (eventTime < now) {
                statusLabel = "Sự kiện đã kết thúc";
            } else {
                statusLabel = "Đang mở bán";
            }

            tvTicketInfo.setText(
                    "Tổng số vé: " + sold + "/" + (total != null ? total : 0)

                            + " | " + statusLabel
            );

            // ===== LOAD TICKET TYPES SUBCOLLECTION =====
            loadTicketTypes(e, tvTicketTypesDetail);

            // ===== BUTTONS =====
            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(e);
            });

            btnViewAttendees.setOnClickListener(v -> {
                if (listener != null) listener.onViewAttendees(e);
            });

            // card click = edit
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(e);
            });
        }

        private void loadTicketTypes(Event event, TextView tv) {
            String eventId = event.getId();
            if (eventId == null) {
                tv.setText("");
                return;
            }

            tv.setText("Đang tải loại vé...");

            db.collection("events")
                    .document(eventId)
                    .collection("ticketTypes")
                    .get()
                    .addOnSuccessListener(snap -> {

                        if (snap.isEmpty()) {
                            tv.setText(""); // Không có loại vé
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String name = d.getString("name");
                            Double price = d.getDouble("price");
                            Long quota = d.getLong("quota");
                            Long sold = d.getLong("sold");

                            if (sb.length() > 0) sb.append("\n");

                            sb.append("Loại vé: ")
                                    .append(name == null ? "Không tên" : name)
                                    .append(" | Giá: ");
                            if (price == null || price == 0d)
                                sb.append("Miễn phí");
                            else
                                sb.append(nf.format(price)).append("đ");

                            long q = quota == null ? 0 : quota;
                            long s = sold == null ? 0 : sold;

                            sb.append(" | Đã bán: ").append(s).append("/").append(q)
                                    .append(" | ");

                            if (s >= q)
                                sb.append("Hết vé");
                            else
                                sb.append("Đang mở bán");
                        }

                        tv.setText(sb.toString());
                    })
                    .addOnFailureListener(e -> tv.setText("Không tải được loại vé"));
        }
    }
}
