package com.example.myapplication.attendee.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.data.repo.EventRepository;

public class HomeVMFactory implements ViewModelProvider.Factory {
    private final EventRepository repo;
    public HomeVMFactory(EventRepository repo) { this.repo = repo; }

    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(repo);
        }
        throw new IllegalArgumentException("Unknown VM: " + modelClass.getName());
    }
}
