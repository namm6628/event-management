package com.example.myapplication.attendee.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.repo.EventRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExploreViewModel extends ViewModel {

    private final EventRepository repo;

    // Inputs
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> category = new MutableLiveData<>("Tất cả");

    // Source từ Room
    private final LiveData<List<Event>> source;

    // Output đã lọc
    private final MediatorLiveData<List<Event>> filtered = new MediatorLiveData<>();

    public ExploreViewModel(EventRepository repo) {
        this.repo = repo;
        this.source = repo.observe();

        filtered.addSource(source, list -> applyFilter());
        filtered.addSource(query, s -> applyFilter());
        filtered.addSource(category, c -> applyFilter());
    }

    public LiveData<List<Event>> events() { return filtered; }

    public void setQuery(String q) {
        if (q == null) q = "";
        query.setValue(q);
    }

    public void setCategory(String c) {
        if (c == null || c.trim().isEmpty()) c = "Tất cả";
        category.setValue(c);
    }

    public void refresh() {
        repo.refresh(null);
    }

    private void applyFilter() {
        List<Event> src = source.getValue();
        if (src == null) {
            filtered.setValue(new ArrayList<>());
            return;
        }

        String q = query.getValue() == null ? "" : query.getValue().toLowerCase(Locale.ROOT);
        String cat = category.getValue() == null ? "Tất cả" : category.getValue();

        List<Event> out = new ArrayList<>();
        for (Event e : src) {
            boolean matchCat = "Tất cả".equals(cat) ||
                    (e.getCategory() != null && e.getCategory().equalsIgnoreCase(cat));
            boolean matchQuery = q.isEmpty()
                    || safe(e.getTitle()).contains(q)
                    || safe(e.getLocation()).contains(q);
            if (matchCat && matchQuery) out.add(e);
        }
        filtered.setValue(out);
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
