// app/src/main/java/com/example/myapplication/attendee/home/HomeFragment.java
package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class HomeFragment extends Fragment {

    private EventAdapter adapter;
    private HomeViewModel vm;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView rv = v.findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EventAdapter(e -> onItemClick(e));
        rv.setAdapter(adapter);

        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        vm.getEvents().observe(getViewLifecycleOwner(), adapter::submitList);
        vm.load();

        SearchView sv = v.findViewById(R.id.searchView);
        if (sv != null) {
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) { return false; }
                @Override public boolean onQueryTextChange(String q) { vm.setQuery(q); return true; }
            });
        }

        ChipGroup chipGroup = v.findViewById(R.id.chipGroup);
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
                String cat = "Tất cả";
                if (!ids.isEmpty()) {
                    Chip c = group.findViewById(ids.get(0));
                    cat = String.valueOf(c.getText());
                }
                vm.setCategory(cat);
            });
        }
    }

    private void onItemClick(Event e){
        Toast.makeText(getContext(), "Mở chi tiết: " + e.title, Toast.LENGTH_SHORT).show();
    }

}

