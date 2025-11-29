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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrganizerHomeFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;

    // thống kê
    private TextView tvOrgName, tvTotalRevenue, tvTotalTickets, tvTotalOrders;

    private OrganizerEventAdapter adapter;
    private FirebaseFirestore db;

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
        tvOrgName       = v.findViewById(R.id.tvOrgName);
        tvTotalRevenue  = v.findViewById(R.id.tvTotalRevenue);
        tvTotalTickets  = v.findViewById(R.id.tvTotalTickets);
        tvTotalOrders   = v.findViewById(R.id.tvTotalOrders);

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

        loadStats();
        loadMyEvents();
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
            // nếu out login thì set về 0 cho chắc
            tvTotalRevenue.setText("Doanh thu: 0 đ");
            tvTotalTickets.setText("Tổng vé đã bán: 0");
            tvTotalOrders.setText("Tổng lượt mua: 0");
            return;
        }

        String uid = user.getUid();

        db.collection("orders")
                .whereEqualTo("ownerId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    long totalOrders = 0L;
                    long totalTickets = 0L;
                    long totalAmount = 0L;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String status = d.getString("status");
                        // chỉ tính đơn đã thanh toán: "PAID", "paid"...
                        if (status == null || !status.equalsIgnoreCase("paid")) {
                            continue;
                        }

                        totalOrders++;

                        Long qty = d.getLong("totalTickets");
                        if (qty != null) totalTickets += qty;

                        Number amountNum = (Number) d.get("totalAmount");
                        if (amountNum != null) totalAmount += amountNum.longValue();
                    }

                    NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
                    tvTotalRevenue.setText("Doanh thu: " + nf.format(totalAmount) + " đ");
                    tvTotalTickets.setText("Tổng vé đã bán: " + totalTickets);
                    tvTotalOrders.setText("Tổng lượt mua: " + totalOrders);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Lỗi tải thống kê: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    tvTotalRevenue.setText("Doanh thu: 0 đ");
                    tvTotalTickets.setText("Tổng vé đã bán: 0");
                    tvTotalOrders.setText("Tổng lượt mua: 0");
                });
    }

    private void loadMyEvents() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập lại", Toast.LENGTH_SHORT).show();
            adapter.submit(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("events")
                .whereEqualTo("ownerId", user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            e.setId(doc.getId());
                            list.add(e);
                        }
                    }
                    adapter.submit(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Không tải được sự kiện: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMyEvents();
        loadStats();
    }
}
