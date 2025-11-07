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
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExploreViewModel extends ViewModel {

    private EventRepository repo;

    // Nguồn dữ liệu gốc (đổ từ Room sau khi sync Firestore)
    private final MutableLiveData<List<Event>> allEvents = new MutableLiveData<>(new ArrayList<>());

    // Trạng thái filter/search
    private final MutableLiveData<String> categoryFilter = new MutableLiveData<>(null); // null = tất cả
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    // Danh sách hiển thị sau khi áp dụng filter/search
    private final MediatorLiveData<List<Event>> visibleEvents = new MediatorLiveData<>();

    // Phân trang kiểu Ticketbox
    private static final int PAGE_SIZE = 12; // chỉnh theo ý bạn
    private DocumentSnapshot lastVisible = null;
    private boolean isLoading = false;
    private boolean isEndReached = false;

    public ExploreViewModel() {
        visibleEvents.addSource(allEvents, it -> applyFilters());
        visibleEvents.addSource(categoryFilter, it -> applyFilters());
        visibleEvents.addSource(searchQuery, it -> applyFilters());
    }

    public ExploreViewModel(EventRepository repo) {
        this();
        this.repo = repo;
        bindRepository(); // quan sát Room, lần đầu có thể gọi refresh() ở Fragment
    }

    public void initIfNeeded(EventRepository repo) {
        if (this.repo == null) {
            this.repo = repo;
            bindRepository();
        }
    }

    private void bindRepository() {
        if (repo == null) return;

        // Quan sát DB local (Room) -> map sang Event -> set vào allEvents
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

    // ------- API cho Fragment -------

    public LiveData<List<Event>> getVisibleEvents() { return visibleEvents; }

    // Alias để tương thích code cũ (HomeFragment trước đây gọi getEvents())
    public LiveData<List<Event>> getEvents() { return getVisibleEvents(); }

    public void setSearchQuery(String q) {
        searchQuery.setValue(q != null ? q : "");
    }

    // Alias tương thích code cũ (vm.setQuery(q))
    public void setQuery(String q) { setSearchQuery(q); }

    public void setCategoryFilter(String catOrNull) {
        categoryFilter.setValue(catOrNull);   // null = tất cả
        refresh();                             // giống Ticketbox: đổi tab -> reset list & tải lại trang đầu
    }

    // Alias tương thích code cũ (vm.setCategory(...))
    public void setCategory(String category) { setCategoryFilter(category); }

    // Tải TRANG ĐẦU theo category hiện tại
    public void refresh() {
        if (repo == null || isLoading) return;
        isLoading = true;
        isEndReached = false;
        lastVisible = null;

        // Xoá local để tránh lẫn dữ liệu category cũ
        repo.clearLocal();

        String cat = categoryFilter.getValue();
        repo.loadFirstPage(cat, PAGE_SIZE)
                .addOnSuccessListener(snap -> {
                    List<Event> first = EventRemoteDataSource.map(snap);
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
    }

    // Tải TRANG TIẾP khi cuộn gần cuối
    public void loadMore() {
        if (repo == null || isLoading || isEndReached) return;
        isLoading = true;

        String cat = categoryFilter.getValue();
        repo.loadNextPage(cat, PAGE_SIZE, lastVisible)
                .addOnSuccessListener(snap -> {
                    List<Event> more = EventRemoteDataSource.map(snap);
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

    // ------- Nội bộ -------

    private void setAllEvents(List<Event> events) {
        allEvents.setValue(events != null ? events : new ArrayList<>());
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private void applyFilters() {
        List<Event> src = allEvents.getValue();
        if (src == null) src = new ArrayList<>();

        String cat = categoryFilter.getValue();              // null = tất cả
        String q = norm(searchQuery.getValue());            // search client-side theo title

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
}
