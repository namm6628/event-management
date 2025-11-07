package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExploreFragment extends Fragment {

    private ExploreViewModel vm;
    private RecyclerView recycler;
    private EventAdapter adapter; // dùng adapter thật bạn đã up

    private TextInputEditText searchBar;
    private ChipGroup chipGroup;
    private MaterialButton btnFilter;

    // Map chipId -> category chuẩn Firestore (null = Tất cả)
    private final Map<Integer, String> chipToCategory = new HashMap<Integer, String>() {{
        put(R.id.chip_all,   null);
        put(R.id.chip_music, "Âm nhạc");
        put(R.id.chip_art,   "Sân khấu & nghệ thuật");
        put(R.id.chip_sport, "Thể thao");
        put(R.id.chip_other, "Khác");
    }};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Dùng chung ViewModel đã được inject repo ở HomeFragment
        vm = new ViewModelProvider(requireActivity()).get(ExploreViewModel.class);

        recycler = view.findViewById(R.id.recyclerEvents);
        searchBar = view.findViewById(R.id.searchBar);
        chipGroup = view.findViewById(R.id.chipGroup);
        btnFilter = view.findViewById(R.id.btnFilter);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setHasFixedSize(true);

        adapter = new EventAdapter(event -> {
            // TODO: xử lý khi click event
            // ví dụ:
            Toast.makeText(requireContext(), "Bạn đã chọn: " + event.getTitle(), Toast.LENGTH_SHORT).show();
        });

        recycler.setAdapter(adapter);

        vm.getVisibleEvents().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            // hoặc adapter.setData(list); adapter.notifyDataSetChanged();
        });

        // Text search
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                vm.setSearchQuery(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Chip filter (singleSelection đã set trong XML)
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                vm.setCategoryFilter(null);
                return;
            }
            int id = checkedIds.get(0);
            vm.setCategoryFilter(chipToCategory.get(id));
        });

        // Bộ lọc nâng cao (placeholder)
        btnFilter.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Mở bộ lọc nâng cao (TODO: BottomSheet)", Toast.LENGTH_SHORT).show()
        );

        // ExploreFragment KHÔNG gọi refresh() nữa nếu HomeFragment đã gọi khi vào tab.
        // Nếu Explore là entry đầu tiên, có thể bật:
        // if (savedInstanceState == null) vm.refresh();

        // Infinite scroll (giống Home)
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof LinearLayoutManager)) return;
                int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                if (total > 0 && last >= total - 4) vm.loadMore();
            }
        });
    }

    // Adapter sample placeholder nếu cần
    static class EventAdapterPlaceholder extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Event> data;
        void submitList(List<Event> list) { this.data = list; notifyDataSetChanged(); }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int v) { throw new UnsupportedOperationException("Use your real adapter"); }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {}
    }
}
