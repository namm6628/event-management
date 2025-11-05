package com.example.myapplication.attendee.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;

/** Factory không tham số: tự tạo Repository (remote-only) để dùng ngay. */
public class ExploreVMFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ExploreViewModel.class)) {
            // Repo tối thiểu dùng remote; khi thêm Room, bạn có thể đổi sang ctor (local, remote)
            EventRepository repo = new EventRepository(new EventRemoteDataSource());
            return (T) new ExploreViewModel(repo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
    }
}
