package com.example.myapplication.attendee.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.repo.EventRepository;

import java.util.List;

public class HomeViewModel extends ViewModel {
    private final EventRepository repo;
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public LiveData<Boolean> loading = _loading;
    public LiveData<List<Event>> events;

    public HomeViewModel(EventRepository repo) {
        this.repo = repo;
        this.events = repo.observe();
    }

    public void refresh() {
        _loading.setValue(true);
        repo.refresh(e -> _loading.postValue(false));
    }
}
