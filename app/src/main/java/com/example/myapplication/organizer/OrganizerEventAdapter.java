package com.example.myapplication.organizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.firebase.Timestamp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerEventAdapter extends RecyclerView.Adapter<OrganizerEventAdapter.VH> {

    public interface OnItemClick {
        void onClick(@NonNull Event e);
    }

    private final List<Event> data = new ArrayList<>();
    private final OnItemClick onItemClick;

    public OrganizerEventAdapter(OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
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
        h.bind(e, onItemClick);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvTime, tvVenue, tvTicketInfo;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvVenue = itemView.findViewById(R.id.tvVenue);
            tvTicketInfo = itemView.findViewById(R.id.tvTicketInfo);
        }

        void bind(Event e, OnItemClick onItemClick) {
            tvTitle.setText(e.getTitle() == null ? "(Không tên)" : e.getTitle());
            tvVenue.setText(e.getLocation() == null ? "Chưa cập nhật địa điểm" : e.getLocation());

            // Thời gian
            String timeText = "Chưa đặt thời gian";
            Timestamp ts = e.getStartTime();
            if (ts != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault());
                timeText = sdf.format(ts.toDate());
            }
            tvTime.setText(timeText);

            // Vé & giá
            Integer total = e.getTotalSeats();
            Integer avail = e.getAvailableSeats();
            Double price = e.getPrice();

            String ticketInfo = "Vé: ";
            if (total != null && avail != null) {
                int sold = total - avail;
                ticketInfo += sold + "/" + total;
            } else {
                ticketInfo += "Chưa cấu hình";
            }

            if (price != null && price > 0) {
                String priceStr = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + "₫";
                ticketInfo += " | Giá: " + priceStr;
            } else {
                ticketInfo += " | Miễn phí";
            }

            tvTicketInfo.setText(ticketInfo);

            itemView.setOnClickListener(v -> {
                if (onItemClick != null) onItemClick.onClick(e);
            });
        }
    }
}
