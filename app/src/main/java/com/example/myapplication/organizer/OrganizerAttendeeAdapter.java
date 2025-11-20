package com.example.myapplication.organizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Order;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerAttendeeAdapter extends RecyclerView.Adapter<OrganizerAttendeeAdapter.VH> {

    private final List<Order> data = new ArrayList<>();

    public void submit(List<Order> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendee_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = data.get(position);

        h.tvUserId.setText(o.getUserId());
        h.tvQuantity.setText("Số vé: " + o.getQuantity());

        String timeText = "";
        if (o.getCreatedAt() != null) {
            DateFormat df = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT,
                    Locale.getDefault()
            );
            timeText = df.format(o.getCreatedAt().toDate());
        }
        h.tvTime.setText(timeText);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvUserId, tvQuantity, tvTime;
        VH(@NonNull View itemView) {
            super(itemView);
            tvUserId = itemView.findViewById(R.id.tvUserId);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
