package com.example.myapplication.attendee.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.firebase.Timestamp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventAdapter extends ListAdapter<Event, EventAdapter.VH> {

    public interface OnItemClick {
        void onClick(@NonNull Event e);
    }

    private final OnItemClick onItemClick;

    public EventAdapter(OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClick = onItemClick;
    }

    private static final DiffUtil.ItemCallback<Event> DIFF =
            new DiffUtil.ItemCallback<Event>() {
                @Override
                public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                    String ai = a.getId(), bi = b.getId();
                    return ai != null && bi != null && ai.equals(bi);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                    return safe(a.getTitle()).equals(safe(b.getTitle()))
                            && safe(a.getLocation()).equals(safe(b.getLocation()))
                            && val(a.getPrice()) == val(b.getPrice())
                            && ts(a.getStartTime()) == ts(b.getStartTime())
                            && safe(a.getThumbnail()).equals(safe(b.getThumbnail()));
                }

                private String safe(String s) { return s == null ? "" : s; }
                private double val(Double d)   { return d == null ? 0d : d; }
                private long ts(Timestamp t)  {
                    return t == null ? 0L : t.toDate().getTime();
                }
            };

    // =========== ViewHolder =============
    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final TextView tvTitle, tvPrice, tvDate;


        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb   = itemView.findViewById(R.id.ivThumb);
            tvTitle   = itemView.findViewById(R.id.tvTitle);
            tvPrice   = itemView.findViewById(R.id.tvPrice);
            tvDate    = itemView.findViewById(R.id.tvDate);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_explore_event, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Event e = getItem(position);

        String title = e.getTitle() == null ? "—" : e.getTitle();
        h.tvTitle.setText(title);

        String dateText = "";
        Timestamp ts = e.getStartTime();
        if (ts != null) {
            Date d = ts.toDate();
            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd 'Tháng' MM, yyyy", new Locale("vi", "VN"));
            dateText = sdf.format(d);
        }
        h.tvDate.setText(dateText);

        String priceLabel;
        if (e.isEnded()) {
            priceLabel = "Sự kiện đã kết thúc";
        } else if (e.isSoldOut()) {
            priceLabel = "Đã hết vé";
        } else {
            Double p = e.getPrice();
            if (p == null || p <= 0) {
                priceLabel = "Miễn phí";
            } else {
                String pStr = NumberFormat
                        .getNumberInstance(new Locale("vi", "VN"))
                        .format(p);
                priceLabel = "Từ " + pStr + " đ";
            }
        }
        h.tvPrice.setText(priceLabel);


        Glide.with(h.itemView.getContext())
                .load(e.getThumbnail())
                .placeholder(R.drawable.sample_event)
                .error(R.drawable.sample_event)
                .centerCrop()
                .into(h.ivThumb);

        h.itemView.setOnClickListener(v -> {
            if (onItemClick != null) onItemClick.onClick(e);
        });
    }

}
