package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.ServiceLocator;
import com.example.myapplication.data.repo.EventRepository;

public class HomeFragment extends Fragment {

    private EventAdapter adapter;
    private HomeViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // 1) RecyclerView
        RecyclerView rv = v.findViewById(R.id.rvEvents); // id trong fragment_home.xml
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EventAdapter(/* onItemClick = */ null);
        rv.setAdapter(adapter);

        // 2) ViewModel với Repository
        EventRepository repo = ServiceLocator.eventRepo(requireContext());
        vm = new ViewModelProvider(this, new HomeVMFactory(repo))
                .get(HomeViewModel.class);

        // 3) Observe dữ liệu từ Room
        vm.events.observe(getViewLifecycleOwner(), adapter::submitList);

        // 4) Lấy dữ liệu lần đầu: Firestore → Room → UI tự cập nhật
        vm.refresh();
    }
}
