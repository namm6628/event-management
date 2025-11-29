package com.example.myapplication.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrganizerHomeFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;

    // thống kê
    private TextView tvOrgName, tvTotalRevenue, tvTotalTickets, tvTotalOrders,
            tvTotalEvents, tvRevenueToday, tvRevenueMonth, tvEventsThisMonth,
            tvAvgFillRate, tvTopEvent, tvOrderStatus;

    private OrganizerEventAdapter adapter;
    private FirebaseFirestore db;

    private MaterialButtonToggleGroup groupFilter;

    // lưu toàn bộ sự kiện của BTC, lọc ở client
    private final List<Event> allEvents = new ArrayList<>();

    private enum FilterType {
        UPCOMING,   // Sắp diễn ra: từ ngày mai trở đi
        TODAY,      // Chuẩn bị diễn ra: trong ngày hôm nay
        FINISHED    // Đã kết thúc: endTime < now
    }

    private FilterType currentFilter = FilterType.UPCOMING;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_organizer_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        db = FirebaseFirestore.getInstance();

        rv = v.findViewById(R.id.recyclerMyEvents);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        // view thống kê
        tvOrgName        = v.findViewById(R.id.tvOrgName);
        tvTotalRevenue   = v.findViewById(R.id.tvTotalRevenue);
        tvTotalTickets   = v.findViewById(R.id.tvTotalTickets);
        tvTotalOrders    = v.findViewById(R.id.tvTotalOrders);
        tvTotalEvents    = v.findViewById(R.id.tvTotalEvents);
        tvRevenueToday   = v.findViewById(R.id.tvRevenueToday);
        tvRevenueMonth   = v.findViewById(R.id.tvRevenueMonth);
        tvEventsThisMonth= v.findViewById(R.id.tvEventsThisMonth);
        tvAvgFillRate    = v.findViewById(R.id.tvAvgFillRate);
        tvTopEvent       = v.findViewById(R.id.tvTopEvent);
        tvOrderStatus    = v.findViewById(R.id.tvOrderStatus);

        // Filter group
        groupFilter = v.findViewById(R.id.groupFilter);

        // set tên BTC từ user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String name = currentUser.getDisplayName();
            if (name == null || name.isEmpty()) name = currentUser.getEmail();
            if (name == null || name.isEmpty()) name = "Ban tổ chức";
            tvOrgName.setText(name);
        } else {
            tvOrgName.setText("Ban tổ chức");
        }

        adapter = new OrganizerEventAdapter(new OrganizerEventAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), CreateEventActivity.class);
                it.putExtra(com.example.myapplication.attendee.detail.EventDetailActivity.EXTRA_EVENT_ID, e.getId());
                startActivity(it);
            }

            @Override
            public void onViewAttendees(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), OrganizerAttendeesActivity.class);
                it.putExtra(OrganizerAttendeesActivity.EXTRA_EVENT_ID, e.getId());
                startActivity(it);
            }

            @Override
            public void onBroadcast(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), OrganizerBroadcastActivity.class);
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_ID, e.getId());
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_TITLE, e.getTitle());
                startActivity(it);
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // 2 ô dashboard
        View cardCreate  = v.findViewById(R.id.btnCreateEvent);
        View cardProfile = v.findViewById(R.id.btnProfile);

        setupCardClick(cardCreate, () ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.organizerCreateEventFragment)
        );

        setupCardClick(cardProfile, () ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.organizerProfileFragment)
        );

        // Filter mặc định + listener
        if (groupFilter != null) {
            groupFilter.check(R.id.btnUpcoming);
            groupFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnUpcoming) {
                    currentFilter = FilterType.UPCOMING;
                } else if (checkedId == R.id.btnToday) {
                    currentFilter = FilterType.TODAY;
                } else if (checkedId == R.id.btnFinished) {
                    currentFilter = FilterType.FINISHED;
                }
                applyFilter(currentFilter);
            });
        }

        loadStats();
        loadAllEvents();
    }

    /** Animation nhỏ + run action sau khi click */
    private void setupCardClick(View card, Runnable action) {
        if (card == null) return;
        card.setOnClickListener(v -> {
            v.animate().cancel();
            v.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(80)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(80)
                                .withEndAction(action::run)
                                .start();
                    })
                    .start();
        });
    }

    /** Load thống kê từ collection orders */
    private void loadStats() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvTotalRevenue.setText("Doanh thu: 0 đ");
            tvTotalTickets.setText("Tổng vé đã bán: 0");
            tvTotalOrders.setText("Tổng lượt mua: 0");
            tvRevenueToday.setText("Doanh thu hôm nay: 0 đ");
            tvRevenueMonth.setText("Doanh thu tháng này: 0 đ");
            tvOrderStatus.setText("Đơn đã thanh toán: 0 | Đang chờ: 0 | Khác: 0");
            return;
        }

        String uid = user.getUid();

        // mốc thời gian
        Calendar cal = Calendar.getInstance();
        // startOfToday
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfToday = cal.getTime();

        // startOfTomorrow
        cal.add(Calendar.DAY_OF_YEAR, 1);
        Date startOfTomorrow = cal.getTime();

        // startOfMonth
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfMonth = cal.getTime();

        // startOfNextMonth
        cal.add(Calendar.MONTH, 1);
        Date startOfNextMonth = cal.getTime();

        db.collection("orders")
                .whereEqualTo("ownerId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    long totalOrdersPaid = 0L;
                    long totalTicketsPaid = 0L;
                    long totalAmountPaid = 0L;

                    long revenueToday = 0L;
                    long revenueThisMonth = 0L;

                    long paidOrders = 0L;
                    long pendingOrders = 0L;
                    long otherOrders = 0L;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String status = d.getString("status");
                        if (status == null) status = "";

                        // phân loại trạng thái đơn
                        if (status.equalsIgnoreCase("paid")) {
                            paidOrders++;
                        } else if (status.equalsIgnoreCase("pending")) {
                            pendingOrders++;
                        } else if (status.equalsIgnoreCase("cancelled")
                                || status.equalsIgnoreCase("canceled")) {
                            otherOrders++;
                        } else {
                            if (!status.isEmpty()) otherOrders++;
                        }

                        Number amountNum = (Number) d.get("totalAmount");
                        Long qty = d.getLong("totalTickets");
                        Timestamp createdTs = d.getTimestamp("createdAt");
                        Date createdDate = createdTs != null ? createdTs.toDate() : null;

                        // chỉ tính thống kê doanh thu cho đơn đã thanh toán
                        if (!status.equalsIgnoreCase("paid")) {
                            continue;
                        }

                        if (qty != null) totalTicketsPaid += qty;
                        if (amountNum != null) {
                            long amount = amountNum.longValue();
                            totalAmountPaid += amount;

                            if (createdDate != null) {
                                // hôm nay
                                if (!createdDate.before(startOfToday)
                                        && createdDate.before(startOfTomorrow)) {
                                    revenueToday += amount;
                                }
                                // trong tháng này
                                if (!createdDate.before(startOfMonth)
                                        && createdDate.before(startOfNextMonth)) {
                                    revenueThisMonth += amount;
                                }
                            }
                        }

                        totalOrdersPaid++;
                    }

                    NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));

                    tvTotalRevenue.setText("Doanh thu: " + nf.format(totalAmountPaid) + " đ");
                    tvTotalTickets.setText("Tổng vé đã bán: " + totalTicketsPaid);
                    tvTotalOrders.setText("Tổng lượt mua: " + totalOrdersPaid);

                    tvRevenueToday.setText("Doanh thu hôm nay: " + nf.format(revenueToday) + " đ");
                    tvRevenueMonth.setText("Doanh thu tháng này: " + nf.format(revenueThisMonth) + " đ");

                    long other = otherOrders;
                    tvOrderStatus.setText(
                            "Đơn đã thanh toán: " + paidOrders +
                                    " | Đang chờ: " + pendingOrders +
                                    " | Khác/Hủy: " + other
                    );
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Lỗi tải thống kê: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    tvTotalRevenue.setText("Doanh thu: 0 đ");
                    tvTotalTickets.setText("Tổng vé đã bán: 0");
                    tvTotalOrders.setText("Tổng lượt mua: 0");
                    tvRevenueToday.setText("Doanh thu hôm nay: 0 đ");
                    tvRevenueMonth.setText("Doanh thu tháng này: 0 đ");
                    tvOrderStatus.setText("Đơn đã thanh toán: 0 | Đang chờ: 0 | Khác: 0");
                });
    }

    /** Lấy toàn bộ event của BTC, không filter ngày trong query */
    private void loadAllEvents() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập lại", Toast.LENGTH_SHORT).show();
            adapter.submit(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            tvTotalEvents.setText("Tổng số sự kiện đã tạo: 0");
            tvEventsThisMonth.setText("Sự kiện diễn ra trong tháng này: 0");
            tvAvgFillRate.setText("Tỉ lệ lấp đầy TB: 0%");
            tvTopEvent.setText("Sự kiện bán chạy: Chưa có dữ liệu");
            return;
        }

        db.collection("events")
                .whereEqualTo("ownerId", user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    allEvents.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            e.setId(doc.getId());
                            allEvents.add(e);
                        }
                    }

                    // === Thống kê trên events ===
                    int totalEvents = allEvents.size();
                    int eventsThisMonth = 0;

                    long totalSeats = 0;
                    long totalSold = 0;

                    String topEventTitle = "Chưa có dữ liệu";
                    int topEventSold = -1;

                    Calendar nowCal = Calendar.getInstance();
                    int curMonth = nowCal.get(Calendar.MONTH);
                    int curYear = nowCal.get(Calendar.YEAR);

                    for (Event e : allEvents) {
                        if (e == null) continue;

                        // month của event
                        Timestamp startTs = e.getStartTime();
                        Date startDate = startTs != null ? startTs.toDate() : null;
                        if (startDate != null) {
                            Calendar ec = Calendar.getInstance();
                            ec.setTime(startDate);
                            int m = ec.get(Calendar.MONTH);
                            int y = ec.get(Calendar.YEAR);
                            if (m == curMonth && y == curYear) {
                                eventsThisMonth++;
                            }
                        }

                        // tính sold & total seats
                        Integer total = e.getTotalSeats();
                        Integer avail = e.getAvailableSeats();
                        if (total != null && total > 0) {
                            int t = total;
                            int sold = 0;
                            if (avail != null) {
                                sold = t - avail;
                                if (sold < 0) sold = 0;
                            }
                            totalSeats += t;
                            totalSold += sold;

                            if (sold > topEventSold) {
                                topEventSold = sold;
                                topEventTitle = (e.getTitle() != null && !e.getTitle().isEmpty())
                                        ? e.getTitle() : "(Không tên)";
                            }
                        }
                    }

                    // set UI
                    tvTotalEvents.setText("Tổng số sự kiện đã tạo: " + totalEvents);
                    tvEventsThisMonth.setText("Sự kiện diễn ra trong tháng này: " + eventsThisMonth);

                    String fillRateText;
                    if (totalSeats > 0) {
                        double rate = (double) totalSold / (double) totalSeats * 100.0;
                        fillRateText = String.format(Locale.getDefault(),
                                "Tỉ lệ lấp đầy TB: %.1f%%", rate);
                    } else {
                        fillRateText = "Tỉ lệ lấp đầy TB: 0%";
                    }
                    tvAvgFillRate.setText(fillRateText);

                    if (topEventSold < 0) {
                        tvTopEvent.setText("Sự kiện bán chạy: Chưa có dữ liệu");
                    } else {
                        tvTopEvent.setText("Sự kiện bán chạy: " + topEventTitle +
                                " (" + topEventSold + " vé)");
                    }

                    applyFilter(currentFilter);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Không tải được sự kiện: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    adapter.submit(new ArrayList<>());
                    tvEmpty.setVisibility(View.VISIBLE);

                    tvTotalEvents.setText("Tổng số sự kiện đã tạo: 0");
                    tvEventsThisMonth.setText("Sự kiện diễn ra trong tháng này: 0");
                    tvAvgFillRate.setText("Tỉ lệ lấp đầy TB: 0%");
                    tvTopEvent.setText("Sự kiện bán chạy: Chưa có dữ liệu");
                });
    }

    /** Lọc list theo 3 tab: Sắp diễn ra / Hôm nay / Đã kết thúc */
    private void applyFilter(FilterType filter) {
        List<Event> filtered = new ArrayList<>();

        // mốc thời gian
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();

        // đầu hôm nay
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfToday = cal.getTime();

        // đầu ngày mai
        cal.add(Calendar.DAY_OF_YEAR, 1);
        Date startOfTomorrow = cal.getTime();

        for (Event e : allEvents) {
            if (e == null) continue;

            Timestamp startTs = e.getStartTime();
            Timestamp endTs   = e.getEndTime();

            Date startDate = startTs != null ? startTs.toDate() : null;
            Date endDate   = endTs != null ? endTs.toDate() : null;

            switch (filter) {
                case UPCOMING:
                    // từ ngày mai trở đi
                    if (startDate != null && !startDate.before(startOfTomorrow)) {
                        filtered.add(e);
                    }
                    break;

                case TODAY:
                    // trong hôm nay: start ∈ [startOfToday, startOfTomorrow)
                    if (startDate != null &&
                            !startDate.before(startOfToday) &&
                            startDate.before(startOfTomorrow)) {
                        filtered.add(e);
                    }
                    break;

                case FINISHED:
                    // đã kết thúc: endTime < now (nếu không có end thì dùng start)
                    Date compareEnd = endDate != null ? endDate : startDate;
                    if (compareEnd != null && compareEnd.before(now)) {
                        filtered.add(e);
                    }
                    break;
            }
        }

        adapter.submit(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAllEvents();
        loadStats();
    }
}
