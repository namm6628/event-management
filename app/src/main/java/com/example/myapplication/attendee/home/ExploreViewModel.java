package com.example.myapplication.attendee.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel dùng cho Home & Explore.
 * Cách B: nhận EventRepository qua constructor (Factory truyền vào).
 * Giai đoạn đầu vẫn dùng EventRemoteDataSource để fetch; repo giữ sẵn cho lộ trình unify sau.
 */
public class ExploreViewModel extends ViewModel {

    private static final int PAGE_SIZE = 20;

    private final MutableLiveData<String> category = new MutableLiveData<>("Tất cả");
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<List<Event>> backing = new MutableLiveData<>(new ArrayList<>());

    // Repo hiện chưa bắt buộc dùng cho fetch; để đây cho thống nhất kiến trúc về lâu dài
    private final EventRepository repo;
    private final EventRemoteDataSource remote = new EventRemoteDataSource();

    private DocumentSnapshot lastVisible = null;
    private boolean isLoading = false;
    private boolean noMore = false;

    public ExploreViewModel(@NonNull EventRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<Event>> getEvents() { return backing; }

    public void setCategory(String cat) {
        if (cat == null) cat = "Tất cả";
        String cur = category.getValue();
        if (cur != null && cur.equals(cat)) return;
        category.setValue(cat);
        refresh();
    }

    public void setQuery(String q) {
        if (q == null) q = "";
        query.setValue(q);
        List<Event> cur = backing.getValue();
        if (cur == null) cur = Collections.emptyList();
        backing.setValue(EventRemoteDataSource.filterClient(cur, q));
    }

    /** Tải trang đầu theo category hiện tại. */
    public void refresh() {
        if (isLoading) return;
        isLoading = true; noMore = false; lastVisible = null;
        final String cat = category.getValue();

        remote.loadFirstPage(cat, PAGE_SIZE).addOnCompleteListener(task -> {
            isLoading = false;
            if (!task.isSuccessful() || task.getResult() == null) {
                backing.setValue(Collections.emptyList());
                return;
            }
            QuerySnapshot snap = task.getResult();
            List<Event> first = EventRemoteDataSource.map(snap);
            if (!first.isEmpty()) lastVisible = snap.getDocuments().get(snap.size() - 1);
            noMore = first.isEmpty();
            backing.setValue(EventRemoteDataSource.filterClient(first, query.getValue()));
        });
    }

    /** Nạp trang tiếp theo. */
    public void loadMore() {
        if (isLoading || noMore) return;
        isLoading = true;
        final String cat = category.getValue();

        remote.loadNextPage(cat, PAGE_SIZE, lastVisible).addOnCompleteListener(task -> {
            isLoading = false;
            if (!task.isSuccessful() || task.getResult() == null) return;

            QuerySnapshot snap = task.getResult();
            List<Event> more = EventRemoteDataSource.map(snap);
            if (!more.isEmpty()) lastVisible = snap.getDocuments().get(snap.size() - 1);
            else { noMore = true; return; }

            List<Event> cur = backing.getValue();
            if (cur == null) cur = new ArrayList<>();
            ArrayList<Event> merged = new ArrayList<>(cur);
            merged.addAll(more);

            backing.setValue(EventRemoteDataSource.filterClient(merged, query.getValue()));
        });
    }
}
