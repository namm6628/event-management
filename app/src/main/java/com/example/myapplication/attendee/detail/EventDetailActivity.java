package com.example.myapplication.attendee.detail;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private static final String WEATHER_API_KEY = "d217750b1e400fc2300711ab107183f2";
    private static final int REQ_PICK_REVIEW_MEDIA = 1001;

    private RequestQueue volleyQueue;

    private ActivityEventDetailBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReviewAdapter reviewAdapter;
    private Event event;

    private String eventId;
    private ListenerRegistration eventListener;

    private TicketTypeAdapter ticketTypeAdapter;
    private Double minTicketPrice = null;

    // Trạng thái loại vé / sơ đồ ghế
    private boolean hasSeatLayoutForEvent = false;
    private boolean ticketTypesLoaded = false;

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
    private Review currentUserReview = null;
    private String currentUserReviewDocId = null;

    // Recommended events
    private final List<RecommendedEvent> recommendedList = new ArrayList<>();
    private RecommendedAdapter recommendedAdapter;

    // Favorite state
    private boolean isFavorite = false;

    // Media chọn trong dialog review
    @Nullable
    private Uri pickedReviewMediaUri = null;
    @Nullable
    private ImageView dialogMediaPreview = null;

    // Member flag
    private boolean isMember = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) currentUserId = currentUser.getUid();

        // 🔹 load trạng thái member/vip của user
        loadCurrentUserMembership();

        volleyQueue = Volley.newRequestQueue(this);

        layoutWeatherContainer = binding.layoutWeatherContainer;
        layoutSkeletonWeather = binding.layoutSkeletonWeather;
        layoutWeather = binding.layoutWeather;
        ivWeatherIcon = binding.ivWeatherIcon;
        tvWeather = binding.tvWeather;

        // Mô tả thu gọn
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

        // Viết đánh giá
        binding.btnWriteReview.setOnClickListener(v -> onWriteReviewClicked());

        // Ticket types
        ticketTypeAdapter = new TicketTypeAdapter();
        binding.recyclerTicketTypes.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        );
        binding.recyclerTicketTypes.setAdapter(ticketTypeAdapter);
        binding.tvTicketDateTime.setText(getString(R.string.ticket_info_header));

        binding.tvToggleTicketTypes.setOnClickListener(v -> {
            if (binding.recyclerTicketTypes.getVisibility() == View.VISIBLE) {
                binding.recyclerTicketTypes.setVisibility(View.GONE);
                binding.tvToggleTicketTypes.setText("Hiện loại vé");
            } else {
                binding.recyclerTicketTypes.setVisibility(View.VISIBLE);
                binding.tvToggleTicketTypes.setText("Ẩn loại vé");
            }
        });

        // Lấy eventId
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
        binding.btnMoreReviews.setVisibility(View.GONE);

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

        // Follow
        binding.btnFollow.setOnClickListener(v -> toggleFavorite());
        updateFollowButtonUi();

        // Map
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

        // Reviews adapter
        reviewAdapter = new ReviewAdapter();
        reviewAdapter.setCurrentUserId(currentUserId); // ⭐ cho adapter biết user hiện tại
        reviewAdapter.setActionListener(new ReviewAdapter.ReviewActionListener() { // ⭐ handle Sửa/Xoá
            @Override
            public void onEdit(@NonNull Review r) {
                // Gán review hiện tại rồi mở dialog sửa
                currentUserReview = r;
                currentUserReviewDocId = r.id;
                showRateDialog();
            }

            @Override
            public void onDelete(@NonNull Review r) {
                if (event == null || event.getId() == null) {
                    Toast.makeText(EventDetailActivity.this,
                            "Chưa có thông tin sự kiện",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(EventDetailActivity.this)
                        .setTitle("Xoá đánh giá")
                        .setMessage("Bạn có chắc muốn xoá đánh giá này?")
                        .setPositiveButton("Xoá", (dialog, which) -> {
                            db.collection("events")
                                    .document(event.getId())
                                    .collection("reviews")
                                    .document(r.id)
                                    .delete()
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(EventDetailActivity.this,
                                                "Đã xoá đánh giá",
                                                Toast.LENGTH_SHORT).show();

                                        if (currentUserReviewDocId != null
                                                && currentUserReviewDocId.equals(r.id)) {
                                            currentUserReview = null;
                                            currentUserReviewDocId = null;
                                        }
                                        loadReviews();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(EventDetailActivity.this,
                                                    "Lỗi xoá: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });

        binding.recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReviews.setAdapter(reviewAdapter);

        // Xem thêm review
        binding.btnMoreReviews.setOnClickListener(v -> {
            reviewsExpanded = !reviewsExpanded;
            renderReviewsUi();
        });

        // Recommended
        recommendedAdapter = new RecommendedAdapter();
        binding.recyclerRecommendedEvents.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        binding.recyclerRecommendedEvents.setAdapter(recommendedAdapter);

        // Đặt vé: nếu có sơ đồ ghế → chọn ghế, nếu không → chọn loại vé
        binding.btnBuyTicket.setOnClickListener(v -> {
            // check điều kiện trước
            if (!validateBeforeBuy()) return;

            // mở dialog chống bot
            showRotateVerifyDialog(this::performBuyTicket);
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
                    } catch (Exception ignored) {}

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
                        } catch (Exception ignored) {}
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

                    binding.recyclerTicketTypes.setVisibility(View.VISIBLE);
                    binding.tvToggleTicketTypes.setVisibility(View.VISIBLE);

                    ticketTypeAdapter.setEventEnded(isEventEnded(event));

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
                    boolean detectedSeatLayout = false;   // <== check có field seats hay không

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketTypeAdapter.TicketType t =
                                d.toObject(TicketTypeAdapter.TicketType.class);
                        if (t != null) list.add(t);

                        // kiểm tra xem ticket type này có cấu hình ghế không
                        java.util.List<String> seats =
                                (java.util.List<String>) d.get("seats");
                        if (seats != null && !seats.isEmpty()) {
                            detectedSeatLayout = true;
                        }
                    }

                    ticketTypesLoaded = true;
                    hasSeatLayoutForEvent = detectedSeatLayout;

                    ticketTypeAdapter.submit(list);

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

    // ======= FOLLOW =======

    private void updateFollowButtonUi() {
        if (binding == null) return;
        binding.btnFollow.setText(isFavorite ? "Bỏ theo dõi" : "Theo dõi");
    }

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
                .document(eventId)
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
        if (event == null) return;

        db.collection("events")
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    recommendedList.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (d.getId().equals(event.getId())) continue;

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
                .addOnFailureListener(e -> binding.layoutRecommended.setVisibility(View.GONE));
    }

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
                    .addOnFailureListener(e -> Toast.makeText(this,
                            "Lỗi: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());

        } else {
            favRef.delete()
                    .addOnSuccessListener(unused -> {
                        isFavorite = false;
                        updateFollowButtonUi();
                        Toast.makeText(this,
                                "Đã bỏ theo dõi sự kiện",
                                Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this,
                            "Lỗi: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());
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
            ivWeatherIcon.setImageResource(R.drawable.outline_cloud_24);
        }

        if (tvWeather != null) {
            String text = String.format(
                    Locale.getDefault(),
                    "Dự báo: %.0f°C, %s",
                    tempC,
                    description
            );
            tvWeather.setText(text);
        }
    }

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

    // ================== REVIEWS ==================

    private void loadReviews() {
        if (event == null || event.getId() == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : null;

        // ⭐ cập nhật lại currentUserId cho adapter phòng trường hợp login/logout
        currentUserId = uid;
        reviewAdapter.setCurrentUserId(uid);

        db.collection("events").document(event.getId())
                .collection("reviews")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    allReviews.clear();
                    currentUserReview = null;
                    currentUserReviewDocId = null;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Review r = d.toObject(Review.class);
                        if (r == null) continue;
                        r.id = d.getId();
                        allReviews.add(r);

                        if (uid != null && uid.equals(r.userId)) {
                            currentUserReview = r;
                            currentUserReviewDocId = r.id;
                        }
                    }

                    reviewsExpanded = false;
                    renderReviewsUi();
                })
                .addOnFailureListener(ex ->
                        Toast.makeText(this,
                                        "Không tải được đánh giá: " + ex.getMessage(),
                                        Toast.LENGTH_SHORT)
                                .show()
                );
    }

    private void renderReviewsUi() {
        int count = allReviews.size();

        binding.tvReviewCount.setText(
                getString(R.string.review_count_fmt, count)
        );

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

        double total = 0;
        for (Review r : allReviews) {
            if (r.rating != null) total += r.rating;
        }
        double avg = count > 0 ? total / count : 0;
        binding.ratingAverage.setRating((float) avg);
        binding.tvAverageRating.setText(
                String.format(Locale.getDefault(), "%.1f/5", avg)
        );

        int max = reviewsExpanded
                ? count
                : Math.min(REVIEWS_COLLAPSED_LIMIT, count);

        List<Review> shown = new ArrayList<>(allReviews.subList(0, max));
        reviewAdapter.submit(shown);

        if (count > REVIEWS_COLLAPSED_LIMIT) {
            binding.btnMoreReviews.setVisibility(View.VISIBLE);
            binding.btnMoreReviews.setText(reviewsExpanded ? "Ẩn bớt" : "Xem thêm");
        } else {
            binding.btnMoreReviews.setVisibility(View.GONE);
        }
    }

    // CLICK "VIẾT ĐÁNH GIÁ"
    private void onWriteReviewClicked() {
        if (event == null || event.getId() == null) {
            Toast.makeText(this, "Chưa có thông tin sự kiện", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để viết đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isEventEnded(event)) {
            checkUserCanReview(can -> {
                if (!can) {
                    Toast.makeText(this,
                            "Bạn chưa mua vé cho sự kiện này",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            "Bạn chỉ có thể viết đánh giá khi sự kiện kết thúc",
                            Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        checkUserCanReview(can -> {
            if (!can) {
                Toast.makeText(this,
                        "Bạn chưa mua vé cho sự kiện này",
                        Toast.LENGTH_SHORT).show();
            } else {
                showRateDialog();
            }
        });
    }

    private void checkUserCanReview(CheckCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || eventId == null) {
            callback.onResult(false);
            return;
        }

        String uid = user.getUid();

        db.collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("eventId", eventId)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> callback.onResult(!snap.isEmpty()))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    private interface CheckCallback {
        void onResult(boolean ok);
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
        android.widget.Button btnPickMedia = dialogView.findViewById(R.id.btnPickMedia);
        ImageView imgMediaPreview = dialogView.findViewById(R.id.imgMediaPreview);

        // Cho onActivityResult biết ImageView nào dùng để preview
        dialogMediaPreview = imgMediaPreview;
        pickedReviewMediaUri = null; // reset

        // fill dữ liệu cũ nếu đã có review
        if (currentUserReview != null) {
            if (currentUserReview.rating != null) {
                rb.setRating(currentUserReview.rating.floatValue());
            }
            if (currentUserReview.content != null) {
                et.setText(currentUserReview.content);
                et.setSelection(currentUserReview.content.length());
            }
            if (currentUserReview.mediaUrl != null && !currentUserReview.mediaUrl.isEmpty()) {
                imgMediaPreview.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(currentUserReview.mediaUrl)
                        .placeholder(R.drawable.sample_event)
                        .error(R.drawable.sample_event)
                        .into(imgMediaPreview);
            } else {
                imgMediaPreview.setVisibility(View.GONE);
            }
        } else {
            imgMediaPreview.setVisibility(View.GONE);
        }

        // Chọn media từ album
        btnPickMedia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("*/*");
            String[] mimeTypes = new String[]{"image/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, REQ_PICK_REVIEW_MEDIA);
        });

        new AlertDialog.Builder(this, R.style.RatingDialogTheme)
                .setTitle(currentUserReview == null
                        ? "Đánh giá sự kiện"
                        : "Chỉnh sửa đánh giá")
                .setView(dialogView)
                .setPositiveButton("Gửi", (dialog, which) -> {
                    float rating = rb.getRating();
                    String content = et.getText().toString().trim();

                    if (rating <= 0f) {
                        Toast.makeText(this, "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (pickedReviewMediaUri != null) {
                        uploadReviewMediaAndSubmit(rating, content);
                    } else {
                        String oldMedia = currentUserReview != null ? currentUserReview.mediaUrl : null;
                        submitReview(rating, content, oldMedia);
                    }

                    dialogMediaPreview = null;
                })
                .setNegativeButton("Hủy", (d, w) -> {
                    pickedReviewMediaUri = null;
                    dialogMediaPreview = null;
                })
                .show();
    }

    private void uploadReviewMediaAndSubmit(float rating, String content) {
        if (pickedReviewMediaUri == null) {
            submitReview(rating, content, null);
            return;
        }
        if (event == null || event.getId() == null) {
            submitReview(rating, content, null);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = "reviewMedia/" + event.getId() + "/" + user.getUid() + "_" + System.currentTimeMillis();
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);

        ref.putFile(pickedReviewMediaUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    String mediaUrl = uri.toString();
                    submitReview(rating, content, mediaUrl);
                    pickedReviewMediaUri = null;
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Upload ảnh/video thất bại, gửi đánh giá không kèm media",
                            Toast.LENGTH_SHORT).show();
                    submitReview(rating, content, null);
                    pickedReviewMediaUri = null;
                });
    }

    private void submitReview(float rating, String content, @Nullable String mediaUrl) {
        if (event == null || event.getId() == null) {
            Toast.makeText(this, "Thiếu thông tin sự kiện để gửi đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để đánh giá", Toast.LENGTH_SHORT).show();
            return;
        }

        String author;
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            author = user.getDisplayName();
        } else if (user.getEmail() != null) {
            author = user.getEmail();
        } else {
            author = "Tôi";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("author", author);
        data.put("rating", rating);
        data.put("content", content);
        data.put("userId", user.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            data.put("mediaUrl", mediaUrl);
        }

        if (currentUserReviewDocId != null) {
            db.collection("events")
                    .document(event.getId())
                    .collection("reviews")
                    .document(currentUserReviewDocId)
                    .set(data)
                    .addOnSuccessListener(r -> {
                        Toast.makeText(this, "Đã cập nhật đánh giá!", Toast.LENGTH_SHORT).show();
                        loadReviews();
                    })
                    .addOnFailureListener(ex ->
                            Toast.makeText(this,
                                            "Lỗi gửi đánh giá: " + ex.getMessage(),
                                            Toast.LENGTH_SHORT)
                                    .show()
                    );
        } else {
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
    }

    // Nhận kết quả pick media từ album
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_REVIEW_MEDIA && resultCode == RESULT_OK && data != null) {
            pickedReviewMediaUri = data.getData();
            if (pickedReviewMediaUri != null && dialogMediaPreview != null) {
                dialogMediaPreview.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(pickedReviewMediaUri)
                        .placeholder(R.drawable.sample_event)
                        .error(R.drawable.sample_event)
                        .into(dialogMediaPreview);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Model review
    public static class Review {
        public String id;
        public String author;
        public String content;
        public Double rating;
        public Timestamp createdAt;
        public String userId;
        public String mediaUrl;

        public Review() {}
    }

    // Model sự kiện gợi ý
    private static class RecommendedEvent {
        String id;
        String title;
        String location;
        String thumbnail;
    }

    // ================== ADAPTER LOẠI VÉ ==================
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
        private boolean eventEnded = false;

        public void submit(List<TicketType> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
        }

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

                if (soldOut) {
                    tvQuota.setVisibility(View.VISIBLE);
                    tvQuota.setText("Hết vé");
                } else {
                    tvQuota.setVisibility(View.GONE);
                }

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
            // Đổi text dựa vào có sơ đồ ghế hay không
            if (event.hasSeatLayout()) {
                binding.btnBuyTicket.setText("Chọn chỗ ngồi");
            } else {
                binding.btnBuyTicket.setText("Chọn vé");
            }
            binding.btnBuyTicket.setAlpha(1f);
            binding.btnBuyTicket.setEnabled(true);
        }
    }

    /** Mở màn chọn ghế */
    private void openSeatSelection(String eventId,
                                   String eventTitle,
                                   int quantity,
                                   double totalPrice,
                                   String ticketType,
                                   String ticketNames) {

        Intent intent = new Intent(this, SeatSelectionActivity.class);
        intent.putExtra("eventId", eventId);
        intent.putExtra("eventTitle", eventTitle);

        // base quantity / price (chủ yếu dùng fallback nếu không có sơ đồ ghế)
        intent.putExtra("quantity", quantity);
        intent.putExtra("totalPrice", totalPrice);

        intent.putExtra("ticketType", ticketType);
        intent.putExtra("ticketNames", ticketNames);

        // member flag cho seat-pricing
        intent.putExtra("isMember", isMember);

        // giới hạn tối đa ghế
        intent.putExtra("maxSeats", 10);

        startActivity(intent);
    }

    /** Mở màn chọn loại vé / số lượng (event KHÔNG có sơ đồ)**/
    private void openTicketQuantitySelection() {
        Intent i = new Intent(this, SelectTicketQuantityActivity.class);
        i.putExtra("eventId", eventId);
        i.putExtra("eventTitle", event != null ? event.getTitle() : "");
        i.putExtra("isMember", isMember);
        startActivity(i);
    }

    /** Check điều kiện cơ bản trước khi cho đặt vé */
    private boolean validateBeforeBuy() {
        if (event == null) {
            Toast.makeText(this, "Chưa tải xong dữ liệu sự kiện", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (isEventEnded(event)) {
            Toast.makeText(this,
                    "Sự kiện đã kết thúc, không thể đặt vé nữa.",
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        if (event.isSoldOut()) {
            Toast.makeText(this,
                    "Sự kiện đã hết vé.",
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        if (!ticketTypesLoaded) {
            Toast.makeText(this,
                    "Đang tải thông tin vé, vui lòng thử lại sau giây lát.",
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        return true;
    }

    /** Sau khi xác minh xong mới cho user chọn vé / chọn chỗ */
    private void performBuyTicket() {
        if (hasSeatLayoutForEvent) {
            int quantity = 1;

            double pricePerTicket = 0d;
            if (minTicketPrice != null) {
                pricePerTicket = minTicketPrice;
            } else if (event.getPrice() != null) {
                pricePerTicket = event.getPrice();
            }
            double totalPrice = pricePerTicket * quantity;

            String defaultTicketName = "Vé tham dự";

            openSeatSelection(
                    eventId,
                    event.getTitle(),
                    quantity,
                    totalPrice,
                    defaultTicketName,
                    defaultTicketName
            );
        } else {
            openTicketQuantitySelection();
        }
    }

    private void showRotateVerifyDialog(Runnable onVerified) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_verify_user_rotate);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageView imgCat = dialog.findViewById(R.id.imgAvatar);
        SeekBar seekRotate = dialog.findViewById(R.id.seekRotate);

        // Ảnh để xoay – cậu có thể đổi thành avatar của user nếu muốn
        String[] verifyImages = {
                "https://firebasestorage.googleapis.com/v0/b/eventmanagement-8d9c4.firebasestorage.app/o/event1.jpg?alt=media&token=faeea74f-e925-4013-8ecc-3b0173bcf973",
                "https://firebasestorage.googleapis.com/v0/b/eventmanagement-8d9c4.firebasestorage.app/o/event2.jpg?alt=media&token=a38e56de-5c40-4b11-bda8-e62634fd540c",
                "https://firebasestorage.googleapis.com/v0/b/eventmanagement-8d9c4.firebasestorage.app/o/z7273876249111_9112a04be27e80bd246e944147d2acfe.jpg?alt=media&token=d5ce97d1-c56f-4913-bcb7-701160a0d483"
        };

        String randomImg = verifyImages[new Random().nextInt(verifyImages.length)];

        Glide.with(this)
                .load(randomImg)
                .into(imgCat);

        // Góc xoay ban đầu ngẫu nhiên (tránh 0° để khỏi trùng)
        final int originalRotation = new Random().nextInt(4) * 90; // 0,90,180,270
        imgCat.setRotation(originalRotation);

        seekRotate.setProgress(0);

        seekRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // progress 0..360 → cộng vào góc ban đầu
                float currentAngle = (originalRotation + progress) % 360;
                imgCat.setRotation(currentAngle);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float finalAngle = imgCat.getRotation() % 360;
                if (finalAngle < 0) finalAngle += 360;

                // Nếu góc gần 0° (±10°) thì coi như đứng thẳng
                boolean ok = (finalAngle <= 10 || finalAngle >= 350);

                if (ok) {
                    dialog.dismiss();
                    if (onVerified != null) onVerified.run();
                } else {
                    // reset về ban đầu
                    seekBar.setProgress(0);
                    imgCat.setRotation(originalRotation);
                    Toast.makeText(EventDetailActivity.this,
                            "Xoay con mèo cho nó nhìn bạn rồi thả tay nhé!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }

    private void loadCurrentUserMembership() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            isMember = false;
            return;
        }
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String tier = doc.getString("membershipTier");
                        isMember = tier != null
                                && (tier.equalsIgnoreCase("member")
                                || tier.equalsIgnoreCase("vip"));
                    } else {
                        isMember = false;
                    }
                })
                .addOnFailureListener(e -> {
                    isMember = false;
                });
    }

}
