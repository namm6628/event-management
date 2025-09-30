package com.example.myapplication.attendee.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import java.util.ArrayList;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.VH> {

    public interface OnClick { void onEventClick(Event e); }

    private final List<Event> origin;
    private final List<Event> shown;
    private final OnClick onClick;

    public EventAdapter(List<Event> data, OnClick onClick) {
        this.origin = new ArrayList<>(data);
        this.shown  = new ArrayList<>(data);
        this.onClick = onClick;
    }

    public void submitFilter(String query, String category) {
        shown.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        String cat = category == null ? "" : category;

        for (Event e : origin) {
            boolean matchCat = (cat.isEmpty() || cat.equals("Tất cả") || cat.equals(e.category));
            boolean matchText = q.isEmpty()
                    || e.title.toLowerCase().contains(q)
                    || e.venue.toLowerCase().contains(q);
            if (matchCat && matchText) shown.add(e);
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        Event e = shown.get(i);
        Glide.with(h.img.getContext()).load(e.coverUrl).into(h.img);
        h.title.setText(e.title);
        h.meta.setText(e.date + " • " + e.venue);
        h.tag.setText(e.category);
        h.price.setText(e.price);
        h.itemView.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).start(); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start(); break;
            }
            return false;
        });

        h.itemView.setOnClickListener(v -> onClick.onEventClick(e));
    }
    @Override public int getItemCount() { return shown.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView title, meta, tag, price;
        VH(@NonNull View v){
            super(v);
            img = v.findViewById(R.id.imgCover);
            title = v.findViewById(R.id.tvTitle);
            meta = v.findViewById(R.id.tvMeta);
            tag = v.findViewById(R.id.tvTag);
            price = v.findViewById(R.id.tvPrice);
        }
    }
}
