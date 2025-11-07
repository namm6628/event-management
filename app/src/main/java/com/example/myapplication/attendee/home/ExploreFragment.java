package com.example.myapplication.attendee.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;


import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExploreFragment extends Fragment {

    // UI (khớp file fragment_explore bạn gửi)
    private TextInputEditText searchBar;
    private ChipGroup chipGroup;
    private Chip chipAll, chipMusic, chipArt, chipSport, chipOther;
    private MaterialButton btnFilter;
    private RecyclerView recyclerEvents;

    // Adapter của bạn: constructor yêu cầu OnItemClick
    private EventAdapter adapter;

    // State filter
    private String fSearch = null;
    private String fCategory = null;         // null = Tất cả
    private String fCity = null;
    private Long fFrom = null, fTo = null;
    private Integer fMinPrice = null, fMaxPrice = null;
    private boolean fOnlyFree = false, fHasTicket = false;
    private String fSortBy = "startTime";    // "startTime" | "createdAt" | "price"
    private boolean fAsc = true;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference eventsRef = db.collection("events");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_explore, container, false);

        // Bind view
        searchBar      = v.findViewById(R.id.searchBar);
        chipGroup      = v.findViewById(R.id.chipGroup);
        chipAll        = v.findViewById(R.id.chip_all);
        chipMusic      = v.findViewById(R.id.chip_music);
        chipArt        = v.findViewById(R.id.chip_art);
        chipSport      = v.findViewById(R.id.chip_sport);
        chipOther      = v.findViewById(R.id.chip_other);
        btnFilter      = v.findViewById(R.id.btnFilter);
        recyclerEvents = v.findViewById(R.id.recyclerEvents);

        // Khởi tạo adapter đúng chữ ký: required OnItemClick
        adapter = new EventAdapter(event -> {
            // TODO: điều hướng đến màn chi tiết sự kiện nếu bạn có Activity/Fragment
            // Ví dụ (nếu có EventDetailActivity):
            // Intent i = new Intent(requireContext(), EventDetailActivity.class);
            // i.putExtra("event_id", event.getId());
            // startActivity(i);

            // Tạm thời demo:
            Toast.makeText(requireContext(),
                    "Click: " + (event.getTitle() == null ? "Sự kiện" : event.getTitle()),
                    Toast.LENGTH_SHORT).show();
        });
        recyclerEvents = v.findViewById(R.id.recyclerEvents);
        recyclerEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerEvents.setAdapter(adapter);

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // Nhận kết quả từ BottomSheet
        getChildFragmentManager().setFragmentResultListener(EventFilterSheet.RESULT_KEY, this, (key, bundle) -> {
            fCity      = bundle.getString("city", null);
            fCategory  = bundle.getString("category", fCategory); // nếu sheet trả category thì ưu tiên
            fFrom      = bundle.containsKey("from") ? bundle.getLong("from") : null;
            fTo        = bundle.containsKey("to")   ? bundle.getLong("to")   : null;
            fMinPrice  = bundle.containsKey("minPrice") ? bundle.getInt("minPrice") : null;
            fMaxPrice  = bundle.containsKey("maxPrice") ? bundle.getInt("maxPrice") : null;
            fOnlyFree  = bundle.getBoolean("onlyFree", false);
            fHasTicket = bundle.getBoolean("hasTicket", false);
            fSortBy    = bundle.getString("sortBy", "startTime");
            fAsc       = bundle.getBoolean("asc", true);
            fetch();
        });

        // Search: submit & realtime
        searchBar.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                fSearch = safeText(tv.getText());
                fetch();
                return true;
            }
            return false;
        });
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fSearch = safeText(s);
                fetch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Map chip → key Firestore (đổi nếu DB bạn dùng tiếng Việt)
        View.OnClickListener chipClick = vv -> {
            if (vv == chipAll)        fCategory = null;
            else if (vv == chipMusic) fCategory = "Âm nhạc";
            else if (vv == chipArt)   fCategory = "Sân khấu & nghệ thuật";
            else if (vv == chipSport) fCategory = "Thể thao";
            else if (vv == chipOther) fCategory = "Khác";
            fetch();
        };
        chipAll.setOnClickListener(chipClick);
        chipMusic.setOnClickListener(chipClick);
        chipArt.setOnClickListener(chipClick);
        chipSport.setOnClickListener(chipClick);
        chipOther.setOnClickListener(chipClick);

        // Nút Bộ lọc nâng cao
        btnFilter.setOnClickListener(view ->
                EventFilterSheet.newInstance(
                        fCity, fCategory, fFrom, fTo, fMinPrice, fMaxPrice, fOnlyFree, fHasTicket, fSortBy, fAsc
                ).show(getChildFragmentManager(), "EventFilterSheet")
        );

        // Load lần đầu
        fetch();
    }

    private void fetch() {
        Query q = eventsRef;

        if (!TextUtils.isEmpty(fCity))     q = q.whereEqualTo("city", fCity);
        if (!TextUtils.isEmpty(fCategory)) q = q.whereEqualTo("category", fCategory);

        // Dải ngày: startTime là Timestamp Firestore (khớp model bạn đã dùng trong DiffUtil)
        if (fFrom != null) q = q.whereGreaterThanOrEqualTo("startTime", new Timestamp(fFrom / 1000, 0));
        if (fTo   != null) q = q.whereLessThanOrEqualTo("startTime",  new Timestamp(fTo   / 1000, 0));

        if (fOnlyFree)  q = q.whereEqualTo("price", 0);
        if (fHasTicket) q = q.whereGreaterThan("availableSeats", 0); // đổi thành "ticketsLeft" nếu DB bạn dùng tên đó

        // Order
        Query.Direction dir = fAsc ? Query.Direction.ASCENDING : Query.Direction.DESCENDING;
        if ("createdAt".equals(fSortBy)) q = q.orderBy("createdAt", dir);
        else if ("price".equals(fSortBy)) q = q.orderBy("price", dir);
        else q = q.orderBy("startTime", dir);

        // Thực thi
        q.limit(200).get().addOnSuccessListener(snap -> {
            List<Event> list = new ArrayList<>();
            final String search = fSearch == null ? null : fSearch.toLowerCase(Locale.ROOT);

            for (DocumentSnapshot d : snap.getDocuments()) {
                Event e = d.toObject(Event.class);
                if (e == null) continue;

                // Lọc client-side: search theo title + min/max price
                if (!TextUtils.isEmpty(search)) {
                    String title = e.getTitle() == null ? "" : e.getTitle();
                    if (!title.toLowerCase(Locale.ROOT).contains(search)) continue;
                }
                if (fMinPrice != null && e.getPrice() != null && e.getPrice() < fMinPrice) continue;
                if (fMaxPrice != null && e.getPrice() != null && e.getPrice() > fMaxPrice) continue;

                list.add(e);
            }
            adapter.submitList(list);
        }).addOnFailureListener(err ->
                Toast.makeText(requireContext(), "Lỗi tải sự kiện: " + err.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    private static String safeText(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
