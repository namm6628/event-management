package com.example.myapplication.attendee.detail;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.ItemReviewBinding;

import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.ActivityEventDetailBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        // 4. [CẬP NHẬT] - Thời gian
        if (r.createdAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            h.b.tvReviewTime.setText(sdf.format(r.createdAt.toDate()));
        } else {
            h.b.tvReviewTime.setText("Vừa xong");
        }
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
