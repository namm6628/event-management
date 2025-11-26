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
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExploreViewModel extends ViewModel {

    // ===== BE MỚI (Room + Paging) =====
    private EventRepository repo;
    private final MutableLiveData<List<Event>> allEvents = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> categoryFilter = new MutableLiveData<>(null); // null = tất cả
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Event>> visibleEvents = new MediatorLiveData<>();

    private static final int PAGE_SIZE = 12;
    private DocumentSnapshot lastVisible = null;
    private boolean isLoading = false;
    private boolean isEndReached = false;

    // ===== Danh sách ngang (BE cũ, dùng remote trực tiếp) =====
    private static final int HORIZONTAL_PAGE_SIZE = 10;
    private final EventRemoteDataSource remote = new EventRemoteDataSource();

    private final MutableLiveData<List<Event>> trendingBacking =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> forYouBacking =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> weekendBacking =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<DynamicCategory>> dynamicCategoriesBacking =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>> videoEventsBacking =
            new MutableLiveData<>(new ArrayList<>());

    // Danh sách tên category động
    private final List<String> DYNAMIC_CATEGORY_NAMES = Arrays.asList(
            "Âm nhạc", "Hội thảo", "Sân khấu & nghệ thuật", "Thể thao", "Khác"
    );

    // ===== Constructors =====
    public ExploreViewModel() {
        visibleEvents.addSource(allEvents, it -> applyFilters());
        visibleEvents.addSource(categoryFilter, it -> applyFilters());
        visibleEvents.addSource(searchQuery, it -> applyFilters());
    }

    public ExploreViewModel(EventRepository repo) {
        this();
        this.repo = repo;
        bindRepository();
    }

    public void initIfNeeded(EventRepository repo) {
        if (this.repo == null) {
            this.repo = repo;
            bindRepository();
        }
    }

    // ===== Bind Room =====
    private void bindRepository() {
        if (repo == null) return;

        LiveData<List<EventEntity>> localLive = repo.getAllLocal();
        if (localLive != null) {
            LiveData<List<Event>> mapped = Transformations.map(localLive, entities -> {
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

    // ===== API cho Fragment =====
    public LiveData<List<Event>> getVisibleEvents() { return visibleEvents; }
    public LiveData<List<Event>> getEvents() { return visibleEvents; }

    public void setSearchQuery(String q) {
        searchQuery.setValue(q != null ? q : "");
    }

    public void setQuery(String q) { setSearchQuery(q); }

    public void setCategoryFilter(String catOrNull) {
        categoryFilter.setValue(catOrNull);
        // đổi tab -> reset list
        refresh();
    }

    public void setCategory(String category) { setCategoryFilter(category); }

    public LiveData<List<Event>> getTrendingEvents() { return trendingBacking; }
    public LiveData<List<Event>> getForYouEvents() { return forYouBacking; }
    public LiveData<List<Event>> getWeekendEvents() { return weekendBacking; }
    public LiveData<List<DynamicCategory>> getDynamicCategories() { return dynamicCategoriesBacking; }
    public LiveData<List<Event>> getVideoEvents() { return videoEventsBacking; }

    // ===== Refresh / Paging =====

    /**
     * Refresh có personalization (userInterest).
     * HomeFragment có thể gọi refresh(lastInterest).
     */
    public void refresh(String userInterest) {
        if (repo == null || isLoading) return;
        isLoading = true;
        isEndReached = false;
        lastVisible = null;

        // Xoá local để tránh lẫn category
        repo.clearLocal();

        String cat = categoryFilter.getValue();

        // Trang đầu list chính
        repo.loadFirstPage(cat, PAGE_SIZE)
                .addOnSuccessListener(snap -> {
                    List<Event> first = EventRemoteDataSource.map(snap);
                    repo.upsertFromRemote(first);
                    if (!first.isEmpty() && snap.size() > 0) {
                        lastVisible = snap.getDocuments().get(snap.size() - 1);
                    }
                    isEndReached = first.isEmpty() || snap.size() < PAGE_SIZE;
                    isLoading = false;
                })
                .addOnFailureListener(e -> isLoading = false);

        // 3 list AI + video + dynamic
        loadHorizontalLists(userInterest);
        loadDynamicCategories();
    }

    /** Refresh mặc định (không truyền interest) */
    public void refresh() {
        refresh(null);
    }

    public void loadMore() {
        if (repo == null || isLoading || isEndReached) return;
        isLoading = true;

        String cat = categoryFilter.getValue();
        repo.loadNextPage(cat, PAGE_SIZE, lastVisible)
                .addOnSuccessListener(snap -> {
                    List<Event> more = EventRemoteDataSource.map(snap);
                    if (!more.isEmpty()) {
                        repo.upsertFromRemote(more);
                        lastVisible = snap.getDocuments().get(snap.size() - 1);
                    }
                    if (more.isEmpty() || snap.size() < PAGE_SIZE) {
                        isEndReached = true;
                    }
                    isLoading = false;
                })
                .addOnFailureListener(e -> isLoading = false);
    }

    // ===== Nội bộ: filter client-side =====
    private void setAllEvents(List<Event> events) {
        allEvents.setValue(events != null ? events : new ArrayList<>());
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private void applyFilters() {
        List<Event> src = allEvents.getValue();
        if (src == null) src = new ArrayList<>();

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
                return ta.compareTo(tb);
            });
        } catch (Exception ignore) {}

        visibleEvents.setValue(out);
    }

    // ===== Danh sách ngang (AI) =====

    private void loadHorizontalLists(String userInterest) {
        // Trending
        remote.loadTrendingEvents(HORIZONTAL_PAGE_SIZE)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        trendingBacking.setValue(
                                EventRemoteDataSource.map(task.getResult()));
                    } else {
                        trendingBacking.setValue(Collections.emptyList());
                    }
                });

        // For You (có interest)
        remote.loadForYouEvents(HORIZONTAL_PAGE_SIZE, userInterest)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        forYouBacking.setValue(
                                EventRemoteDataSource.map(task.getResult()));
                    } else {
                        forYouBacking.setValue(Collections.emptyList());
                    }
                });

        // Weekend
        remote.loadEventsForWeekend(HORIZONTAL_PAGE_SIZE)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        weekendBacking.setValue(
                                EventRemoteDataSource.map(task.getResult()));
                    } else {
                        weekendBacking.setValue(Collections.emptyList());
                    }
                });

        // Video
        remote.loadVideoEvents(HORIZONTAL_PAGE_SIZE)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        videoEventsBacking.setValue(
                                EventRemoteDataSource.map(task.getResult()));
                    } else {
                        videoEventsBacking.setValue(Collections.emptyList());
                    }
                });
    }

    private void loadDynamicCategories() {
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (String catName : DYNAMIC_CATEGORY_NAMES) {
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
