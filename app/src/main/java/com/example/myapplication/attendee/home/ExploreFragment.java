package com.example.myapplication.attendee.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.R;
import com.example.myapplication.attendee.detail.EventDetailActivity;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.databinding.FragmentExploreBinding;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;

public class ExploreFragment extends Fragment {

    private FragmentExploreBinding binding;
    private EventAdapter adapter;

    private String fSearch = null;
    private String fCategory = null;
    private String fCity = null;
    private Long fFrom = null, fTo = null;
    private Integer fMinPrice = null, fMaxPrice = null;
    private boolean fOnlyFree = false, fHasTicket = false;
    private String fSortBy = "startTime";
    private boolean fAsc = true;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference eventsRef = db.collection("events");

    private final ActivityResultLauncher<Intent> openSearch =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    SearchFilters filters = (SearchFilters) result.getData()
                            .getSerializableExtra(SearchActivity.EXTRA_FILTERS);
                    String q = result.getData().getStringExtra(SearchActivity.EXTRA_QUERY);

                    fSearch = (q == null || q.trim().isEmpty()) ? null : q.trim();
                    if (filters != null) {
                        fCity = decodeCity(filters.cityCode);
                        fOnlyFree = filters.onlyFree;
                        fCategory = decodeCategory(filters.categoryCode);
                        fFrom = filters.fromUtcMs;
                        fTo = filters.toUtcMs;
                    }
                    if (binding != null) {
                        binding.tvSearchHint.setText(
                                fSearch == null ? getString(R.string.search_hint) : fSearch
                        );
                    }
                    fetch();
                }
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        adapter = new EventAdapter(event -> {
            if (event.getId() == null || event.getId().isEmpty()) {
                Toast.makeText(requireContext(), "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent it = new Intent(requireContext(), EventDetailActivity.class);
            it.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
            startActivity(it);
        });

        GridLayoutManager grid = new GridLayoutManager(requireContext(), 2);
        binding.recyclerEvents.setLayoutManager(grid);
        binding.recyclerEvents.setHasFixedSize(true);

        binding.recyclerEvents.setAdapter(adapter);

        binding.searchBar.setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), SearchActivity.class);
            if (fSearch != null) i.putExtra(SearchActivity.EXTRA_QUERY, fSearch);
            openSearch.launch(i);
        });
        binding.tvSearchHint.setText(getString(R.string.search_hint));

        View.OnClickListener chipClick = vv -> {
            Chip c = (Chip) vv;
            if (c == binding.chipAll)        fCategory = null;
            else if (c == binding.chipMusic) fCategory = "Âm nhạc";
            else if (c == binding.chipArt)   fCategory = "Sân khấu & nghệ thuật";
            else if (c == binding.chipSport) fCategory = "Thể thao";
            else if (c == binding.chipOther) fCategory = "Khác";
            fetch();
        };
        binding.chipAll.setOnClickListener(chipClick);
        binding.chipMusic.setOnClickListener(chipClick);
        binding.chipArt.setOnClickListener(chipClick);
        binding.chipSport.setOnClickListener(chipClick);
        binding.chipOther.setOnClickListener(chipClick);

        fetch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.recyclerEvents.setAdapter(null);
        binding = null;
    }

    private void fetch() {
        if (binding == null) return;

        Query q = eventsRef;

        if (!TextUtils.isEmpty(fCity))     q = q.whereEqualTo("location", fCity); // DB dùng 'location'
        if (!TextUtils.isEmpty(fCategory)) q = q.whereEqualTo("category", fCategory);

        if (fFrom != null) q = q.whereGreaterThanOrEqualTo("startTime", new Timestamp(fFrom / 1000, 0));
        if (fTo   != null) q = q.whereLessThanOrEqualTo("startTime",  new Timestamp(fTo   / 1000, 0));

        if (fOnlyFree)  q = q.whereEqualTo("price", 0d);
        if (fHasTicket) q = q.whereGreaterThan("availableSeats", 0);

        Query.Direction dir = fAsc ? Query.Direction.ASCENDING : Query.Direction.DESCENDING;
        if ("createdAt".equals(fSortBy))      q = q.orderBy("createdAt", dir);
        else if ("price".equals(fSortBy))     q = q.orderBy("price", dir);
        else                                  q = q.orderBy("startTime", dir);

        q.limit(200).get().addOnSuccessListener(snap -> {
            List<Event> list = new ArrayList<>();
            final String search = fSearch == null ? null : fSearch.toLowerCase(Locale.ROOT);

            for (DocumentSnapshot d : snap.getDocuments()) {
                Event e = d.toObject(Event.class);
                if (e == null) continue;

                if (e.getId() == null || e.getId().isEmpty()) {
                    try { e.setId(d.getId()); } catch (Exception ignored) {}
                }

                if (!TextUtils.isEmpty(search)) {
                    String title = e.getTitle() == null ? "" : e.getTitle();
                    if (!title.toLowerCase(Locale.ROOT).contains(search)) continue;
                }

                if (fMinPrice != null && e.getPrice() != null && e.getPrice() < fMinPrice.doubleValue()) continue;
                if (fMaxPrice != null && e.getPrice() != null && e.getPrice() > fMaxPrice.doubleValue()) continue;

                list.add(e);
            }
            if (binding != null) adapter.submitList(list);
        }).addOnFailureListener(err ->
                Toast.makeText(requireContext(), "Lỗi tải sự kiện: " + err.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    private String decodeCity(String code) {
        if (code == null || code.equals("all")) return null;
        switch (code) {
            case "hanoi": return "Hà Nội";
            case "hcm":   return "TP.HCM";
            case "dalat": return "Đà Lạt";
            case "other": return "Khác";
            default: return null;
        }
    }
    private String decodeCategory(String code) {
        if (code == null || code.equals("all")) return null;
        switch (code) {
            case "music": return "Âm nhạc";
            case "art":   return "Sân khấu & nghệ thuật";
            case "sport": return "Thể thao";
            case "other": return "Khác";
            default: return null;
        }
    }
}
