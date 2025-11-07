package com.example.myapplication.attendee.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventEntity;
import com.example.myapplication.data.repo.EventRepository;
import com.example.myapplication.data.remote.EventRemoteDataSource; // [THÊM]
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot; // [THÊM]

// [THÊM] - Import các thư viện cần thiết
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.Arrays;
// -----

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExploreViewModel extends ViewModel {

    // [GIỮ NGUYÊN] - BE MỚI (Room)
    private EventRepository repo;
    private final MutableLiveData<List<Event>> allEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> categoryFilter = new MutableLiveData<>(null); // null = tất cả
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Event>> visibleEvents = new MediatorLiveData<>();
    private static final int PAGE_SIZE = 12;
    private DocumentSnapshot lastVisible = null;
    private boolean isLoading = false;
    private boolean isEndReached = false;

    // [THÊM MỚI] - Các tính năng từ BE cũ
    private static final int HORIZONTAL_PAGE_SIZE = 10;
    // Dùng remote trực tiếp để không ảnh hưởng tới logic `repo` và Room
    private final EventRemoteDataSource remote = new EventRemoteDataSource();

    // LiveData cho các danh sách ngang
    private final MutableLiveData<List<Event>> trendingBacking = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> forYouBacking = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> weekendBacking = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<DynamicCategory>> dynamicCategoriesBacking = new MutableLiveData<>(new ArrayList<>());

    // Danh sách các category động (bạn có thể thêm/bớt tùy ý)
    private final List<String> DYNAMIC_CATEGORY_NAMES = Arrays.asList(
            "Âm nhạc", "Hội thảo", "Sân khấu & nghệ thuật", "Thể thao", "Khác"
    );
    // [THÊM MỚI] - Kết thúc


    // [GIỮ NGUYÊN] - BE MỚI
    public ExploreViewModel() {
        visibleEvents.addSource(allEvents, it -> applyFilters());
        visibleEvents.addSource(categoryFilter, it -> applyFilters());
        visibleEvents.addSource(searchQuery, it -> applyFilters());
    }

    // [GIỮ NGUYÊN] - BE MỚI
    public ExploreViewModel(EventRepository repo) {
        this();
        this.repo = repo;
        bindRepository();
    }

    // [GIỮ NGUYÊN] - BE MỚI
    public void initIfNeeded(EventRepository repo) {
        if (this.repo == null) {
            this.repo = repo;
            bindRepository();
        }
    }

    // [GIỮ NGUYÊN] - BE MỚI
    private void bindRepository() {
        if (repo == null) return;
        LiveData<List<EventEntity>> localLive = repo.getAllLocal();
        if (localLive != null) {
            LiveData<List<Event>> mapped = Transformations.map(localLive, entities -> {
                // ... (Giữ nguyên toàn bộ logic map EventEntity -> Event)
                List<Event> list = new ArrayList<>();
                if (entities != null) {
                    for (EventEntity en : entities) {
                        Event e = new Event();
                        e.setId(en.getId());
                        e.setTitle(en.getTitle());
                        e.setLocation(en.getLocation());
                        e.setCategory(en.getCategory());
                        e.setThumbnail(en.getThumbnail());
                        if (en.getStartTime() != null) {
                            e.setStartTime(new Timestamp(new java.util.Date(en.getStartTime())));
                        }
                        e.setPrice(en.getPrice());
                        e.setAvailableSeats(en.getAvailableSeats());
                        e.setTotalSeats(en.getTotalSeats());
                        list.add(e);
                    }
                }
                return list;
            });
            visibleEvents.addSource(mapped, this::setAllEvents);
        }
    }

    // ------- API cho Fragment -------

    // [GIỮ NGUYÊN] - BE MỚI
    public LiveData<List<Event>> getVisibleEvents() { return visibleEvents; }
    public LiveData<List<Event>> getEvents() { return getVisibleEvents(); } // Alias
    public void setSearchQuery(String q) { searchQuery.setValue(q != null ? q : ""); }
    public void setQuery(String q) { setSearchQuery(q); } // Alias
    public void setCategoryFilter(String catOrNull) {
        categoryFilter.setValue(catOrNull);
        refresh(); // giống Ticketbox: đổi tab -> reset list
    }
    public void setCategory(String category) { setCategoryFilter(category); } // Alias

    // [THÊM MỚI] - Getters cho các danh sách ngang
    public LiveData<List<Event>> getTrendingEvents() { return trendingBacking; }
    public LiveData<List<Event>> getForYouEvents() { return forYouBacking; }
    public LiveData<List<Event>> getWeekendEvents() { return weekendBacking; }
    public LiveData<List<DynamicCategory>> getDynamicCategories() { return dynamicCategoriesBacking; }
    // [THÊM MỚI] - Kết thúc


    /**
     * [CẬP NHẬT] - Tải TRANG ĐẦU (BE MỚI) + Tải các danh sách ngang (BE CŨ)
     */
    public void refresh() {
        if (repo == null || isLoading) return;
        isLoading = true;
        isEndReached = false;
        lastVisible = null;

        // [GIỮ NGUYÊN] - BE MỚI: Xoá local để tránh lẫn dữ liệu category cũ
        repo.clearLocal();

        String cat = categoryFilter.getValue();
        // [GIỮ NGUYÊN] - BE MỚI: Tải trang đầu của danh sách chính
        repo.loadFirstPage(cat, PAGE_SIZE)
                .addOnSuccessListener(snap -> {
                    List<Event> first = EventRemoteDataSource.map(snap); // Dùng static map
                    repo.upsertFromRemote(first); // ghi Room -> UI auto update
                    if (!first.isEmpty() && snap.size() > 0) {
                        lastVisible = snap.getDocuments().get(snap.size() - 1);
                    }
                    isEndReached = first.isEmpty() || snap.size() < PAGE_SIZE;
                    isLoading = false;
                })
                .addOnFailureListener(e -> {
                    isLoading = false;
                });

        // [THÊM MỚI] - Tải đồng thời 3 danh sách "AI"
        loadHorizontalLists();
        // [THÊM MỚI] - Tải đồng thời các danh mục động
        loadDynamicCategories();
    }

    // [GIỮ NGUYÊN] - BE MỚI: Tải TRANG TIẾP
    public void loadMore() {
        if (repo == null || isLoading || isEndReached) return;
        isLoading = true;

        String cat = categoryFilter.getValue();
        repo.loadNextPage(cat, PAGE_SIZE, lastVisible)
                .addOnSuccessListener(snap -> {
                    List<Event> more = EventRemoteDataSource.map(snap); // Dùng static map
                    if (!more.isEmpty()) {
                        repo.upsertFromRemote(more); // append vào Room
                        lastVisible = snap.getDocuments().get(snap.size() - 1);
                    }
                    if (more.isEmpty() || snap.size() < PAGE_SIZE) isEndReached = true;
                    isLoading = false;
                })
                .addOnFailureListener(e -> {
                    isLoading = false;
                });
    }

    // ------- Nội bộ (BE MỚI) -------

    // [GIỮ NGUYÊN] - BE MỚI
    private void setAllEvents(List<Event> events) {
        allEvents.setValue(events != null ? events : new ArrayList<>());
    }

    // [GIỮ NGUYÊN] - BE MỚI
    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    // [GIỮ NGUYÊN] - BE MỚI
    private void applyFilters() {
        List<Event> src = allEvents.getValue();
        if (src == null) src = new ArrayList<>();

        // Logic lọc và search client-side của bạn được giữ nguyên
        String cat = categoryFilter.getValue();
        String q = norm(searchQuery.getValue());

        List<Event> out = new ArrayList<>();
        for (Event e : src) {
            String evCat = e.getCategory() != null ? e.getCategory() : "Khác";
            boolean catOk = (cat == null) || evCat.equals(cat);
            String title = e.getTitle() != null ? e.getTitle() : "";
            boolean searchOk = q.isEmpty() || norm(title).contains(q);
            if (catOk && searchOk) out.add(e);
        }

        try {
            Collections.sort(out, (a, b) -> {
                Timestamp ta = a.getStartTime();
                Timestamp tb = b.getStartTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return ta.compareTo(tb); // sự kiện sớm hơn hiển thị trước
            });
        } catch (Exception ignore) {}

        visibleEvents.setValue(out);
    }


    // ------- [THÊM MỚI] - Các hàm tải danh sách ngang (Từ BE CŨ) -------

    /** [THÊM MỚI] - Hàm tải 3 danh sách "AI" */
    private void loadHorizontalLists() {
        // Dùng remote (BE mới) để gọi các hàm AI
        remote.loadTrendingEvents(HORIZONTAL_PAGE_SIZE).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                trendingBacking.setValue(EventRemoteDataSource.map(task.getResult()));
            } else {
                trendingBacking.setValue(Collections.emptyList());
            }
        });

        remote.loadForYouEvents(HORIZONTAL_PAGE_SIZE).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                forYouBacking.setValue(EventRemoteDataSource.map(task.getResult()));
            } else {
                forYouBacking.setValue(Collections.emptyList());
                // ⚠️ Nhớ kiểm tra Logcat để tạo index cho 'For You'
            }
        });

        remote.loadEventsForWeekend(HORIZONTAL_PAGE_SIZE).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                weekendBacking.setValue(EventRemoteDataSource.map(task.getResult()));
            } else {
                weekendBacking.setValue(Collections.emptyList());
            }
        });
    }

    /** [THÊM MỚI] - Hàm tải các danh mục động */
    private void loadDynamicCategories() {
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (String catName : DYNAMIC_CATEGORY_NAMES) {
            // Dùng remote (BE mới) để tải
            tasks.add(remote.loadFirstPage(catName, HORIZONTAL_PAGE_SIZE));
        }

        Tasks.whenAllSuccess(tasks).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                dynamicCategoriesBacking.setValue(Collections.emptyList());
                return;
            }
            List<DynamicCategory> dynamicList = new ArrayList<>();
            List<Object> results = task.getResult();
            for (int i = 0; i < results.size(); i++) {
                String catName = DYNAMIC_CATEGORY_NAMES.get(i);
                QuerySnapshot snapshot = (QuerySnapshot) results.get(i);
                List<Event> events = EventRemoteDataSource.map(snapshot);
                if (!events.isEmpty()) {
                    dynamicList.add(new DynamicCategory(catName, events));
                }
            }
            dynamicCategoriesBacking.setValue(dynamicList);
        });
    }
}