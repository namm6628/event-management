package com.example.myapplication.attendee.home;

import android.graphics.Typeface; // [THÊM]
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // [THÊM]
import android.widget.LinearLayout; // [THÊM]
import android.widget.TextView; // [THÊM]

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.widget.NestedScrollView; // [THÊM]
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil; // [THÊM]
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter; // [THÊM]
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide; // [THÊM]
import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.AppDatabase;
import com.example.myapplication.data.local.EventDao;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.firebase.Timestamp; // [THÊM]
import java.text.SimpleDateFormat; // [THÊM]
import java.util.ArrayList; // [THÊM]
import java.util.List;
import java.util.Locale; // [THÊM]
import java.util.Objects;
import java.util.Objects;

public class HomeFragment extends Fragment {

    // [GIỮ NGUYÊN] - BE MỚI
    private ExploreViewModel vm;
    private SearchView searchView;
    private ChipGroup chipGroup;

    // [THAY ĐỔI] - Quản lý nhiều Adapter và View
    private EventsAdapter specialAdapter;
    private EventsAdapter trendingAdapter;
    private EventsAdapter forYouAdapter;
    private EventsAdapter weekendAdapter;
    private EventsAdapter videoAdapter;

    private View exploreCategoriesContainer;
    private TextView tvTitleSpecial;
    private RecyclerView rvSpecial;
    private LinearLayout dynamicCategoriesLayout;

    private View tvTitleVideo;
    private RecyclerView rvVideoEvents;
    private RecyclerView.LayoutManager specialHorizontalManager;
    private RecyclerView.LayoutManager specialVerticalManager;
    private final List<EventsAdapter> dynamicAdapters = new ArrayList<>();


    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // [CẬP NHẬT] - Đảm bảo bạn đang dùng layout có NestedScrollView
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // [GIỮ NGUYÊN] - BE MỚI (Khởi tạo Repo + Room)
        EventDao dao = AppDatabase.getInstance(requireContext()).eventDao();
        EventRemoteDataSource remote = new EventRemoteDataSource();
        EventRepository repo = new EventRepository(dao, remote);

        ExploreVMFactory factory = new ExploreVMFactory(repo);
        vm = new ViewModelProvider(requireActivity(), factory).get(ExploreViewModel.class);

//        searchView = v.findViewById(R.id.searchView);
        chipGroup  = v.findViewById(R.id.chipGroup);

        exploreCategoriesContainer = v.findViewById(R.id.exploreCategoriesContainer);
        tvTitleSpecial = v.findViewById(R.id.tvTitleSpecial);
        rvSpecial = v.findViewById(R.id.rvSpecialEvents);
        dynamicCategoriesLayout = v.findViewById(R.id.dynamicCategoriesLayout);

        tvTitleVideo = v.findViewById(R.id.tvTitleVideo);
        rvVideoEvents = v.findViewById(R.id.rvVideoEvents);

        specialHorizontalManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        specialVerticalManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);

        // [CẬP NHẬT] --- Cài đặt các Adapter ---
        // 1. Sự kiện đặc biệt (Danh sách chính, điều khiển bởi Room)
        rvSpecial.setLayoutManager(specialHorizontalManager);
        specialAdapter = new EventsAdapter(event -> { /* TODO: Xử lý click */ });
        rvSpecial.setAdapter(specialAdapter);

        rvVideoEvents.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        videoAdapter = new EventsAdapter(event -> {
            if (event.getVideoUrl() != null && !event.getVideoUrl().isEmpty()) {
                android.content.Intent intent = new android.content.Intent(requireContext(), VideoPlayerActivity.class);
                intent.putExtra("VIDEO_URL", event.getVideoUrl());
                startActivity(intent);
            }
        });
        rvVideoEvents.setAdapter(videoAdapter);

        // 2. Sự kiện xu hướng
        RecyclerView rvTrending = v.findViewById(R.id.rvTrendingEvents);
        rvTrending.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        trendingAdapter = new EventsAdapter(event -> { /* TODO: Xử lý click */ });
        rvTrending.setAdapter(trendingAdapter);

        // 3. Dành cho bạn
        RecyclerView rvForYou = v.findViewById(R.id.rvForYouEvents);
        rvForYou.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        forYouAdapter = new EventsAdapter(event -> { /* TODO: Xử lý click */ });
        rvForYou.setAdapter(forYouAdapter);

        // 4. Cuối tuần này
        RecyclerView rvWeekend = v.findViewById(R.id.rvWeekendEvents);
        rvWeekend.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        weekendAdapter = new EventsAdapter(event -> { /* TODO: Xử lý click */ });
        rvWeekend.setAdapter(weekendAdapter);

        // [CẬP NHẬT] --- Quan sát LiveData ---
        // 1. Quan sát danh sách chính (từ Room, đã filter)
        vm.getVisibleEvents().observe(getViewLifecycleOwner(), list -> {
            specialAdapter.submitList(list);
        });
        vm.getVideoEvents().observe(getViewLifecycleOwner(), videoAdapter::submitList);

        // 2. Quan sát các danh sách ngang (tải trực tiếp)
        vm.getTrendingEvents().observe(getViewLifecycleOwner(), trendingAdapter::submitList);
        vm.getForYouEvents().observe(getViewLifecycleOwner(), forYouAdapter::submitList);
        vm.getWeekendEvents().observe(getViewLifecycleOwner(), weekendAdapter::submitList);
        vm.getDynamicCategories().observe(getViewLifecycleOwner(), this::updateDynamicCategoriesUI);


        // [GIỮ NGUYÊN] - BE MỚI: Logic SearchView
        if (searchView != null) { // <-- THÊM DÒNG KIỂM TRA NÀY
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) {
                    vm.setSearchQuery(query);
                    searchView.clearFocus();
                    handleSearchUI(query);
                    return true;
                }
                @Override public boolean onQueryTextChange(String newText) {
                    vm.setSearchQuery(newText);
                    handleSearchUI(newText);
                    return true;
                }
            });
        }

        // [GIỮ NGUYÊN] - BE MỚI: Logic Chip filter
        if (chipGroup != null) { // <-- THÊM DÒNG KIỂM TRA NÀY
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
                }
            });
        }

        // [CẬP NHẬT] - Logic Infinite scroll (dùng hàm mới)
        setupScrollListeners(v);

        // [GIỮ NGUYÊN] - BE MỚI: Tải lần đầu
        if (savedInstanceState == null) vm.refresh();
    }

    // [GIỮ NGUYÊN] - BE MỚI: Hàm map Chip
    private String mapChipLabelToCategory(String label) {
        if (label == null) return null;
        switch (label.trim()) {
            case "Tất cả":      return null;
            case "Âm nhạc":     return "Âm nhạc";
            case "Thể thao":    return "Thể thao";
            case "Sân khấu":    return "Sân khấu & nghệ thuật";
            case "Hội thảo":    return "Hội thảo";
            default:            return label;
        }
    }

    // ------- [THÊM] - Các hàm trợ giúp cho UI (Từ BE CŨ) -------

    /** [THÊM] - Cập nhật UI khi tìm kiếm */
    private void handleSearchUI(String query) {
        boolean isSearching = query != null && !query.isEmpty();

        // Ẩn/hiện các danh sách ngang
        exploreCategoriesContainer.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        dynamicCategoriesLayout.setVisibility(isSearching ? View.GONE : View.VISIBLE);

        // Đổi tiêu đề
        tvTitleVideo.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        tvTitleSpecial.setText(isSearching ? "Kết quả tìm kiếm" : "Sự kiện đặc biệt");
        rvVideoEvents.setVisibility(isSearching ? View.GONE : View.VISIBLE);

        // Đổi LayoutManager
        rvSpecial.setLayoutManager(isSearching ? specialVerticalManager : specialHorizontalManager);
    }

    /** [THÊM] - Cài đặt logic cuộn (load more) cho UI mới */
    private void setupScrollListeners(View v) {
        // Logic 1: Load more khi cuộn NestedScrollView (chế độ bình thường)
        NestedScrollView nestedScrollView = v.findViewById(R.id.nestedScrollView);
        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (sv, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (exploreCategoriesContainer.getVisibility() == View.VISIBLE) { // Nếu đang ở chế độ bth
                if (scrollY == (sv.getChildAt(0).getMeasuredHeight() - sv.getMeasuredHeight())) {
                    vm.loadMore();
                }
            }
        });

        // Logic 2: Load more khi cuộn rvSpecial (chế độ tìm kiếm)
        rvSpecial.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                if (exploreCategoriesContainer.getVisibility() == View.GONE) { // Nếu đang ở chế độ tìm kiếm
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm == null) return;
                    int last = lm.findLastVisibleItemPosition();
                    int total = specialAdapter.getItemCount();
                    if (total > 0 && last >= total - 4) {
                        vm.loadMore();
                    }
                }
            }
        });
    }

    /** [THÊM] - Hàm tự động tạo và thêm các danh mục động */
    private void updateDynamicCategoriesUI(List<DynamicCategory> categories) {
        dynamicCategoriesLayout.removeAllViews();
        dynamicAdapters.clear();
        if (categories == null) return;

        float density = getResources().getDisplayMetrics().density;
        int marginBottom8dp = (int) (8 * density);
        int marginBottom16dp = (int) (16 * density);
        int paddingEnd12dp = (int) (12 * density);

        for (DynamicCategory category : categories) {
            // Tạo TextView (Tiêu đề)
            TextView titleView = new TextView(requireContext());
            titleView.setText(category.getCategoryName());
            titleView.setTextAppearance(android.R.style.TextAppearance_Material_Medium);
            titleView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(0, 0, 0, marginBottom8dp);
            titleView.setLayoutParams(titleParams);

            // Tạo RecyclerView (Danh sách sự kiện)
            RecyclerView rv = new RecyclerView(requireContext());
            rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            rv.setPadding(0, 0, paddingEnd12dp, 0);
            LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rvParams.setMargins(0, 0, 0, marginBottom16dp);
            rv.setLayoutParams(rvParams);

            // Tạo Adapter và gán dữ liệu
            EventsAdapter dynamicAdapter = new EventsAdapter(event -> { /* TODO: Xử lý click */ });
            rv.setAdapter(dynamicAdapter);
            dynamicAdapter.submitList(category.getEvents());
            dynamicAdapters.add(dynamicAdapter);

            // Thêm vào layout
            dynamicCategoriesLayout.addView(titleView);
            dynamicCategoriesLayout.addView(rv);
        }
    }

    // ------- [THÊM] - Adapter (dùng item_event_card.xml) -------

    // Interface cho callback khi click
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    static class EventsAdapter extends ListAdapter<Event, EventsAdapter.VH> {

        private final OnEventClickListener clickListener;

        protected EventsAdapter(OnEventClickListener listener) {
            super(DIFF);
            this.clickListener = listener;
        }

        static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
            @Override public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                // Logic so sánh ID (an toàn nhất)
                String idA = a.getId() == null ? a.getTitle() : a.getId();
                String idB = b.getId() == null ? b.getTitle() : b.getId();
                return idA.equals(idB);
            }
            @Override public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                // Logic so sánh nội dung (từ các lần trước)
                String titleA = a.getTitle() == null ? "" : a.getTitle();
                String titleB = b.getTitle() == null ? "" : b.getTitle();
                Timestamp timeA = a.getStartTime();
                Timestamp timeB = b.getStartTime();
                boolean timesSame = (timeA == null) ? (timeB == null) : timeA.equals(timeB);
                return titleA.equals(titleB) && timesSame &&
                        java.util.Objects.equals(a.getAvailableSeats(), b.getAvailableSeats());
            }
        };

        static class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle;
            final ImageView thumbnail;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvTitle);
                subtitle = itemView.findViewById(R.id.tvLocation);
                thumbnail = itemView.findViewById(R.id.imgThumbnail);
            }

            public void bind(Event event, OnEventClickListener listener) {
                // Gán listener cho toàn bộ card
                itemView.setOnClickListener(v -> listener.onEventClick(event));
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Event e = getItem(pos);
            if (e == null) return;

            // Bind dữ liệu (text, thời gian)
            h.title.setText(e.getTitle() == null ? "(No title)" : e.getTitle());
            long t = (e.getStartTime() == null) ? 0L : e.getStartTime().toDate().getTime();
            String time = (t == 0L) ? "" :
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(t);
            h.subtitle.setText(time);

            // Bind hình ảnh (dùng Glide)
            if (e.getThumbnail() != null && !e.getThumbnail().isEmpty()) {
                Glide.with(h.itemView.getContext())
                        .load(e.getThumbnail())
                        .centerCrop()
                        .placeholder(R.color.chip_bg) // Dùng màu placeholder
                        .into(h.thumbnail);
            } else {
                h.thumbnail.setImageResource(R.color.chip_bg); // Màu placeholder
            }

            // Bind click listener
            h.bind(e, clickListener);
        }
    }
}