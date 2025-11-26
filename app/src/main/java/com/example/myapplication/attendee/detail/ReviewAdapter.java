package com.example.myapplication.attendee.detail;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.ItemReviewBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách đánh giá (Review).
 * Sử dụng ItemReviewBinding (layout: res/layout/item_review.xml).
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private final List<EventDetailActivity.Review> items = new ArrayList<>();

    public ReviewAdapter() {
    }

    public void submit(List<EventDetailActivity.Review> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding b = ItemReviewBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EventDetailActivity.Review r = items.get(position);
        if (r == null) return;

        // 1. Tên người review
        h.b.tvReviewerName.setText(
                (r.author == null || r.author.trim().isEmpty())
                        ? "Ẩn danh"
                        : r.author
        );

        // 2. Nội dung
        h.b.tvReviewContent.setText(
                r.content == null ? "" : r.content
        );

        // 3. Số sao
        if (r.rating != null) {
            try {
                h.b.ratingBar.setRating(r.rating.floatValue());
            } catch (Exception ignored) {
                h.b.ratingBar.setRating(0f);
            }
        } else {
            h.b.ratingBar.setRating(0f);
        }

        // 4. Thời gian tạo review (createdAt)
        if (r.createdAt != null) {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            h.b.tvReviewTime.setText(sdf.format(r.createdAt.toDate()));
        } else {
            h.b.tvReviewTime.setText("Vừa xong");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;

        VH(@NonNull ItemReviewBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}
