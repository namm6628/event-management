package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class ExploreFragment extends Fragment {

    private EventAdapter adapter;
    private ExploreViewModel vm;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = i.inflate(R.layout.fragment_explore, c, false);

        // 1️⃣ RecyclerView
        RecyclerView rv = v.findViewById(R.id.recyclerEvents);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EventAdapter(event -> {});
        rv.setAdapter(adapter);

        // 2️⃣ ViewModel
        vm = new ViewModelProvider(this).get(ExploreViewModel.class);

        vm.getEvents().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            rv.smoothScrollToPosition(0); // mượt hơn scrollToPosition
        });

        vm.initIfNeeded();


        // 3️⃣ Search
        TextInputEditText searchBar = v.findViewById(R.id.searchBar);
        if (searchBar != null) {
            searchBar.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    vm.setQuery(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // 4️⃣ ChipGroup
        ChipGroup chipGroup = v.findViewById(R.id.chipGroup);

// Chỉ add một lần để tránh chồng chip
        if (chipGroup.getChildCount() == 0) {
            for (String cat : getResources().getStringArray(R.array.categories)) {
                Chip chip = new Chip(getContext(), null,
                        com.google.android.material.R.style.Widget_Material3_Chip_Filter);
                chip.setId(View.generateViewId());
                chip.setText(cat);           // "Âm nhạc", "Sân khấu", "Thể thao", "Hội thảo"
                chip.setCheckable(true);
                chip.setClickable(true);                            // ✅ đảm bảo nhận click
                chip.setEnsureMinTouchTargetSize(true);
                chipGroup.addView(chip);
            }
        }

// Bật single selection & listener ở group (ổn định hơn)
        // === CHIP LISTENER (TV, có "Tất cả") ===
        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(false);

        // Đảm bảo ChipGroup không bị view khác chồng lên
        chipGroup.bringToFront();

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) { vm.setCategory(""); return; }
            Chip selected = group.findViewById(ids.get(0));
            if (selected == null) { vm.setCategory(""); return; }
            String cat = selected.getText().toString().trim();
            vm.setCategory(cat.equalsIgnoreCase("Tất cả") ? "" : cat);
            if (searchBar != null && searchBar.length() > 0) searchBar.setText("");
        });


        // 5️⃣ Nút Bộ lọc nâng cao
        MaterialButton btnFilter = v.findViewById(R.id.btnFilter);
        btnFilter.setOnClickListener(_v -> {
            new EventFilterSheet()
                    .setOnApply((city, cat, from, to) -> {
                        vm.setCity(city);
                        if (cat != null && !cat.isEmpty()) vm.setCategory(cat);
                    })
                    .show(getParentFragmentManager(), "filter");
        });

        return v;
    }
}
