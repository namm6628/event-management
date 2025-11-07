package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.AppDatabase;
import com.example.myapplication.data.local.EventDao;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class HomeFragment extends Fragment {

    private ExploreViewModel vm;
    private RecyclerView recycler;
    private EventAdapter adapter; // dùng adapter thật bạn đã up
    private SearchView searchView;
    private ChipGroup chipGroup;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // --- Repo: Room + Firestore ---
        EventDao dao = AppDatabase.getInstance(requireContext()).eventDao();
        EventRemoteDataSource remote = new EventRemoteDataSource(); // ctor mặc định
        EventRepository repo = new EventRepository(dao, remote);

        // Share ViewModel theo Activity để ExploreFragment dùng chung
        ExploreVMFactory factory = new ExploreVMFactory(repo);
        vm = new ViewModelProvider(requireActivity(), factory).get(ExploreViewModel.class);

        // --- View binding ---
        recycler   = v.findViewById(R.id.rvEvents);
        searchView = v.findViewById(R.id.searchView);
        chipGroup  = v.findViewById(R.id.chipGroup);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setHasFixedSize(true);

        // Bằng dòng này: truyền callback OnItemClick
        adapter = new EventAdapter(event -> {
            // TODO: xử lý khi click vào một Event
            // ví dụ: mở màn hình chi tiết hoặc toast
            // Toast.makeText(requireContext(), "Chọn: " + event.getTitle(), Toast.LENGTH_SHORT).show();
        });
        recycler.setAdapter(adapter);

        // Quan sát dữ liệu hiển thị sau filter/search
        vm.getVisibleEvents().observe(getViewLifecycleOwner(), list -> {
            // Nếu EventAdapter của bạn có submitList(List<Event>) thì dùng:
            adapter.submitList(list);
            // Nếu adapter của bạn dùng setData(...) thì đổi sang:
            // adapter.setData(list); adapter.notifyDataSetChanged();
        });

        // SearchView: cập nhật khi text thay đổi hoặc submit
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                vm.setSearchQuery(query);
                searchView.clearFocus();
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                vm.setSearchQuery(newText);
                return true;
            }
        });

        // Chip filter: đọc text chip -> map về category chuẩn
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                vm.setCategoryFilter(null); // Tất cả
                return;
            }
            int id = checkedIds.get(0);
            Chip chip = group.findViewById(id);
            if (chip != null) {
                String label = chip.getText() != null ? chip.getText().toString() : "";
                vm.setCategoryFilter(mapChipLabelToCategory(label));
                // setCategoryFilter sẽ tự refresh() theo category mới
            }
        });

        // Infinite scroll: gần cuối thì nạp tiếp
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

        // Lần đầu vào màn hình → tải trang đầu
        if (savedInstanceState == null) vm.refresh();
    }

    /** Map label chip (layout Home) về category chuẩn trong Firestore/ViewModel */
    private String mapChipLabelToCategory(String label) {
        if (label == null) return null;
        switch (label.trim()) {
            case "Tất cả":      return null;
            case "Âm nhạc":     return "Âm nhạc";
            case "Thể thao":    return "Thể thao";
            case "Sân khấu":    return "Sân khấu & nghệ thuật"; // layout Home dùng label ngắn
            case "Hội thảo":    return "Hội thảo"; // hoặc "Khác" nếu Firestore của bạn không có "Hội thảo"
            default:            return label; // fallback: giữ nguyên
        }
    }

    // Adapter sample placeholder nếu cần (bạn đã có EventAdapter.java nên không dùng class này)
    static class EventAdapterPlaceholder extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Event> data;
        void submitList(List<Event> list) { this.data = list; notifyDataSetChanged(); }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int v) { throw new UnsupportedOperationException("Use your real adapter"); }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {}
    }
}
