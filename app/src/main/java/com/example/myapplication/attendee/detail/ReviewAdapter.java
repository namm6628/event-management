package com.example.myapplication.attendee.detail;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.ItemReviewBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách đánh giá (Review).
 * Sử dụng ItemReviewBinding (layout: res/layout/item_review.xml).
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private final List<EventDetailActivity.Review> items = new ArrayList<>();

    public ReviewAdapter() {}

    public void submit(List<EventDetailActivity.Review> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding b = ItemReviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EventDetailActivity.Review r = items.get(position);
        if (r == null) return;

        // Tên người review (layout dùng tvReviewerName)
        h.b.tvReviewerName.setText(r.author == null ? "Ẩn danh" : r.author);

        // Nội dung
        h.b.tvReviewContent.setText(r.content == null ? "" : r.content);

        // Rating (model dùng Double); set rating an toàn
        if (r.rating != null) {
            try {
                h.b.ratingBar.setRating(r.rating.floatValue());
            } catch (Exception ex) {
                h.b.ratingBar.setRating(0f);
            }
        } else {
            h.b.ratingBar.setRating(0f);
        }

        // Thời gian: nếu model có createdAt thì bạn có thể hiển thị ở đây.
        // Hiện tại model Review (trong EventDetailActivity) không khai báo createdAt,
        // nên tạm để trống hoặc hiển thị giá trị mặc định.
        h.b.tvReviewTime.setText(""); // hoặc "—"
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;
        VH(@NonNull ItemReviewBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }
    }
}
