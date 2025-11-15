package com.example.myapplication.attendee.detail;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.ActivityEventDetailBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ActivityEventDetailBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReviewAdapter reviewAdapter;
    private Event event; // sẽ được set sau khi fetch xong

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ❌ ĐOẠN NÀY TRƯỚC ĐÂY BỊ LỖI:
        // setContentView(R.layout.activity_event_detail);
        // + đọc etDescription, etCategory... -> mình bỏ hẳn vì không đúng màn hình này
        // (đã chuyển logic sang các hàm helper Organizer ở dưới)

        // Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.event_detail_title));
        }

        // Đọc eventId từ Intent
        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Không có ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // UI mặc định trước khi có dữ liệu
        binding.tvTitle.setText("");
        binding.tvArtist.setText(getString(R.string.artist_unknown));
        binding.tvVenue.setText("");
        binding.tvTime.setText("");
        binding.tvPrice.setText(getString(R.string.free));
        binding.tvReviewCount.setText(getString(R.string.review_count_fmt, 0));
        binding.recyclerReviews.setVisibility(View.GONE);
        binding.tvEmptyReviews.setVisibility(View.VISIBLE);
        binding.ratingAverage.setRating(0f);
        binding.tvAverageRating.setText("0.0/5");

        // Share
        binding.btnShare.setOnClickListener(v -> {
            String share = getString(R.string.share_template,
                    event != null && event.getTitle() != null ? event.getTitle() : "",
                    event != null && event.getLocation() != null ? event.getLocation() : "");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, share);
            startActivity(Intent.createChooser(intent, getString(R.string.share_event)));
        });

        // Follow toggle
        binding.btnFollow.setOnClickListener(v -> {
            boolean selected = !binding.btnFollow.isChecked();
            binding.btnFollow.setChecked(selected);
            Snackbar.make(
                    binding.getRoot(),
                    getString(selected ? R.string.followed : R.string.unfollowed),
                    Snackbar.LENGTH_SHORT
            ).show();
        });

        // Mở bản đồ (an toàn nếu event chưa tải xong)
        binding.btnOpenMap.setOnClickListener(v -> {
            String q = (event == null) ? null : event.getLocation();
            if (q == null || q.isEmpty()) {
                Toast.makeText(this, "Chưa có địa điểm", Toast.LENGTH_SHORT).show();
            } else {
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(q));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // RecyclerView reviews
        reviewAdapter = new ReviewAdapter();
        binding.recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReviews.setAdapter(reviewAdapter);

        // Fetch Event -> sau đó load reviews
        loadEvent(eventId);
    }

    // ===================== ORGANIZER HELPER =====================
    // Giữ lại logic quản lý vé / update sự kiện nhưng
    // KHÔNG dùng binding.* để tránh lỗi; sau này gọi từ màn Organizer riêng.

    /**
     * Thêm 1 loại vé cho sự kiện (tên, giá, quota).
     * Gọi chỗ khác như:
     * addTicketType(eventId, "Vé thường", 150000, 100);
     */
    public void addTicketType(String eventId,
                              String ticketTypeName,
                              double ticketPrice,
                              int ticketQuota) {

        Map<String, Object> ticket = new HashMap<>();
        ticket.put("name", ticketTypeName);
        ticket.put("price", ticketPrice);
        ticket.put("quota", ticketQuota);
        ticket.put("sold", 0);

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .add(ticket)
                .addOnSuccessListener(documentReference -> {
                    // Thành công, thêm vé vào sự kiện
                    // TODO: show Toast/log nếu cần
                })
                .addOnFailureListener(e -> {
                    // TODO: xử lý lỗi (log, Toast...)
                });
    }

    /**
     * Cập nhật số lượng vé đã bán cho 1 loại vé.
     */
    public void updateTicketSales(String eventId,
                                  String ticketTypeId,
                                  int soldQuantity) {
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .document(ticketTypeId)
                .update("sold", FieldValue.increment(soldQuantity))
                .addOnSuccessListener(aVoid -> {
                    // Thành công, cập nhật số vé đã bán
                })
                .addOnFailureListener(e -> {
                    // Xử lý lỗi
                });
    }

    /**
     * Chỉnh sửa / cập nhật cơ bản thông tin sự kiện đã tạo.
     * Sau này màn Organizer chỉ cần truyền title/description mới vào hàm này.
     */
    public void updateEventBasicInfo(String eventId,
                                     String title,
                                     String description) {

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", description);

        db.collection("events")
                .document(eventId)
                .update(data)
                .addOnSuccessListener(aVoid -> {
                    // Thành công
                })
                .addOnFailureListener(e -> {
                    // Xử lý lỗi
                });
    }
    // ================== HẾT ORGANIZER HELPER ====================

    private void loadEvent(String eventId) {
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    event = doc.toObject(Event.class);
                    if (event == null) {
                        Toast.makeText(this, "Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Đảm bảo có id (nếu schema không map sẵn)
                    try {
                        if (event.getId() == null || event.getId().isEmpty()) {
                            event.setId(doc.getId());
                        }
                    } catch (Exception ignored) {}

                    // Title trên ActionBar
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(
                                event.getTitle() == null
                                        ? getString(R.string.event_detail_title)
                                        : event.getTitle()
                        );
                    }

                    // Ảnh bìa
                    Glide.with(this)
                            .load(event.getThumbnail())
                            .placeholder(R.drawable.sample_event)
                            .error(R.drawable.sample_event)
                            .into(binding.ivCover);

                    // Texts
                    binding.tvTitle.setText(event.getTitle() == null ? "" : event.getTitle());
                    binding.tvArtist.setText(
                            event.getArtist() == null ? getString(R.string.artist_unknown) : event.getArtist()
                    );
                    binding.tvVenue.setText(event.getLocation() == null ? "" : event.getLocation());

                    String timeText = "";
                    if (event.getStartTime() != null) {
                        try {
                            String start = DateFormat.format(
                                    "EEE, dd/MM/yyyy • HH:mm",
                                    event.getStartTime().toDate()
                            ).toString();
                            if (event.getEndTime() != null) {
                                String end = DateFormat.format(
                                        "HH:mm, dd/MM",
                                        event.getEndTime().toDate()
                                ).toString();
                                timeText = getString(R.string.time_range_fmt, start, end);
                            } else {
                                timeText = start;
                            }
                        } catch (Exception ignored) {}
                    }
                    binding.tvTime.setText(timeText);

                    Double p = event.getPrice();
                    String priceText = (p == null || p == 0d)
                            ? getString(R.string.free)
                            : NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(p) + "₫";
                    binding.tvPrice.setText(priceText);

                    // Sau khi có event -> load reviews
                    loadReviews();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải sự kiện: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadReviews() {
        if (event == null || event.getId() == null) return;

        db.collection("events").document(event.getId())
                .collection("reviews")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Review> reviews = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Review r = d.toObject(Review.class);
                        if (r != null) reviews.add(r);
                    }
                    reviewAdapter.submit(reviews);

                    int count = reviews.size();
                    binding.tvReviewCount.setText(getString(R.string.review_count_fmt, count));

                    // ⭐ Tính điểm trung bình
                    double total = 0;
                    for (Review r : reviews) {
                        if (r.rating != null) total += r.rating;
                    }
                    double avg = count > 0 ? total / count : 0;
                    binding.ratingAverage.setRating((float) avg);
                    binding.tvAverageRating.setText(
                            String.format(Locale.getDefault(), "%.1f/5", avg)
                    );

                    // Toggle empty/list
                    if (count == 0) {
                        binding.recyclerReviews.setVisibility(View.GONE);
                        binding.tvEmptyReviews.setVisibility(View.VISIBLE);
                    } else {
                        binding.recyclerReviews.setVisibility(View.VISIBLE);
                        binding.tvEmptyReviews.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không tải được đánh giá: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // dùng onBackPressed() cũ
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Model review (tối giản)
    public static class Review {
        public String author;
        public String content;
        public Double rating; // 0..5

        public Review() {}
    }
}
