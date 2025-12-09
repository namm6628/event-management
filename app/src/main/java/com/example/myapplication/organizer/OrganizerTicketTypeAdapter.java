package com.example.myapplication.organizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.TicketType;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerTicketTypeAdapter
        extends RecyclerView.Adapter<OrganizerTicketTypeAdapter.VH> {

    public interface OnSetupSeatsClickListener {
        void onSetupSeats(@NonNull TicketType ticketType, int position);
    }

    private final List<TicketType> data = new ArrayList<>();
    private final OnSetupSeatsClickListener listener;

    public OrganizerTicketTypeAdapter(OnSetupSeatsClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<TicketType> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void submitFromSnapshot(QuerySnapshot snap) {
        data.clear();
        if (snap != null) {
            for (DocumentSnapshot d : snap.getDocuments()) {
                TicketType t = d.toObject(TicketType.class);
                if (t == null) continue;
                // đảm bảo set id document
                t.setId(d.getId());
                data.add(t);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ticket_type_manage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(data.get(position), listener, position);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvSeatStatus;
        View btnSetupSeats;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTicketName);
            tvPrice = itemView.findViewById(R.id.tvTicketPrice);

            tvSeatStatus = itemView.findViewById(R.id.tvTicketQuota);

            btnSetupSeats = itemView.findViewById(R.id.btnSetupSeats);
        }

        void bind(TicketType t, OnSetupSeatsClickListener listener, int position) {
            String name = t.getName();
            tvName.setText(name != null ? name : "Loại vé");

            double price = t.getPrice();
            String priceStr = (price == 0d)
                    ? "Miễn phí"
                    : NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                    .format(price) + " ₫";
            tvPrice.setText(priceStr);

            if (t.getSelectedSeatIds() == null || t.getSelectedSeatIds().isEmpty()) {
                tvSeatStatus.setText("Chưa chọn ghế");
            } else {
                tvSeatStatus.setText("Đã chọn " + t.getSelectedSeatIds().size() + " ghế");
            }

            btnSetupSeats.setOnClickListener(v -> {
                if (listener != null) listener.onSetupSeats(t, position);
            });
        }
    }
}
