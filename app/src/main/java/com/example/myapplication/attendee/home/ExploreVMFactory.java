package com.example.myapplication.attendee.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;

/** Factory không tham số: tự tạo Repository (remote-only) để dùng ngay. */
public class ExploreVMFactory implements ViewModelProvider.Factory {
    private final EventRepository repo;

    public ExploreVMFactory(EventRepository repo) {
        this.repo = repo;
    }

    @NonNull @Override @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ExploreViewModel.class)) {
            return (T) new ExploreViewModel(repo); // khớp constructor mới
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}

