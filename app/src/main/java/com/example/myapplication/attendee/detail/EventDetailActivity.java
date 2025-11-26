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
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
                binding.tvToggleTicketTypes.setText("Hiện loại vé");
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

        // Nút Đặt vé
        binding.btnBuyTicket.setOnClickListener(v -> {
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
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Lỗi tải sự kiện: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    loadWeatherForecast(event);   // 🔹 gọi dự báo thời tiết
                    loadReviews();
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
                .get()
                .addOnSuccessListener(snap -> {
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
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                        "Không tải được loại vé: " + e.getMessage(),
                                        Toast.LENGTH_SHORT)
                                .show()
                );
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
                    "Dự báo: %.0f°C, %s",
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
                    } catch (JSONException e) {
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
                    reviewAdapter.submit(reviews);

                    int count = reviews.size();
                    binding.tvReviewCount.setText(
                            getString(R.string.review_count_fmt, count)
                    );

                    double total = 0;
                    for (Review r : reviews) {
                        if (r.rating != null) total += r.rating;
                    }
                    double avg = count > 0 ? total / count : 0;
                    binding.ratingAverage.setRating((float) avg);
                    binding.tvAverageRating.setText(
                            String.format(Locale.getDefault(), "%.1f/5", avg)
                    );

                    if (count == 0) {
                        binding.recyclerReviews.setVisibility(View.GONE);
                        binding.tvEmptyReviews.setVisibility(View.VISIBLE);
                    } else {
                        binding.recyclerReviews.setVisibility(View.VISIBLE);
                        binding.tvEmptyReviews.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                        "Không tải được đánh giá: " + e.getMessage(),
                                        Toast.LENGTH_SHORT)
                                .show()
                );
    }

    // ================== ĐẶT VÉ ==================

    private void showBuyTicketDialog() {
        final int available = (event.getAvailableSeats() == null ? 0 : event.getAvailableSeats());

        if (available <= 0) {
            Toast.makeText(this, "Sự kiện đã hết vé", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xác định đơn giá
        final double unitPrice;
        if (minTicketPrice != null && minTicketPrice > 0) {
            unitPrice = minTicketPrice;
        } else if (event.getPrice() != null && event.getPrice() > 0) {
            unitPrice = event.getPrice();
        } else {
            unitPrice = 0d;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_buy_ticket, null);
        TextView tvEventTitle = dialogView.findViewById(R.id.tvEventTitle);
        TextView tvUnitPrice = dialogView.findViewById(R.id.tvUnitPrice);
        TextView tvTotalPrice = dialogView.findViewById(R.id.tvTotalPrice);
        EditText edtQuantity = dialogView.findViewById(R.id.edtQuantity);

        tvEventTitle.setText(event.getTitle() == null ? "" : event.getTitle());

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        String unitPriceStr;
        if (unitPrice <= 0) {
            unitPriceStr = getString(R.string.free);
        } else {
            unitPriceStr = nf.format(unitPrice) + " ₫";
        }
        tvUnitPrice.setText(unitPriceStr);

        edtQuantity.setText("1");
        if (unitPrice <= 0) {
            tvTotalPrice.setText(getString(R.string.free));
        } else {
            tvTotalPrice.setText(nf.format(unitPrice) + " ₫");
        }

        edtQuantity.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String txt = s.toString().trim();
                int q = 0;
                try {
                    q = Integer.parseInt(txt);
                } catch (NumberFormatException ignored) {
                }

                if (q <= 0) {
                    tvTotalPrice.setText("0 ₫");
                } else if (unitPrice <= 0) {
                    tvTotalPrice.setText(getString(R.string.free));
                } else {
                    double total = q * unitPrice;
                    tvTotalPrice.setText(nf.format(total) + " ₫");
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận đặt vé")
                .setView(dialogView)
                .setPositiveButton("Đặt vé", (dialog, which) -> {

                    String s = edtQuantity.getText().toString().trim();
                    if (s.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập số vé", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int quantity;
                    try {
                        quantity = Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Số vé không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (quantity <= 0) {
                        Toast.makeText(this, "Số vé phải > 0", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (quantity > available) {
                        Toast.makeText(this, "Không đủ vé, tối đa " + available, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    placeOrder(quantity);
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void placeOrder(int quantity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || event == null || event.getId() == null) {
            Toast.makeText(this, "Thiếu thông tin đặt vé", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        String eventDocId = event.getId();

        binding.btnBuyTicket.setEnabled(false);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            var eventRef = db.collection("events").document(eventDocId);
            var snapshot = transaction.get(eventRef);

            Integer avail = snapshot.getLong("availableSeats") != null
                    ? snapshot.getLong("availableSeats").intValue()
                    : 0;

            if (avail < quantity) {
                throw new RuntimeException("Không đủ vé, còn " + avail);
            }

            var ordersRef = db.collection("orders").document();
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("userId", userId);
            orderData.put("eventId", eventDocId);
            orderData.put("quantity", quantity);
            orderData.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(ordersRef, orderData);
            transaction.update(eventRef, "availableSeats", avail - quantity);

            return null;
        }).addOnSuccessListener(unused -> {
            binding.btnBuyTicket.setEnabled(true);

            String msg = "Bạn đã đặt " + quantity + " vé cho sự kiện \""
                    + (event.getTitle() == null ? "" : event.getTitle()) + "\"";
            new AlertDialog.Builder(this)
                    .setTitle("Đặt vé thành công")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        }).addOnFailureListener(e -> {
            binding.btnBuyTicket.setEnabled(true);
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("Không đủ vé")) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Lỗi đặt vé: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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
                        Toast.makeText(this, "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
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
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                        "Lỗi gửi đánh giá: " + e.getMessage(),
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

        public void submit(List<TicketType> list) {
            data.clear();
            if (list != null) data.addAll(list);
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
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvQuota;

            VH(@NonNull View itemView) {
                super(itemView);
                tvName  = itemView.findViewById(R.id.tvTicketName);
                tvPrice = itemView.findViewById(R.id.tvTicketPrice);
                tvQuota = itemView.findViewById(R.id.tvTicketQuota);
            }

            void bind(TicketType t) {
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

                if (quota > 0 && avail <= 0) {
                    tvQuota.setVisibility(View.VISIBLE);
                    tvQuota.setText("Hết vé");
                    tvQuota.setTextColor(
                            itemView.getResources().getColor(android.R.color.holo_red_dark)
                    );
                } else {
                    tvQuota.setVisibility(View.GONE);
                }
            }
        }
    }
}
