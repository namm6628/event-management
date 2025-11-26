package com.example.myapplication.attendee.detail;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.common.model.TicketType;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;


public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private ActivityEventDetailBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ReviewAdapter reviewAdapter;
    private Event event; // sẽ được set sau khi fetch xong

    private String eventId;
    private ListenerRegistration eventListener;

    private TicketTypeAdapter ticketTypeAdapter;

    private Double minTicketPrice = null;

    private boolean isDescriptionExpanded = false;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());

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


        setContentView(binding.getRoot());

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


        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        // Toolbar

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.event_detail_title); // cố định
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
        binding.recyclerReviews.setVisibility(android.view.View.GONE);
        binding.tvEmptyReviews.setVisibility(android.view.View.VISIBLE);
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

        // Mở bản đồ
        binding.btnOpenMap.setOnClickListener(v -> {
            String q = null;
            if (event != null) {
                // Ưu tiên địa chỉ chi tiết
                if (event.getAddressDetail() != null && !event.getAddressDetail().isEmpty()) {
                    q = event.getAddressDetail();
                } else {
                    q = event.getLocation(); // fallback
                }
            }

            if (q == null || q.isEmpty()) {
                Toast.makeText(this, "Chưa có địa chỉ để mở bản đồ", Toast.LENGTH_SHORT).show();
            } else {
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(q));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });


        // RecyclerView reviews
        reviewAdapter = new ReviewAdapter();
        binding.recyclerReviews.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReviews.setAdapter(reviewAdapter);

        // 🎫 Nút Đặt vé – PHẢI nằm trong onCreate
        // lấy eventId từ Intent 1 lần
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

// ...

        binding.btnBuyTicket.setOnClickListener(v -> {
            // test xem có nhận click chưa


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
                            .centerCrop()                     // đảm bảo luôn phủ kín khung
                            .placeholder(R.drawable.sample_event)
                            .error(R.drawable.sample_event)
                            .into(binding.ivCover);


                    // Text
                    binding.tvTitle.setText(event.getTitle() == null ? "" : event.getTitle());
                    binding.tvArtist.setText(
                            event.getArtist() == null ? getString(R.string.artist_unknown) : event.getArtist()
                    );
                    binding.tvVenue.setText(event.getLocation() == null ? "" : event.getLocation());
                    // Địa chỉ chi tiết (field mới)
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
                                // Không có endTime thì chỉ hiện giờ bắt đầu
                                timeText = startHour + ", " + day;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    binding.tvTime.setText(timeText);
                    binding.tvTicketDateTime.setText(timeText);

                    String desc = event.getDescription();
                    if (desc == null || desc.trim().isEmpty()) {
                        binding.tvDescription.setText("");
                    } else {
                        binding.tvDescription.setText(desc);
                    }

                    Double p = event.getPrice();
                    String priceText = (p == null || p == 0d)
                            ? getString(R.string.free)
                            : NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(p) + "₫";
                    binding.tvPrice.setText(priceText);
                    binding.tvBottomPrice.setText(priceText);


                    loadTicketTypes();


                    // Load reviews
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
                        TicketTypeAdapter.TicketType t = d.toObject(TicketTypeAdapter.TicketType.class);
                        if (t != null) list.add(t);
                    }
                    ticketTypeAdapter.submit(list);

                    // 👉 TÍNH GIÁ MIN TỪ ticketTypes
                    if (list.isEmpty()) {
                        // Không có loại vé: giữ logic cũ
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
                            // Tất cả vé free
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
                        Toast.makeText(this, "Không tải được loại vé: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
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
                        binding.recyclerReviews.setVisibility(android.view.View.GONE);
                        binding.tvEmptyReviews.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.recyclerReviews.setVisibility(android.view.View.VISIBLE);
                        binding.tvEmptyReviews.setVisibility(android.view.View.GONE);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không tải được đánh giá: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ================== ĐẶT VÉ ==================

    private void showBuyTicketDialog() {

        final int available = (event.getAvailableSeats() == null ? 0 : event.getAvailableSeats());

        if (available <= 0) {
            Toast.makeText(this, "Sự kiện đã hết vé", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xác định đơn giá: ưu tiên giá min từ ticketTypes, nếu không có thì dùng event.getPrice()
        final double unitPrice;
        if (minTicketPrice != null && minTicketPrice > 0) {
            unitPrice = minTicketPrice;
        } else if (event.getPrice() != null && event.getPrice() > 0) {
            unitPrice = event.getPrice();
        } else {
            unitPrice = 0d;
        }

        // Inflate view custom
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_buy_ticket, null);
        TextView tvEventTitle = dialogView.findViewById(R.id.tvEventTitle);
        TextView tvUnitPrice = dialogView.findViewById(R.id.tvUnitPrice);
        TextView tvTotalPrice = dialogView.findViewById(R.id.tvTotalPrice);
        EditText edtQuantity = dialogView.findViewById(R.id.edtQuantity);

        tvEventTitle.setText(event.getTitle() == null ? "" : event.getTitle());

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        // Đơn giá hiển thị
        String unitPriceStr;
        if (unitPrice <= 0) {
            unitPriceStr = getString(R.string.free);
        } else {
            unitPriceStr = nf.format(unitPrice) + " ₫";  // ✅ format đúng: truyền vào double
        }
        tvUnitPrice.setText(unitPriceStr);

        // Mặc định 1 vé
        edtQuantity.setText("1");
        if (unitPrice <= 0) {
            tvTotalPrice.setText(getString(R.string.free));
        } else {
            tvTotalPrice.setText(nf.format(unitPrice) + " ₫");
        }

        // Cập nhật tổng tiền khi user đổi số vé
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
                    double total = q * unitPrice;                 // ✅ Number
                    tvTotalPrice.setText(nf.format(total) + " ₫"); // ✅ truyền Number vào format
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

            // listener realtime sẽ tự update availableSeats
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

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
        }
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

        public Review() {
        }
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
