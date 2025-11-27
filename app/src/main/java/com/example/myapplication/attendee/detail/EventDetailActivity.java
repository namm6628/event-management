package com.example.myapplication.attendee.detail;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.ActivityEventDetailBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    // API Key OpenWeatherMap (đổi thành key của bạn)
    private static final String WEATHER_API_KEY = "d217750b1e400fc2300711ab107183f2";

    // Volley queue để gọi API thời tiết
    private RequestQueue volleyQueue;

    private ActivityEventDetailBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReviewAdapter reviewAdapter;
    private Event event; // sẽ được set sau khi fetch xong

    private String eventId;
    private ListenerRegistration eventListener;

    private TicketTypeAdapter ticketTypeAdapter;
    private Double minTicketPrice = null;
    private boolean isDescriptionExpanded = false;

    // Weather UI
    private View layoutWeatherContainer;
    private View layoutSkeletonWeather;
    private View layoutWeather;
    private ImageView ivWeatherIcon;
    private TextView tvWeather;

    private FirebaseUser currentUser;
    private String currentUserId;

    // Reviews
    private boolean reviewsExpanded = false;
    private static final int REVIEWS_COLLAPSED_LIMIT = 3;
    private final List<Review> allReviews = new ArrayList<>();

    // Recommended events
    private final List<RecommendedEvent> recommendedList = new ArrayList<>();
    private RecommendedAdapter recommendedAdapter;

    // trạng thái user đã theo dõi (favorite) event này chưa
    private boolean isFavorite = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        // Khởi tạo Volley
        volleyQueue = Volley.newRequestQueue(this);

        // Link weather views từ layout
        layoutWeatherContainer = binding.layoutWeatherContainer;
        layoutSkeletonWeather = binding.layoutSkeletonWeather;
        layoutWeather = binding.layoutWeather;
        ivWeatherIcon = binding.ivWeatherIcon;
        tvWeather = binding.tvWeather;

        // Mặc định thu gọn mô tả
        binding.tvDescription.setMaxLines(4);
        binding.tvDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);

        binding.tvDescriptionToggle.setOnClickListener(v -> {
            isDescriptionExpanded = !isDescriptionExpanded;
            if (isDescriptionExpanded) {
                binding.tvDescription.setMaxLines(Integer.MAX_VALUE);
                binding.tvDescription.setEllipsize(null);
                binding.tvDescriptionToggle.setText(R.string.see_less);
            } else {
                binding.tvDescription.setMaxLines(4);
                binding.tvDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
                binding.tvDescriptionToggle.setText(R.string.see_more);
            }
        });

        binding.btnWriteReview.setOnClickListener(v -> showRateDialog());

        // Adapter loại vé
        ticketTypeAdapter = new TicketTypeAdapter();
        binding.recyclerTicketTypes.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        );
        binding.recyclerTicketTypes.setAdapter(ticketTypeAdapter);

        binding.tvTicketDateTime.setText(getString(R.string.ticket_info_header));

        // Toggle ẩn/hiện loại vé
        binding.tvToggleTicketTypes.setOnClickListener(v -> {
            if (binding.recyclerTicketTypes.getVisibility() == View.VISIBLE) {
                binding.recyclerTicketTypes.setVisibility(View.GONE);
                binding.tvToggleTicketTypes.setText("Xem loại vé");
            } else {
                binding.recyclerTicketTypes.setVisibility(View.VISIBLE);
                binding.tvToggleTicketTypes.setText("Ẩn loại vé");
            }
        });

        // Lấy eventId từ Intent
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        // Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.event_detail_title);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Không có ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // UI mặc định
        binding.tvTitle.setText("");
        binding.tvArtist.setText(getString(R.string.artist_unknown));
        binding.tvVenue.setText("");
        binding.tvTime.setText("");
        binding.tvPrice.setText(getString(R.string.free));
        binding.tvDescription.setText("");

        binding.tvReviewCount.setText(getString(R.string.review_count_fmt, 0));
        binding.recyclerReviews.setVisibility(View.GONE);
        binding.tvEmptyReviews.setVisibility(View.VISIBLE);
        binding.ratingAverage.setRating(0f);
        binding.tvAverageRating.setText("0.0/5");

        // Share
        binding.btnShare.setOnClickListener(v -> {
            String share = getString(
                    R.string.share_template,
                    event != null && event.getTitle() != null ? event.getTitle() : "",
                    event != null && event.getLocation() != null ? event.getLocation() : ""
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, share);
            startActivity(Intent.createChooser(intent, getString(R.string.share_event)));
        });

        // Nút Theo dõi → toggle favorite
        binding.btnFollow.setOnClickListener(v -> toggleFavorite());
        // text mặc định
        updateFollowButtonUi();

        // Mở bản đồ
        binding.btnOpenMap.setOnClickListener(v -> {
            String q = null;
            if (event != null) {
                if (event.getAddressDetail() != null && !event.getAddressDetail().isEmpty()) {
                    q = event.getAddressDetail();
                } else {
                    q = event.getLocation();
                }
            }

            if (q == null || q.isEmpty()) {
                Toast.makeText(this, "Chưa có địa chỉ để mở bản đồ", Toast.LENGTH_SHORT).show();
            } else {
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(q));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // Reviews
        reviewAdapter = new ReviewAdapter();
        binding.recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReviews.setAdapter(reviewAdapter);

        // Nút "Xem thêm" đánh giá
        binding.btnMoreReviews.setOnClickListener(v -> {
            reviewsExpanded = !reviewsExpanded;
            renderReviewsUi();
        });

        // Recommended events Recycler
        recommendedAdapter = new RecommendedAdapter();
        binding.recyclerRecommendedEvents.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        binding.recyclerRecommendedEvents.setAdapter(recommendedAdapter);

        // Nút Đặt vé
        binding.btnBuyTicket.setOnClickListener(v -> {
            if (event == null) {
                Toast.makeText(this, "Chưa tải xong dữ liệu sự kiện", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEventEnded(event)) {
                Toast.makeText(
                        this,
                        "Sự kiện đã kết thúc, không thể đặt vé nữa.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (event.isSoldOut()) {
                Toast.makeText(
                        this,
                        "Sự kiện đã hết vé.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (eventId == null || eventId.isEmpty()) {
                Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent i = new Intent(EventDetailActivity.this, SelectTicketsActivity.class);
            i.putExtra(SelectTicketsActivity.EXTRA_EVENT_ID, eventId);
            startActivity(i);
        });
    }

    // ===================== ORGANIZER HELPER =====================

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
                .add(ticket);
    }

    public void updateTicketSales(String eventId,
                                  String ticketTypeId,
                                  int soldQuantity) {
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .document(ticketTypeId)
                .update("sold", FieldValue.increment(soldQuantity));
    }

    public void updateEventBasicInfo(String eventId,
                                     String title,
                                     String description) {

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", description);

        db.collection("events")
                .document(eventId)
                .update(data);
    }

    // ================== REALTIME EVENT + REVIEWS ====================

    @Override
    protected void onStart() {
        super.onStart();

        if (eventId == null || eventId.isEmpty()) return;

        if (eventListener != null) {
            eventListener.remove();
            eventListener = null;
        }

        eventListener = db.collection("events")
                .document(eventId)
                .addSnapshotListener((doc, ex) -> {
                    if (ex != null) {
                        Toast.makeText(this, "Lỗi tải sự kiện: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        Toast.makeText(this, "Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    event = doc.toObject(Event.class);
                    if (event == null) {
                        Toast.makeText(this, "Không tìm thấy sự kiện", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    try {
                        if (event.getId() == null || event.getId().isEmpty()) {
                            event.setId(doc.getId());
                        }
                    } catch (Exception ignored) {
                    }

                    // Ảnh
                    String thumb = event.getThumbnail();
                    Glide.with(this)
                            .load(thumb)
                            .centerCrop()
                            .placeholder(R.drawable.sample_event)
                            .error(R.drawable.sample_event)
                            .into(binding.ivCover);

                    // Text
                    binding.tvTitle.setText(event.getTitle() == null ? "" : event.getTitle());
                    binding.tvArtist.setText(
                            event.getArtist() == null
                                    ? getString(R.string.artist_unknown)
                                    : event.getArtist()
                    );
                    binding.tvVenue.setText(event.getLocation() == null ? "" : event.getLocation());
                    binding.tvAddressDetail.setText(
                            event.getAddressDetail() == null ? "" : event.getAddressDetail()
                    );

                    String timeText = "";
                    if (event.getStartTime() != null) {
                        try {
                            java.util.Date startDate = event.getStartTime().toDate();
                            String day = DateFormat.format("dd/MM/yyyy", startDate).toString();
                            String startHour = DateFormat.format("HH:mm", startDate).toString();

                            if (event.getEndTime() != null) {
                                java.util.Date endDate = event.getEndTime().toDate();
                                String endHour = DateFormat.format("HH:mm", endDate).toString();
                                timeText = startHour + " - " + endHour + ", " + day;
                            } else {
                                timeText = startHour + ", " + day;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    binding.tvTime.setText(timeText);
                    binding.tvTicketDateTime.setText(timeText);

                    String desc = event.getDescription();
                    binding.tvDescription.setText(
                            (desc == null || desc.trim().isEmpty()) ? "" : desc
                    );

                    Double p = event.getPrice();
                    String priceText = (p == null || p == 0d)
                            ? getString(R.string.free)
                            : NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(p) + "₫";
                    binding.tvPrice.setText(priceText);
                    binding.tvBottomPrice.setText(priceText);

                    loadTicketTypes();
                    loadWeatherForecast(event);
                    loadReviews();

                    updateBuyButtonState();

                    // Luôn hiển thị list loại vé, chỉ làm mờ item nếu event kết thúc
                    binding.recyclerTicketTypes.setVisibility(View.VISIBLE);
                    binding.tvToggleTicketTypes.setVisibility(View.VISIBLE);

                    // báo cho adapter biết event đã kết thúc chưa để set alpha item
                    ticketTypeAdapter.setEventEnded(isEventEnded(event));

                    // Sau khi đã có event → kiểm tra trạng thái favorite ban đầu
                    checkFavoriteState();
                    loadRecommendedEvents();

                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventListener != null) {
            eventListener.remove();
            eventListener = null;
        }
    }

    private void loadTicketTypes() {
        if (event == null || event.getId() == null) return;

        db.collection("events")
                .document(event.getId())
                .collection("ticketTypes")
                .addSnapshotListener((snap, ex) -> {
                    if (ex != null) {
                        Toast.makeText(this,
                                "Không tải được loại vé: " + ex.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    List<TicketTypeAdapter.TicketType> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketTypeAdapter.TicketType t =
                                d.toObject(TicketTypeAdapter.TicketType.class);
                        if (t != null) list.add(t);
                    }
                    ticketTypeAdapter.submit(list);

                    // 👉 TÍNH GIÁ MIN TỪ ticketTypes
                    if (list.isEmpty()) {
                        Double p = event.getPrice();
                        String priceText = (p == null || p == 0d)
                                ? getString(R.string.free)
                                : NumberFormat
                                .getNumberInstance(new Locale("vi", "VN"))
                                .format(p) + "₫";
                        binding.tvPrice.setText(priceText);
                        binding.tvBottomPrice.setText(priceText);
                        minTicketPrice = p;
                    } else {
                        double min = Double.MAX_VALUE;
                        boolean hasPaidTicket = false;

                        for (TicketTypeAdapter.TicketType t : list) {
                            if (t.price != null && t.price > 0) {
                                hasPaidTicket = true;
                                if (t.price < min) min = t.price;
                            }
                        }

                        if (!hasPaidTicket) {
                            binding.tvPrice.setText(getString(R.string.free));
                            binding.tvBottomPrice.setText(getString(R.string.free));
                            minTicketPrice = 0d;
                        } else {
                            minTicketPrice = min;
                            String formatted = NumberFormat
                                    .getNumberInstance(new Locale("vi", "VN"))
                                    .format(minTicketPrice) + " ₫";
                            binding.tvPrice.setText("Giá từ: " + formatted);
                            binding.tvBottomPrice.setText(formatted);
                            binding.tvBottomFromLabel.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    /** Cập nhật text nút Follow theo isFavorite */
    private void updateFollowButtonUi() {
        if (binding == null) return;
        binding.btnFollow.setText(isFavorite ? "Bỏ theo dõi" : "Theo dõi");
    }

    /** Check xem user hiện tại đã favorite event này chưa */
    private void checkFavoriteState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || eventId == null) {
            isFavorite = false;
            updateFollowButtonUi();
            return;
        }

        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .collection("favoriteEvents")
                .document(eventId)        // dùng eventId làm id doc
                .get()
                .addOnSuccessListener(doc -> {
                    isFavorite = doc.exists();
                    updateFollowButtonUi();
                })
                .addOnFailureListener(e -> {
                    isFavorite = false;
                    updateFollowButtonUi();
                });
    }

    private void loadRecommendedEvents() {
        // Nếu chưa có event thì thôi
        if (event == null) return;

        db.collection("events")
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    recommendedList.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (d.getId().equals(event.getId())) continue; // bỏ sự kiện hiện tại

                        Event e = d.toObject(Event.class);
                        if (e == null) continue;

                        RecommendedEvent re = new RecommendedEvent();
                        re.id = d.getId();
                        re.title = e.getTitle();
                        re.location = e.getLocation();
                        re.thumbnail = e.getThumbnail();

                        recommendedList.add(re);
                    }

                    if (recommendedList.isEmpty()) {
                        binding.layoutRecommended.setVisibility(View.GONE);
                    } else {
                        binding.layoutRecommended.setVisibility(View.VISIBLE);
                        recommendedAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    // lỗi thì ẩn luôn section
                    binding.layoutRecommended.setVisibility(View.GONE);
                });
    }

    /** Toggle follow / unfollow và lưu Firestore */
    private void toggleFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để theo dõi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (event == null || event.getId() == null) {
            Toast.makeText(this, "Chưa có thông tin sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        DocumentReference favRef = db.collection("users")
                .document(uid)
                .collection("favoriteEvents")
                .document(event.getId());

        if (!isFavorite) {
            // → Thêm vào yêu thích
            Map<String, Object> data = new HashMap<>();
            data.put("eventId", event.getId());
            data.put("title", event.getTitle());
            data.put("thumbnail", event.getThumbnail());
            data.put("location", event.getLocation());
            data.put("createdAt", FieldValue.serverTimestamp());

            favRef.set(data)
                    .addOnSuccessListener(unused -> {
                        isFavorite = true;
                        updateFollowButtonUi();
                        Toast.makeText(this,
                                "Đã thêm vào sự kiện yêu thích",
                                Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this,
                                "Lỗi: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });

        } else {
            // → Bỏ yêu thích
            favRef.delete()
                    .addOnSuccessListener(unused -> {
                        isFavorite = false;
                        updateFollowButtonUi();
                        Toast.makeText(this,
                                "Đã bỏ theo dõi sự kiện",
                                Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this,
                                "Lỗi: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // =============== WEATHER HELPERS ===============

    private void showWeatherLoading() {
        if (layoutWeatherContainer != null) {
            layoutWeatherContainer.setVisibility(View.VISIBLE);
        }
        if (layoutSkeletonWeather != null) {
            layoutSkeletonWeather.setVisibility(View.VISIBLE);
        }
        if (layoutWeather != null) {
            layoutWeather.setVisibility(View.INVISIBLE);
            layoutWeather.setAlpha(1f);
        }
        if (tvWeather != null) {
            tvWeather.setText("Đang tải dự báo...");
        }
    }

    private void showWeatherError(String message) {
        if (layoutSkeletonWeather != null) {
            layoutSkeletonWeather.setVisibility(View.GONE);
        }
        if (layoutWeather != null) {
            layoutWeather.setVisibility(View.VISIBLE);
            layoutWeather.setAlpha(1f);
        }
        if (ivWeatherIcon != null) {
            ivWeatherIcon.setImageResource(R.drawable.outline_cloud_24);
        }
        if (tvWeather != null) {
            tvWeather.setText(message);
        }
    }

    private void fadeInWeather() {
        if (layoutWeather == null || layoutSkeletonWeather == null) return;
        layoutSkeletonWeather.setVisibility(View.GONE);
        layoutWeather.setAlpha(0f);
        layoutWeather.setVisibility(View.VISIBLE);
        layoutWeather.animate()
                .alpha(1f)
                .setDuration(250)
                .setListener(null);
    }

    private void bindWeather(String description, double tempC) {
        if (ivWeatherIcon != null) {
            // Tạm thời dùng 1 icon chung, sau này nếu có icon riêng mưa/nắng thì map thêm
            ivWeatherIcon.setImageResource(R.drawable.outline_cloud_24);
        }

        if (tvWeather != null) {
            String text = String.format(
                    Locale.getDefault(),
                    "Dự báo thời tiết: %.0f°C, %s",
                    tempC,
                    description
            );
            tvWeather.setText(text);
        }
    }

    // =============== DỰ BÁO THỜI TIẾT CHO SỰ KIỆN ===============

    private void loadWeatherForecast(Event event) {
        if (tvWeather == null) return;

        if (event == null) {
            showWeatherError("Không có sự kiện để dự báo");
            return;
        }

        showWeatherLoading();

        if (event.getLocation() == null || event.getStartTime() == null) {
            showWeatherError("Không có đủ thông tin để dự báo");
            return;
        }

        long eventTimeMillis = event.getStartTime().toDate().getTime();

        // API free chỉ dự báo 5 ngày tới
        long fiveDaysMs = 5L * 24 * 3600 * 1000;
        if (eventTimeMillis > System.currentTimeMillis() + fiveDaysMs) {
            showWeatherError("Sự kiện còn quá xa để dự báo thời tiết");
            return;
        }

        String city = mapCityName(event.getLocation());

        String url = String.format(
                Locale.getDefault(),
                "https://api.openweathermap.org/data/2.5/forecast?q=%s&appid=%s&units=metric&lang=vi",
                city,
                WEATHER_API_KEY
        );

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        JSONArray list = response.getJSONArray("list");
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
                            double temp = bestForecast
                                    .getJSONObject("main")
                                    .getDouble("temp");
                            String desc = bestForecast
                                    .getJSONArray("weather")
                                    .getJSONObject(0)
                                    .getString("description");

                            bindWeather(desc, temp);
                            fadeInWeather();
                        } else {
                            showWeatherError("Không tìm thấy mốc thời gian phù hợp");
                        }
                    } catch (JSONException ex) {
                        showWeatherError("Không đọc được dữ liệu thời tiết");
                    }
                },
                error -> showWeatherError("Không tải được thời tiết")
        );

        volleyQueue.add(request);
    }

    private String mapCityName(String location) {
        if (location == null) return "Ho Chi Minh City";
        String loc = location.toLowerCase(Locale.ROOT);
        if (loc.contains("hcm") || loc.contains("hồ chí minh") || loc.contains("ho chi minh"))
            return "Ho Chi Minh City";
        if (loc.contains("hà nội") || loc.contains("ha noi"))
            return "Hanoi";
        if (loc.contains("đà nẵng") || loc.contains("da nang"))
            return "Da Nang";
        return "Ho Chi Minh City";
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

                    // cập nhật list gốc & render lại UI
                    allReviews.clear();
                    allReviews.addAll(reviews);
                    renderReviewsUi();
                })
                .addOnFailureListener(ex ->
                        Toast.makeText(this,
                                        "Không tải được đánh giá: " + ex.getMessage(),
                                        Toast.LENGTH_SHORT)
                                .show()
                );
    }

    /** Render lại UI cho phần review (ẩn/bớt, xem thêm, điểm TB, số lượng) */
    private void renderReviewsUi() {
        int count = allReviews.size();

        // Cập nhật tổng số đánh giá
        binding.tvReviewCount.setText(
                getString(R.string.review_count_fmt, count)
        );

        // Không có review
        if (count == 0) {
            binding.recyclerReviews.setVisibility(View.GONE);
            binding.tvEmptyReviews.setVisibility(View.VISIBLE);
            binding.btnMoreReviews.setVisibility(View.GONE);
            binding.ratingAverage.setRating(0f);
            binding.tvAverageRating.setText("0.0/5");
            return;
        }

        binding.recyclerReviews.setVisibility(View.VISIBLE);
        binding.tvEmptyReviews.setVisibility(View.GONE);

        // Tính average (dựa trên TẤT CẢ review)
        double total = 0;
        for (Review r : allReviews) {
            if (r.rating != null) total += r.rating;
        }
        double avg = count > 0 ? total / count : 0;
        binding.ratingAverage.setRating((float) avg);
        binding.tvAverageRating.setText(
                String.format(Locale.getDefault(), "%.1f/5", avg)
        );

        // List hiển thị: 3 cái đầu hoặc tất cả
        int max = reviewsExpanded
                ? count
                : Math.min(REVIEWS_COLLAPSED_LIMIT, count);

        List<Review> shown = new ArrayList<>(allReviews.subList(0, max));
        reviewAdapter.submit(shown);

        // Nút "Xem thêm / Ẩn bớt"
        if (count > REVIEWS_COLLAPSED_LIMIT) {
            binding.btnMoreReviews.setVisibility(View.VISIBLE);
            binding.btnMoreReviews.setText(reviewsExpanded ? "Ẩn đánh giá" : "Xem thêm đánh giá");
        } else {
            binding.btnMoreReviews.setVisibility(View.GONE);
        }
    }

    private void showRateDialog() {
        if (event == null || event.getId() == null) {
            Toast.makeText(this, "Chưa có thông tin sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_rate_review, null);

        android.widget.RatingBar rb = dialogView.findViewById(R.id.dialogRatingBar);
        EditText et = dialogView.findViewById(R.id.etDialogComment);

        new AlertDialog.Builder(this, R.style.RatingDialogTheme)
                .setTitle("Đánh giá sự kiện")
                .setView(dialogView)
                .setPositiveButton("Gửi", (dialog, which) -> {
                    float rating = rb.getRating();
                    String content = et.getText().toString().trim();
                    if (rating <= 0f) {
                        Toast.makeText(this, "Bạn chưa chọn số sao", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitReview(rating, content);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void submitReview(float rating, String content) {
        if (event == null || event.getId() == null) {
            Toast.makeText(this, "Thiếu thông tin sự kiện để gửi đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        String author = "Tôi";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                author = user.getDisplayName();
            } else if (user.getEmail() != null) {
                author = user.getEmail();
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("author", author);
        data.put("rating", rating);
        data.put("content", content);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("events")
                .document(event.getId())
                .collection("reviews")
                .add(data)
                .addOnSuccessListener(r -> {
                    Toast.makeText(this, "Đã gửi đánh giá!", Toast.LENGTH_SHORT).show();
                    loadReviews();
                })
                .addOnFailureListener(ex ->
                        Toast.makeText(this,
                                        "Lỗi gửi đánh giá: " + ex.getMessage(),
                                        Toast.LENGTH_SHORT)
                                .show()
                );
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }

    // ================== MENU BACK ==================

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Model review (tối giản)
    public static class Review {
        public String author;
        public String content;
        public Double rating;
        public Timestamp createdAt;

        public Review() {}
    }

    // Model sự kiện gợi ý
    private static class RecommendedEvent {
        String id;
        String title;
        String location;
        String thumbnail;
    }

    // ================== ADAPTER LOẠI VÉ (CHỈ HIỂN THỊ) ==================
    private static class TicketTypeAdapter extends
            RecyclerView.Adapter<TicketTypeAdapter.VH> {

        static class TicketType {
            public String name;
            public Double price;
            public Long quota;
            public Long sold;

            public TicketType() {}
        }

        private final List<TicketType> data = new ArrayList<>();
        private boolean eventEnded = false;   // flag sự kiện đã kết thúc

        public void submit(List<TicketType> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

        // gọi từ Activity sau khi biết event đã kết thúc hay chưa
        public void setEventEnded(boolean ended) {
            this.eventEnded = ended;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ticket_type, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position), eventEnded);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvQuota;
            View root;

            VH(@NonNull View itemView) {
                super(itemView);
                root   = itemView;
                tvName = itemView.findViewById(R.id.tvTicketName);
                tvPrice= itemView.findViewById(R.id.tvTicketPrice);
                tvQuota= itemView.findViewById(R.id.tvTicketQuota);
            }

            void bind(TicketType t, boolean eventEnded) {
                tvName.setText(t.name == null ? "Loại vé" : t.name);

                String priceStr;
                if (t.price == null || t.price == 0d) {
                    priceStr = "Miễn phí";
                } else {
                    priceStr = NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(t.price) + " ₫";
                }
                tvPrice.setText(priceStr);

                long quota = t.quota == null ? 0 : t.quota;
                long sold  = t.sold == null ? 0 : t.sold;
                long avail = quota - sold;
                boolean soldOut = quota > 0 && avail <= 0;

                // Chỉ hiện "Hết vé" khi hết – KHÔNG hiện "Còn xx vé"
                if (soldOut) {
                    tvQuota.setVisibility(View.VISIBLE);
                    tvQuota.setText("Hết vé");
                } else {
                    tvQuota.setVisibility(View.GONE);
                }

                // Làm mờ dòng nếu event kết thúc hoặc vé đó hết
                if (eventEnded || soldOut) {
                    root.setAlpha(0.4f);
                } else {
                    root.setAlpha(1f);
                }
            }
        }
    }

    // ================== ADAPTER SỰ KIỆN GỢI Ý ==================
    private class RecommendedAdapter extends RecyclerView.Adapter<RecommendedAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recommended_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(recommendedList.get(position));
        }

        @Override
        public int getItemCount() {
            return recommendedList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgThumb;
            TextView tvTitle, tvLocation;

            VH(@NonNull View itemView) {
                super(itemView);
                imgThumb = itemView.findViewById(R.id.imgThumb);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvLocation = itemView.findViewById(R.id.tvLocation);
            }

            void bind(RecommendedEvent e) {
                tvTitle.setText(e.title != null ? e.title : "");
                tvLocation.setText(e.location != null ? e.location : "");

                Glide.with(imgThumb.getContext())
                        .load(e.thumbnail)
                        .centerCrop()
                        .placeholder(R.drawable.sample_event)
                        .error(R.drawable.sample_event)
                        .into(imgThumb);

                itemView.setOnClickListener(v -> {
                    Intent i = new Intent(EventDetailActivity.this, EventDetailActivity.class);
                    i.putExtra(EXTRA_EVENT_ID, e.id);
                    startActivity(i);
                });
            }
        }
    }

    // ============ Helper ============

    private boolean isEventEnded(@Nullable Event e) {
        return e != null && e.isEnded();
    }

    private void updateBuyButtonState() {
        if (binding == null || event == null) return;

        if (isEventEnded(event)) {
            binding.btnBuyTicket.setText("Sự kiện đã kết thúc");
            binding.btnBuyTicket.setAlpha(0.6f);
            binding.btnBuyTicket.setEnabled(false);
        } else if (event.isSoldOut()) {
            binding.btnBuyTicket.setText("Đã hết vé");
            binding.btnBuyTicket.setAlpha(0.6f);
            binding.btnBuyTicket.setEnabled(false);
        } else {
            binding.btnBuyTicket.setText("Đặt vé");
            binding.btnBuyTicket.setAlpha(1f);
            binding.btnBuyTicket.setEnabled(true);
        }
    }
}
