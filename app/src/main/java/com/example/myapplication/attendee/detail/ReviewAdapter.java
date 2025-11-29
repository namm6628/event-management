package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
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

    @Nullable
    private String currentUserId = null;

    @Nullable
    private ReviewActionListener actionListener = null;

    public interface ReviewActionListener {
        void onEdit(@NonNull EventDetailActivity.Review review);
        void onDelete(@NonNull EventDetailActivity.Review review);
    }

    public ReviewAdapter() {
    }

    public ReviewAdapter(@Nullable String currentUserId,
                         @Nullable ReviewActionListener listener) {
        this.currentUserId = currentUserId;
        this.actionListener = listener;
    }

    public void setCurrentUserId(@Nullable String currentUserId) {
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    public void setActionListener(@Nullable ReviewActionListener listener) {
        this.actionListener = listener;
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

        // 5. Media (ảnh / video)
        bindMedia(h, r);

        // 6. Nút Sửa / Xóa (chỉ hiện nếu là review của user hiện tại)
        boolean isMine = (currentUserId != null
                && r.userId != null
                && currentUserId.equals(r.userId));

        if (isMine) {
            h.b.btnEditReview.setVisibility(View.VISIBLE);
            h.b.btnDeleteReview.setVisibility(View.VISIBLE);
        } else {
            h.b.btnEditReview.setVisibility(View.GONE);
            h.b.btnDeleteReview.setVisibility(View.GONE);
        }

        h.b.btnEditReview.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onEdit(r);
            }
        });

        h.b.btnDeleteReview.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDelete(r);
            }
        });
    }

    private void bindMedia(@NonNull VH h, @NonNull EventDetailActivity.Review r) {
        String mediaUrl = r.mediaUrl;
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            h.b.imgReviewMedia.setVisibility(View.GONE);
            h.b.imgReviewMedia.setOnClickListener(null);
            return;
        }

        h.b.imgReviewMedia.setVisibility(View.VISIBLE);

        String lower = mediaUrl.toLowerCase(Locale.ROOT);
        boolean isVideo = lower.endsWith(".mp4")
                || lower.endsWith(".mov")
                || lower.endsWith(".mkv")
                || lower.contains("video");

        if (isVideo) {
            // hiện icon video
            h.b.imgReviewMedia.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            h.b.imgReviewMedia.setImageResource(R.drawable.ic_video_placeholder);

            h.b.imgReviewMedia.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(mediaUrl), "video/*");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    // Nếu không mở được video thì thôi, không crash
                }
            });
        } else {
            // là ảnh → load bằng Glide
            h.b.imgReviewMedia.setOnClickListener(null);
            h.b.imgReviewMedia.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            Glide.with(h.b.imgReviewMedia.getContext())
                    .load(mediaUrl)
                    .placeholder(R.drawable.sample_event)
                    .error(R.drawable.sample_event)
                    .into(h.b.imgReviewMedia);
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
