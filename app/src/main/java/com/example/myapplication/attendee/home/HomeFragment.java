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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.common.DummyData;
import com.example.myapplication.common.model.Event;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

public class HomeFragment extends Fragment {

    private EventAdapter adapter;
    private String currentQuery = "";
    private String currentCategory = "Tất cả";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        RecyclerView rv = v.findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Event> data = DummyData.events();
        adapter = new EventAdapter(data, this::onItemClick);
        rv.setAdapter(adapter);

        SearchView sv = v.findViewById(R.id.searchView);
        if (sv != null) {
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) { return false; }
                @Override public boolean onQueryTextChange(String q) {
                    currentQuery = q;
                    adapter.submitFilter(currentQuery, currentCategory);
                    return true;
                }
            });
        }

        ChipGroup chipGroup = v.findViewById(R.id.chipGroup);
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    currentCategory = "Tất cả";
                } else {
                    Chip c = group.findViewById(checkedIds.get(0));
                    currentCategory = c.getText().toString();
                }
                adapter.submitFilter(currentQuery, currentCategory);
            });
        }
    }

    private void onItemClick(Event e){
        Toast.makeText(getContext(), "Mở chi tiết: " + e.title, Toast.LENGTH_SHORT).show();
    }
}
