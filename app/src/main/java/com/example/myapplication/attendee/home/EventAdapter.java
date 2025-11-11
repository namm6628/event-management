package com.example.myapplication.attendee.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.firebase.Timestamp;  // ✅ dùng để xử lý startTime

import java.text.NumberFormat;
import java.util.Locale;

/**
 * ListAdapter + DiffUtil để cập nhật mượt.
 * onItemClick: truyền lambda nếu muốn xử lý click item (có thể null).
 */
public class EventAdapter extends ListAdapter<Event, EventAdapter.VH> {

    public interface OnItemClick { void onClick(@NonNull Event e); }

    private final OnItemClick onItemClick;

    public EventAdapter(OnItemClick onItemClick) {
        super(DIFF);
        this.onItemClick = onItemClick;
    }

    static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
        @Override
        public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
            String ai = a.getId(), bi = b.getId();
            return ai != null && bi != null && ai.equals(bi);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
            // nếu Event chưa có equals(), so sánh các field cơ bản
            return safe(a.getTitle()).equals(safe(b.getTitle()))
                    && safe(a.getLocation()).equals(safe(b.getLocation()))
                    && safe(a.getCategory()).equals(safe(b.getCategory()))
                    && val(a.getPrice()) == val(b.getPrice())                         // Double
                    && val(a.getStartTime()) == val(b.getStartTime())                 // ✅ Timestamp → millis
                    && val(a.getAvailableSeats()) == val(b.getAvailableSeats())       // Integer
                    && val(a.getTotalSeats()) == val(b.getTotalSeats())               // Integer
                    && safe(a.getThumbnail()).equals(safe(b.getThumbnail()));
        }

        private String safe(String s){ return s == null ? "" : s; }
        private long val(Integer i){ return i == null ? -1L : i.longValue(); }
        private long val(Long l){ return l == null ? -1L : l; }
        private double val(Double d) { return d == null ? 0.0 : d; }

        // ✅ helper cho Firestore Timestamp
        private long val(Timestamp t) { return t == null ? 0L : t.toDate().getTime(); }
    };

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Event e = getItem(position);
        h.tvTitle.setText(nz(e.getTitle(), "—"));
        h.tvLocation.setText(nz(e.getLocation(), "—"));

        String priceText = "—";
        if (e.getPrice() != null) {
            priceText = NumberFormat.getCurrencyInstance(new Locale("vi","VN"))
                    .format(e.getPrice());
        }
        h.tvPrice.setText(h.itemView.getContext().getString(R.string.price_format, priceText));

        // ✅ Load thumbnail bằng Glide
        Glide.with(h.itemView.getContext())
                .load(e.getThumbnail())                 // URL từ Firestore
                .placeholder(R.drawable.sample_event)    // ảnh tạm
                .error(R.drawable.sample_event)          // ảnh lỗi
                .centerCrop()
                .into(h.imgThumb);


        h.itemView.setOnClickListener(v -> {
            if (onItemClick != null) onItemClick.onClick(e);
        });
    }

    static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        final ImageView imgThumb;
        final TextView tvTitle, tvLocation, tvPrice;
        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb   = itemView.findViewById(R.id.ivThumb); // 👈 trùng id trong XML
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvPrice = itemView.findViewById(R.id.tvPrice);
        }
    }

    private static String nz(String s, String d){ return s == null ? d : s; }
}
