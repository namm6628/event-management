package com.example.myapplication.attendee.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class EventAdapter extends ListAdapter<Event, EventAdapter.VH> {

    public interface Listener {
        void onClick(Event e);
    }

    private final Listener listener;

    // Constructor cho Explore
    public EventAdapter(Listener l) {
        super(DIFF);
        this.listener = l;
    }

    // Constructor overload cho Home
    public EventAdapter(List<Event> initialData, Listener listener) {
        this(listener);
        submitList(initialData);
    }

    private static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
        @Override
        public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
            return a.equals(b);
        }
    };

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle;
        @Nullable TextView tvMetaLine1;
        @Nullable TextView tvMetaLine2;

        VH(@NonNull View v) {
            super(v);
            imgCover = v.findViewById(R.id.imgCover);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvMetaLine1 = v.findViewById(R.id.tvMetaLine1);
            tvMetaLine2 = v.findViewById(R.id.tvMetaLine2);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Event e = getItem(pos);

        if (h.tvTitle != null) h.tvTitle.setText(e.title);

        if (h.imgCover != null) {
            Glide.with(h.imgCover.getContext())
                    .load(e.coverUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(h.imgCover);
        }

        String dateStr = e.date; // vì date là String

        String venueStr = e.venue;

        setTextOrHide(h.tvMetaLine1, dateStr);
        setTextOrHide(h.tvMetaLine2, venueStr);

        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }

    private void setTextOrHide(@Nullable TextView tv, @Nullable String text) {
        if (tv == null) return;
        if (text == null || text.trim().isEmpty()) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }

    // Giữ tương thích với HomeFragment cũ
    public void submitFilter(String currentQuery, String currentCategory) {
        if (getCurrentList() == null) return;
        java.util.List<Event> filtered = new java.util.ArrayList<>();
        for (Event e : getCurrentList()) {
            boolean ok = (currentQuery == null || e.title.toLowerCase().contains(currentQuery.toLowerCase()))
                    && (currentCategory == null || e.category.equalsIgnoreCase(currentCategory));
            if (ok) filtered.add(e);
        }
        submitList(filtered);
    }


}
