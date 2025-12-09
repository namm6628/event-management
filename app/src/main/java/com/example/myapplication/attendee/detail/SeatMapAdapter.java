package com.example.myapplication.attendee.detail;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Seat;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SeatMapAdapter extends RecyclerView.Adapter<SeatMapAdapter.SeatVH> {

    public interface OnSelectionChange {
        void onSelectionChange(List<Seat> selectedSeats);
    }

    private final int maxSeats;
    private final OnSelectionChange selectionListener;

    private final List<Seat> data = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();

    public SeatMapAdapter(int maxSeats, OnSelectionChange listener) {
        this.maxSeats = maxSeats;
        this.selectionListener = listener;
    }

    public void setSeatList(List<Seat> seats) {
        data.clear();
        selectedIds.clear();

        if (seats != null) {
            for (Seat s : seats) {
                if (s.getStatus() == null || s.getStatus().trim().isEmpty()) {
                    s.setStatus("available");
                }
                data.add(s);
            }
        }

        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<Seat> getSelectedSeats() {
        List<Seat> res = new ArrayList<>();
        for (Seat s : data) {
            if (selectedIds.contains(s.getId())) {
                res.add(s);
            }
        }
        return res;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChange(getSelectedSeats());
        }
    }

    @NonNull
    @Override
    public SeatVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
        View v = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_seat_config, parent, false);
        return new SeatVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SeatVH holder, int position) {
        Seat seat = data.get(position);
        holder.bind(seat, selectedIds.contains(seat.getId()));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class SeatVH extends RecyclerView.ViewHolder {
        MaterialButton btnSeat;

        SeatVH(@NonNull View itemView) {
            super(itemView);
            btnSeat = itemView.findViewById(R.id.btnSeat);
        }

        void bind(Seat seat, boolean isSelected) {
            final String label = seat.getLabel();
            btnSeat.setText(label);

            String status = seat.getStatus() == null ? "available" : seat.getStatus();

            if ("booked".equalsIgnoreCase(status)
                    || "blocked".equalsIgnoreCase(status)
                    || "hold".equalsIgnoreCase(status)) {
                btnSeat.setEnabled(false);
                btnSeat.setBackgroundTintList(
                        androidx.core.content.ContextCompat.getColorStateList(
                                itemView.getContext(),
                                R.color.seat_booked
                        )
                );
                btnSeat.setOnClickListener(null);
                return;
            }

            btnSeat.setEnabled(true);
            btnSeat.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(
                            itemView.getContext(),
                            isSelected ? R.color.seat_selected : R.color.seat_available
                    )
            );

            btnSeat.setOnClickListener(v -> {
                boolean currentlySelected = selectedIds.contains(seat.getId());

                if (!currentlySelected) {
                    if (selectedIds.size() >= maxSeats) {
                        Toast.makeText(
                                itemView.getContext(),
                                String.format(Locale.getDefault(),
                                        "Bạn chỉ được chọn tối đa %d ghế", maxSeats),
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }
                    selectedIds.add(seat.getId());
                } else {
                    selectedIds.remove(seat.getId());
                }

                notifyItemChanged(getBindingAdapterPosition());
                notifySelectionChanged();
            });
        }
    }
}
