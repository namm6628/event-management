package com.example.myapplication.attendee.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.attendee.detail.EventDetailActivity;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.AppDatabase;
import com.example.myapplication.data.local.EventDao;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class HomeFragment extends Fragment {

    // ViewModel
    private ExploreViewModel vm;
    private SearchView searchView;
    private ChipGroup chipGroup;

    // Nhiều adapter & view
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

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View v,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(v, savedInstanceState);

        // Repo + Room
        EventDao dao = AppDatabase.getInstance(requireContext()).eventDao();
        EventRemoteDataSource remote = new EventRemoteDataSource();
        EventRepository repo = new EventRepository(dao, remote);

        ExploreVMFactory factory = new ExploreVMFactory(repo);
        vm = new ViewModelProvider(requireActivity(), factory).get(ExploreViewModel.class);

        // searchView = v.findViewById(R.id.searchView); // nếu layout có thì mở dòng này
        chipGroup  = v.findViewById(R.id.chipGroup);

        exploreCategoriesContainer = v.findViewById(R.id.exploreCategoriesContainer);
        tvTitleSpecial = v.findViewById(R.id.tvTitleSpecial);
        rvSpecial = v.findViewById(R.id.rvSpecialEvents);
        dynamicCategoriesLayout = v.findViewById(R.id.dynamicCategoriesLayout);

        tvTitleVideo = v.findViewById(R.id.tvTitleVideo);
        rvVideoEvents = v.findViewById(R.id.rvVideoEvents);

        specialHorizontalManager =
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        specialVerticalManager =
                new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);

        // ===== Adapter & Recycler =====

        // 1. Sự kiện đặc biệt
        rvSpecial.setLayoutManager(specialHorizontalManager);
        specialAdapter = new EventsAdapter(event -> {
            saveUserInterest(event);
            openEventDetail(event);
        });
        rvSpecial.setAdapter(specialAdapter);

        // 2. Video events
        rvVideoEvents.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        videoAdapter = new EventsAdapter(event -> {
            if (event.getVideoUrl() != null && !event.getVideoUrl().isEmpty()) {
                Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
                intent.putExtra("VIDEO_URL", event.getVideoUrl());
                startActivity(intent);
            }
        });
        rvVideoEvents.setAdapter(videoAdapter);

        // 3. Xu hướng
        RecyclerView rvTrending = v.findViewById(R.id.rvTrendingEvents);
        rvTrending.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        trendingAdapter = new EventsAdapter(event -> {
            saveUserInterest(event);
            openEventDetail(event);
        });
        rvTrending.setAdapter(trendingAdapter);

        // 4. Dành cho bạn
        RecyclerView rvForYou = v.findViewById(R.id.rvForYouEvents);
        rvForYou.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        forYouAdapter = new EventsAdapter(event -> {
            saveUserInterest(event);
            openEventDetail(event);
        });
        rvForYou.setAdapter(forYouAdapter);

        // 5. Cuối tuần
        RecyclerView rvWeekend = v.findViewById(R.id.rvWeekendEvents);
        rvWeekend.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        weekendAdapter = new EventsAdapter(event -> {
            saveUserInterest(event);
            openEventDetail(event);
        });
        rvWeekend.setAdapter(weekendAdapter);

        // ===== Quan sát LiveData =====
        vm.getVisibleEvents().observe(getViewLifecycleOwner(), list -> specialAdapter.submitList(list));
        vm.getVideoEvents().observe(getViewLifecycleOwner(), list -> videoAdapter.submitList(list));
        vm.getTrendingEvents().observe(getViewLifecycleOwner(), list -> trendingAdapter.submitList(list));
        vm.getForYouEvents().observe(getViewLifecycleOwner(), list -> forYouAdapter.submitList(list));
        vm.getWeekendEvents().observe(getViewLifecycleOwner(), list -> weekendAdapter.submitList(list));
        vm.getDynamicCategories().observe(getViewLifecycleOwner(), this::updateDynamicCategoriesUI);

        // ===== SearchView filter =====
        if (searchView != null) {
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

        // ===== Chip filter =====
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds == null || checkedIds.isEmpty()) {
                    vm.setCategoryFilter(null);
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

        // Infinite scroll
        setupScrollListeners(v);

        // Lần đầu load: truyền sở thích gần nhất (nếu có)
        if (savedInstanceState == null) {
            SharedPreferences prefs =
                    requireContext().getSharedPreferences("USER_PREFS", Context.MODE_PRIVATE);
            String lastInterest = prefs.getString("LAST_INTEREST_CATEGORY", null);
            vm.refresh(lastInterest);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (vm != null) {
            SharedPreferences prefs =
                    requireContext().getSharedPreferences("USER_PREFS", Context.MODE_PRIVATE);
            String lastInterest = prefs.getString("LAST_INTEREST_CATEGORY", null);
            vm.refresh(lastInterest);
        }
    }

    // Map chip label → category
    private String mapChipLabelToCategory(String label) {
        if (label == null) return null;
        switch (label.trim()) {
            case "Tất cả":   return null;
            case "Âm nhạc":  return "Âm nhạc";
            case "Thể thao": return "Thể thao";
            case "Sân khấu": return "Sân khấu & nghệ thuật";
            case "Hội thảo": return "Hội thảo";
            default:         return label;
        }
    }

    /** Đổi UI khi search */
    private void handleSearchUI(String query) {
        boolean isSearching = query != null && !query.isEmpty();

        exploreCategoriesContainer.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        dynamicCategoriesLayout.setVisibility(isSearching ? View.GONE : View.VISIBLE);

        tvTitleVideo.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        tvTitleSpecial.setText(isSearching ? "Kết quả tìm kiếm" : "Sự kiện đặc biệt");
        rvVideoEvents.setVisibility(isSearching ? View.GONE : View.VISIBLE);

        rvSpecial.setLayoutManager(
                isSearching ? specialVerticalManager : specialHorizontalManager
        );
    }

    /** Scroll để load thêm */
    private void setupScrollListeners(View v) {
        NestedScrollView nestedScrollView = v.findViewById(R.id.nestedScrollView);
        nestedScrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (sv, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (exploreCategoriesContainer.getVisibility() == View.VISIBLE) {
                        if (scrollY == (sv.getChildAt(0).getMeasuredHeight() - sv.getMeasuredHeight())) {
                            vm.loadMore();
                        }
                    }
                });

        rvSpecial.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(
                    @NonNull RecyclerView recyclerView,
                    int dx,
                    int dy
            ) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                if (exploreCategoriesContainer.getVisibility() == View.GONE) {
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

    /** Render các danh mục động (DynamicCategory) */
    private void updateDynamicCategoriesUI(List<DynamicCategory> categories) {
        dynamicCategoriesLayout.removeAllViews();
        dynamicAdapters.clear();
        if (categories == null) return;

        float density = getResources().getDisplayMetrics().density;
        int marginBottom8dp = (int) (8 * density);
        int marginBottom16dp = (int) (16 * density);
        int paddingEnd12dp = (int) (12 * density);

        for (DynamicCategory category : categories) {
            // Tiêu đề
            TextView titleView = new TextView(requireContext());
            titleView.setText(category.getCategoryName());
            titleView.setTextAppearance(android.R.style.TextAppearance_Material_Medium);
            titleView.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, 0, 0, marginBottom8dp);
            titleView.setLayoutParams(titleParams);

            // RecyclerView
            RecyclerView rv = new RecyclerView(requireContext());
            rv.setLayoutManager(
                    new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            rv.setClipToPadding(false);
            rv.setPadding(0, 0, paddingEnd12dp, 0);
            LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rvParams.setMargins(0, 0, 0, marginBottom16dp);
            rv.setLayoutParams(rvParams);

            // Adapter riêng cho mỗi category
            EventsAdapter dynamicAdapter = new EventsAdapter(event -> {
                saveUserInterest(event);
                openEventDetail(event);
            });
            rv.setAdapter(dynamicAdapter);
            dynamicAdapter.submitList(category.getEvents());
            dynamicAdapters.add(dynamicAdapter);

            dynamicCategoriesLayout.addView(titleView);
            dynamicCategoriesLayout.addView(rv);
        }
    }

    // ===== Helper mở chi tiết =====
    private void openEventDetail(Event event) {
        if (event.getId() == null || event.getId().isEmpty()) {
            android.widget.Toast.makeText(
                    requireContext(),
                    "Thiếu ID sự kiện",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }
        Intent it = new Intent(requireContext(), EventDetailActivity.class);
        it.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
        startActivity(it);
    }

    // Lưu sở thích user
    private void saveUserInterest(Event event) {
        if (event.getCategory() != null) {
            SharedPreferences prefs =
                    requireContext().getSharedPreferences("USER_PREFS", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("LAST_INTEREST_CATEGORY", event.getCategory())
                    .apply();
        }
    }

    // ===== Adapter list sự kiện =====

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public static class EventsAdapter extends ListAdapter<Event, EventsAdapter.VH> {

        private final OnEventClickListener clickListener;

        public EventsAdapter(OnEventClickListener listener) {
            super(DIFF);
            this.clickListener = listener;
        }

        static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
            @Override
            public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                String idA = a.getId() == null ? a.getTitle() : a.getId();
                String idB = b.getId() == null ? b.getTitle() : b.getId();
                return Objects.equals(idA, idB);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                String titleA = a.getTitle() == null ? "" : a.getTitle();
                String titleB = b.getTitle() == null ? "" : b.getTitle();
                Timestamp timeA = a.getStartTime();
                Timestamp timeB = b.getStartTime();
                boolean timesSame = (timeA == null) ? (timeB == null) : timeA.equals(timeB);
                return titleA.equals(titleB)
                        && timesSame
                        && Objects.equals(a.getAvailableSeats(), b.getAvailableSeats());
            }
        };

        static class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            final TextView price;
            final ImageView thumbnail;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvTitle);
                subtitle = itemView.findViewById(R.id.tvPlace);
                price = itemView.findViewById(R.id.tvPrice);
                thumbnail = itemView.findViewById(R.id.ivThumb);
            }

            void bind(Event event, OnEventClickListener listener) {
                itemView.setOnClickListener(v -> listener.onEventClick(event));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Event e = getItem(position);
            if (e == null) return;

            // Title
            h.title.setText(e.getTitle() == null ? "(No title)" : e.getTitle());

            // Subtitle = thời gian
            long t = (e.getStartTime() == null) ? 0L : e.getStartTime().toDate().getTime();
            String time = (t == 0L) ? "" :
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(t);
            h.subtitle.setText(time);

            // Giá
            Double p = e.getPrice();
            if (p == null || p == 0d) {
                h.price.setText("Miễn phí");
                h.price.setTextColor(
                        android.graphics.Color.parseColor("#4CAF50")
                );
            } else {
                String priceText = java.text.NumberFormat
                        .getNumberInstance(new Locale("vi", "VN"))
                        .format(p) + " đ";
                h.price.setText(priceText);
                h.price.setTextColor(
                        android.graphics.Color.parseColor("#D32F2F")
                );
            }

            // Thumbnail
            if (e.getThumbnail() != null && !e.getThumbnail().isEmpty()) {
                Glide.with(h.itemView.getContext())
                        .load(e.getThumbnail())
                        .centerCrop()
                        .placeholder(R.color.chip_bg_light)
                        .into(h.thumbnail);
            } else {
                h.thumbnail.setImageResource(R.color.chip_bg_light);
            }

            h.bind(e, clickListener);
        }
    }
}
