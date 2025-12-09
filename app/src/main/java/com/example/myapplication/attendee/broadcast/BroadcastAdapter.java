package com.example.myapplication.attendee.broadcast;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.EventBroadcast;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BroadcastAdapter extends ListAdapter<EventBroadcast, BroadcastAdapter.VH> {

    public interface Listener {
        void onDismiss(@NonNull EventBroadcast b);
    }

    private Listener listener;

    public BroadcastAdapter() {
        super(DIFF);
    }

    public BroadcastAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<EventBroadcast> DIFF =
            new DiffUtil.ItemCallback<EventBroadcast>() {
                @Override
                public boolean areItemsTheSame(@NonNull EventBroadcast oldItem,
                                               @NonNull EventBroadcast newItem) {
                    String oi = oldItem.getId();
                    String ni = newItem.getId();
                    return oi != null && oi.equals(ni);
                }

                @Override
                public boolean areContentsTheSame(@NonNull EventBroadcast oldItem,
                                                  @NonNull EventBroadcast newItem) {
                    return safeEquals(oldItem.getTitle(), newItem.getTitle())
                            && safeEquals(oldItem.getMessage(), newItem.getMessage())
                            && safeEquals(oldItem.getEventTitle(), newItem.getEventTitle())
                            && ((oldItem.getCreatedAt() == null && newItem.getCreatedAt() == null)
                            || (oldItem.getCreatedAt() != null
                            && newItem.getCreatedAt() != null
                            && oldItem.getCreatedAt().equals(newItem.getCreatedAt())));
                }

                private boolean safeEquals(Object a, Object b) {
                    if (a == null) return b == null;
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_broadcast, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvEventTitle, tvBroadcastTitle, tvMessage, tvTime;
        ImageButton btnDismiss;

        VH(@NonNull View itemView) {
            super(itemView);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvBroadcastTitle = itemView.findViewById(R.id.tvBroadcastTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnDismiss = itemView.findViewById(R.id.btnDismiss);
        }

        void bind(EventBroadcast b, Listener listener) {
            String event = b.getEventTitle();
            tvEventTitle.setText(
                    (event == null || event.isEmpty()) ? "(Sự kiện không tên)" : event
            );

            String title = b.getTitle();
            tvBroadcastTitle.setText(
                    (title == null || title.isEmpty()) ? "Thông báo từ BTC" : title
            );

            tvMessage.setText(b.getMessage() != null ? b.getMessage() : "");

            Timestamp ts = b.getCreatedAt();
            if (ts == null) {
                tvTime.setText("");
            } else {
                tvTime.setText(formatRelativeTime(ts.toDate()));
            }

            if (btnDismiss != null) {
                if (listener == null) {
                    btnDismiss.setVisibility(View.GONE);
                    btnDismiss.setOnClickListener(null);
                } else {
                    btnDismiss.setVisibility(View.VISIBLE);
                    btnDismiss.setOnClickListener(v -> listener.onDismiss(b));
                }
            }
        }

        private String formatRelativeTime(Date date) {
            long now = System.currentTimeMillis();
            long time = date.getTime();
            long diff = now - time;

            if (diff < TimeUnit.MINUTES.toMillis(1)) {
                return "Vừa xong";
            } else if (diff < TimeUnit.HOURS.toMillis(1)) {
                long mins = TimeUnit.MILLISECONDS.toMinutes(diff);
                return mins + " phút trước";
            } else if (diff < TimeUnit.DAYS.toMillis(1)) {
                long hours = TimeUnit.MILLISECONDS.toHours(diff);
                return hours + " giờ trước";
            } else if (diff < TimeUnit.DAYS.toMillis(7)) {
                long days = TimeUnit.MILLISECONDS.toDays(diff);
                return days + " ngày trước";
            } else {
                SimpleDateFormat sdf =
                        new SimpleDateFormat("HH:mm, dd/MM/yyyy", new Locale("vi", "VN"));
                return sdf.format(date);
            }
        }
    }
}
