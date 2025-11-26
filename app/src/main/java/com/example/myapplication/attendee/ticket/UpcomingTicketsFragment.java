package com.example.myapplication.attendee.ticket;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.attendee.detail.EventDetailActivity;
import com.example.myapplication.attendee.home.EventAdapter;
import com.example.myapplication.common.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vé SẮP DIỄN RA:
 * - Có vé: hiển thị rvTickets
 * - Không vé: empty + "Có thể bạn cũng thích"
 */
public class UpcomingTicketsFragment extends Fragment {

    private RecyclerView rvTickets;
    private View layoutEmptyBig;
    private RecyclerView rvSuggestions;
    private MaterialButton btnBuyNow;

    private TicketAdapter ticketAdapter;
    private EventAdapter suggestionAdapter;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public UpcomingTicketsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_upcoming_tickets, container, false);

        rvTickets = v.findViewById(R.id.rvTickets);
        layoutEmptyBig = v.findViewById(R.id.layoutEmptyBig);
        rvSuggestions = v.findViewById(R.id.rvSuggestions);
        btnBuyNow = v.findViewById(R.id.btnBuyNow);

        // List vé (dọc)
        rvTickets.setLayoutManager(new LinearLayoutManager(getContext()));
        ticketAdapter = new TicketAdapter(item -> {
            Context c = requireContext();
            Intent i = new Intent(c, EventDetailActivity.class);
            i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, item.eventId);
            c.startActivity(i);
        });
        rvTickets.setAdapter(ticketAdapter);

        // List gợi ý (ngang)
        LinearLayoutManager lm = new LinearLayoutManager(
                getContext(), LinearLayoutManager.HORIZONTAL, false);
        rvSuggestions.setLayoutManager(lm);
        suggestionAdapter = new EventAdapter(event -> {
            Context c = requireContext();
            Intent i = new Intent(c, EventDetailActivity.class);
            i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
            c.startActivity(i);
        });
        rvSuggestions.setAdapter(suggestionAdapter);

        btnBuyNow.setOnClickListener(view -> navigateToExplore());

        loadTickets();

        return v;
    }

    private void navigateToExplore() {
        if (getActivity() instanceof TicketNavigationHost) {
            ((TicketNavigationHost) getActivity()).onBuyTicketClicked();
        } else {
            // fallback: báo cho dev biết chưa implement
            Toast.makeText(getContext(),
                    "Chưa cấu hình điều hướng sang Explore (TicketNavigationHost).",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void loadTickets() {
        if (auth.getCurrentUser() == null) {
            showEmptyBig();
            loadSuggestions();
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        long nowMillis = System.currentTimeMillis();

        db.collection("orders")
                .whereEqualTo("userId", uid)   // TODO: sửa field nếu khác
                .whereEqualTo("status", "paid")
                .get()
                .addOnSuccessListener(orderSnap -> {
                    if (orderSnap.isEmpty()) {
                        showEmptyBig();
                        loadSuggestions();
                        return;
                    }

                    List<TicketAdapter.TicketItem> result = new ArrayList<>();
                    List<Task<DocumentSnapshot>> tasks = new ArrayList<>();

                    for (DocumentSnapshot orderDoc : orderSnap) {
                        String eventId = orderDoc.getString("eventId"); // TODO: field eventId
                        if (eventId == null) continue;

                        Task<DocumentSnapshot> t = db.collection("events")
                                .document(eventId)
                                .get();
                        tasks.add(t);

                        t.addOnSuccessListener(evSnap -> {
                            if (!evSnap.exists()) return;
                            Event e = evSnap.toObject(Event.class);
                            if (e == null) return;

                            Timestamp startTs = e.getStartTime();
                            Timestamp endTs = e.getEndTime();

                            if (startTs == null) return;

                            long start = startTs.toDate().getTime();
                            long end = (endTs != null)
                                    ? endTs.toDate().getTime()
                                    : start + 2 * 60 * 60 * 1000;

                            // SỰ KIỆN SẮP DIỄN RA
                            if (end <= nowMillis) return;

                            List<?> ticketsArr = (List<?>) orderDoc.get("tickets");
                            if (ticketsArr == null || ticketsArr.isEmpty()) {
                                // fallback: 1 vé / order
                                TicketAdapter.TicketItem item = new TicketAdapter.TicketItem();
                                item.orderId = orderDoc.getId();
                                item.eventId = eventId;
                                item.title = e.getTitle();
                                item.venue = e.getLocation();
                                item.addressDetail = e.getAddressDetail();
                                item.startTimeMillis = start;
                                item.endTimeMillis = end;

                                Double price = e.getPrice();
                                item.ticketPrice = price != null ? price.longValue() : 0L;
                                item.ticketQuantity = 1;
                                item.ticketTypeName = null;

                                result.add(item);
                            } else {
                                // 1 TicketItem cho mỗi LOẠI vé trong array
                                for (Object obj : ticketsArr) {
                                    if (!(obj instanceof java.util.Map)) continue;
                                    java.util.Map<?, ?> tk = (java.util.Map<?, ?>) obj;

                                    String typeName = (String) tk.get("name");
                                    Number qNum = (Number) tk.get("quantity");
                                    Number pNum = (Number) tk.get("price");

                                    TicketAdapter.TicketItem item = new TicketAdapter.TicketItem();
                                    item.orderId = orderDoc.getId();
                                    item.eventId = eventId;
                                    item.title = e.getTitle();
                                    item.venue = e.getLocation();
                                    item.addressDetail = e.getAddressDetail();
                                    item.startTimeMillis = start;
                                    item.endTimeMillis = end;

                                    item.ticketTypeName = typeName;
                                    item.ticketQuantity = qNum != null ? qNum.longValue() : 0L;
                                    item.ticketPrice = pNum != null ? pNum.longValue() : 0L;

                                    result.add(item);
                                }
                            }

                            TicketAdapter.TicketItem item = new TicketAdapter.TicketItem();
                            item.orderId = orderDoc.getId();
                            item.eventId = eventId;
                            item.title = e.getTitle();

                            // ======= Thời gian =======
                            item.startTimeMillis = start;
                            item.endTimeMillis = end;

                            // ======= Venue =======
                            item.venue = e.getLocation();          // hoặc e.getVenue() nếu getter tên khác
                            item.addressDetail = e.getAddressDetail();  // địa chỉ chi tiết (nếu có)

                            // ======= Lấy loại vé & giá vé đúng trong order =======
                            List<Object> tickets = (List<Object>) orderDoc.get("tickets");
                            if (tickets != null && !tickets.isEmpty()) {
                                Object first = tickets.get(0);
                                if (first instanceof java.util.Map) {
                                    java.util.Map<String,Object> map = (java.util.Map<String,Object>) first;

                                    item.ticketTypeName = (String) map.get("name");

                                    if (map.get("price") instanceof Number) {
                                        item.ticketPrice = ((Number) map.get("price")).longValue();
                                    }
                                }
                            }

                            // ======= Giá min của sự kiện =======
                            Double price = e.getPrice();
                            item.minPrice = price != null ? price.longValue() : 0L;

                            result.add(item);
                        });

                    }

                    Tasks.whenAllComplete(tasks)
                            .addOnCompleteListener(t -> {
                                if (result.isEmpty()) {
                                    showEmptyBig();
                                    loadSuggestions();
                                } else {
                                    Collections.sort(result,
                                            (a, b) -> Long.compare(a.startTimeMillis, b.startTimeMillis));
                                    ticketAdapter.submitList(result);
                                    showTicketList();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            "Lỗi tải vé: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmptyBig();
                    loadSuggestions();
                });
    }

    private void loadSuggestions() {
        db.collection("events")
                .orderBy("startTime", Query.Direction.ASCENDING) // cần index nếu Firestore báo
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        Event e = d.toObject(Event.class);
                        if (e != null) {
                            e.setId(d.getId());
                            events.add(e);
                        }
                    }
                    suggestionAdapter.submitList(events);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Lỗi tải gợi ý: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showEmptyBig() {
        rvTickets.setVisibility(View.GONE);
        layoutEmptyBig.setVisibility(View.VISIBLE);
    }

    private void showTicketList() {
        layoutEmptyBig.setVisibility(View.GONE);
        rvTickets.setVisibility(View.VISIBLE);
    }
}
