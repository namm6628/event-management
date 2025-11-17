package com.example.myapplication.attendee.detail;

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
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    // [QUAN TRỌNG] Thay bằng API Key OpenWeatherMap của bạn
    private static final String WEATHER_API_KEY = "d217750b1e400fc2300711ab107183f2";

    private ActivityEventDetailBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReviewAdapter reviewAdapter;
    private Event event;
    private RequestQueue volleyQueue; // Hàng đợi để gọi API thời tiết

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Khởi tạo Volley
        volleyQueue = Volley.newRequestQueue(this);

        // Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.event_detail_title));
        }

        binding.toolbar.setNavigationOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed(); // Quay lại màn hình trước
        });

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Không có ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupUIInteractions();

        // Setup RecyclerView Reviews
        reviewAdapter = new ReviewAdapter();
        binding.recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReviews.setAdapter(reviewAdapter);

        // Tải dữ liệu
        loadEvent(eventId);
    }

    private void setupUIInteractions() {
        // 1. Share (Giữ nguyên)
        binding.btnShare.setOnClickListener(v -> {
            String share = getString(R.string.share_template,
                    event != null ? event.getTitle() : "",
                    event != null ? event.getLocation() : "");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, share);
            startActivity(Intent.createChooser(intent, getString(R.string.share_event)));
        });

        // 2. Follow (Giữ nguyên)
        binding.btnFollow.setOnClickListener(v -> {
            boolean selected = !binding.btnFollow.isChecked();
            binding.btnFollow.setChecked(selected);
            Snackbar.make(binding.getRoot(),
                    getString(selected ? R.string.followed : R.string.unfollowed),
                    Snackbar.LENGTH_SHORT).show();
        });

        // 3. Map (Giữ nguyên)
        binding.btnOpenMap.setOnClickListener(v -> {
            String q = (event == null) ? null : event.getLocation();
            if (q == null || q.isEmpty()) {
                Toast.makeText(this, "Chưa có địa điểm", Toast.LENGTH_SHORT).show();
            } else {
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(q));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // 4. [MỚI] Viết đánh giá
        binding.btnWriteReview.setOnClickListener(v -> showRateDialog());

        // 5. [MỚI] Đặt vé
        binding.btnBookTicket.setOnClickListener(v -> {
            if (event == null) return;
            Toast.makeText(this, "Chuyển đến màn hình chọn vé...", Toast.LENGTH_SHORT).show();
            // TODO: Mở BookingActivity hoặc BottomSheet đặt vé
        });
    }

    // --- LOGIC TẢI SỰ KIỆN TỪ FIRESTORE ---
    private void loadEvent(String eventId) {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(doc -> {
                    event = doc.toObject(Event.class);
                    if (event == null) {
                        Toast.makeText(this, "Sự kiện không tồn tại", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    if (event.getId() == null) event.setId(doc.getId());

                    updateUIWithEventData(event);

                    // Gọi API thời tiết ngay khi có thông tin sự kiện
                    loadWeatherForecast(event);

                    // Tải danh sách đánh giá
                    loadReviews();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUIWithEventData(Event event) {
        // Text info
        binding.tvTitle.setText(event.getTitle());
        binding.tvArtist.setText(event.getArtist() == null ? getString(R.string.artist_unknown) : event.getArtist());
        binding.tvVenue.setText(event.getLocation());

        // Thời gian
        if (event.getStartTime() != null) {
            String timeStr = DateFormat.format("EEE, dd/MM/yyyy • HH:mm", event.getStartTime().toDate()).toString();
            binding.tvTime.setText("Thời gian: " + timeStr);
        }

        // Ảnh bìa
        Glide.with(this)
                .load(event.getThumbnail())
                .placeholder(R.drawable.sample_event)
                .into(binding.ivCover);

        // Giá vé (Cập nhật vào thanh Bottom Bar)
        Double p = event.getPrice();
        String priceText = (p == null || p == 0d) ? "Miễn phí"
                : NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(p) + " đ";
        binding.tvBottomPrice.setText(priceText);
    }

    // --- [MỚI] LOGIC GỌI API THỜI TIẾT (VOLLEY) ---
    private void loadWeatherForecast(Event event) {
        if (event.getLocation() == null || event.getStartTime() == null) {
            binding.tvWeather.setText("Không có thông tin để dự báo");
            return;
        }

        // Lấy thời gian sự kiện
        long eventTimeMillis = event.getStartTime().toDate().getTime();
        // API miễn phí chỉ dự báo 5 ngày. Nếu sự kiện xa quá thì thôi.
        if (eventTimeMillis > System.currentTimeMillis() + (5L * 24 * 3600 * 1000)) {
            binding.tvWeather.setText("Sự kiện còn quá xa để dự báo thời tiết");
            return;
        }

        // Chuyển tên thành phố sang tiếng Anh (API cần)
        String city = mapCityName(event.getLocation());

        // URL API OpenWeatherMap
        String url = String.format("https://api.openweathermap.org/data/2.5/forecast?q=%s&appid=%s&units=metric&lang=vi",
                city, WEATHER_API_KEY);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray list = response.getJSONArray("list");
                        // Tìm mốc thời gian dự báo gần với giờ sự kiện nhất
                        JSONObject bestForecast = null;
                        long minDiff = Long.MAX_VALUE;

                        for (int i = 0; i < list.length(); i++) {
                            JSONObject item = list.getJSONObject(i);
                            long dt = item.getLong("dt") * 1000L;
                            long diff = Math.abs(dt - eventTimeMillis);
                            if (diff < minDiff) {
                                minDiff = diff;
                                bestForecast = item;
                            }
                        }

                        if (bestForecast != null) {
                            double temp = bestForecast.getJSONObject("main").getDouble("temp");
                            String desc = bestForecast.getJSONArray("weather").getJSONObject(0).getString("description");
                            // Update UI
                            binding.tvWeather.setText(String.format(Locale.getDefault(),
                                    "Dự báo: %.0f°C, %s", temp, desc));
                        }
                    } catch (JSONException e) {
                        binding.tvWeather.setText("Không có dữ liệu thời tiết");
                    }
                },
                error -> {
                    binding.tvWeather.setText("Không tải được thời tiết");
                }
        );

        volleyQueue.add(request);
    }

    private String mapCityName(String location) {
        if (location == null) return "Ho Chi Minh City";
        String loc = location.toLowerCase();
        if (loc.contains("hcm") || loc.contains("hồ chí minh") || loc.contains("ho chi minh")) return "Ho Chi Minh City";
        if (loc.contains("hà nội") || loc.contains("ha noi")) return "Hanoi";
        if (loc.contains("đà nẵng") || loc.contains("da nang")) return "Da Nang";
        return "Ho Chi Minh City"; // Mặc định
    }

    // --- LOGIC ĐÁNH GIÁ (REVIEW) ---
    private void loadReviews() {
        if (event == null || event.getId() == null) return;
        db.collection("events").document(event.getId())
                .collection("reviews")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Review> reviews = new ArrayList<>();
                    double totalRating = 0;
                    for (DocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        if (r != null) {
                            reviews.add(r);
                            if(r.rating != null) totalRating += r.rating;
                        }
                    }
                    reviewAdapter.submit(reviews);

                    // Update thống kê
                    int count = reviews.size();
                    binding.tvReviewCount.setText("(" + count + ")");
                    if (count > 0) {
                        double avg = totalRating / count;
                        binding.tvAverageRating.setText(String.format(Locale.getDefault(), "%.1f/5", avg));
                        binding.ratingAverage.setRating((float) avg);
                        binding.tvEmptyReviews.setVisibility(View.GONE);
                        binding.recyclerReviews.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmptyReviews.setVisibility(View.VISIBLE);
                        binding.recyclerReviews.setVisibility(View.GONE);
                    }
                });
    }

    private void showRateDialog() {
        // Tạo view dialog từ layout dialog_rate_review.xml
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rate_review, null);
        RatingBar rb = dialogView.findViewById(R.id.dialogRatingBar);
        EditText et = dialogView.findViewById(R.id.etDialogComment);

        new AlertDialog.Builder(this)
                .setTitle("Đánh giá sự kiện")
                .setView(dialogView)
                .setPositiveButton("Gửi", (dialog, which) -> {
                    float rating = rb.getRating();
                    String content = et.getText().toString();
                    if (rating > 0) submitReview(rating, content);
                    else Toast.makeText(this, "Vui lòng chọn sao!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Trong file EventDetailActivity.java

    private void submitReview(float rating, String content) {
        if (event == null) return;

        // 1. Đóng gói dữ liệu
        Map<String, Object> data = new HashMap<>();
        data.put("author", "Tôi");
        data.put("rating", rating);
        data.put("content", content);
        data.put("createdAt", FieldValue.serverTimestamp());

        // 2. Gửi lên Firebase
        db.collection("events").document(event.getId()) // Vào đúng sự kiện
                .collection("reviews")                  // Vào (hoặc tạo mới) bảng 'reviews'
                .add(data)                              // Thêm dữ liệu
                .addOnSuccessListener(r -> {
                    Toast.makeText(this, "Đánh giá thành công!", Toast.LENGTH_SHORT).show();
                    loadReviews(); // Tải lại danh sách để hiện ngay review vừa viết
                });
    }

    // Model Review nội bộ
    public static class Review {
        public String author;
        public String content;
        public Double rating;
        public Timestamp createdAt; // Quan trọng để hiển thị thời gian

        public Review() {}
    }
}