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
import com.example.myapplication.common.ServiceLocator;
import com.example.myapplication.data.repo.EventRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class ExploreFragment extends Fragment {

    private ExploreViewModel vm;
    private EventAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // 1) RecyclerView
        RecyclerView rv = v.findViewById(R.id.recyclerEvents);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EventAdapter(/* onItemClick = */ null);
        rv.setAdapter(adapter);

        // 2) VM + Repo
        EventRepository repo = ServiceLocator.eventRepo(requireContext());
        vm = new ViewModelProvider(this, new ExploreVMFactory(repo)).get(ExploreViewModel.class);

        // 3) Search
        TextInputEditText search = v.findViewById(R.id.searchBar);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                vm.setQuery(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 4) Chip group (nếu bạn đang tạo chip động, giữ nguyên; ở đây demo vài chip phổ biến)
        ChipGroup chips = v.findViewById(R.id.chipGroup);
        if (chips.getChildCount() == 0) {
            addChip(chips, "Tất cả", true);
            addChip(chips, "Âm nhạc", false);
            addChip(chips, "Hội thảo", false);
            addChip(chips, "Sân khấu", false);
            addChip(chips, "Thể thao", false);
        }
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            String cat = "Tất cả";
            if (!checkedIds.isEmpty()) {
                Chip c = group.findViewById(checkedIds.get(0));
                if (c != null) cat = String.valueOf(c.getText());
            }
            vm.setCategory(cat);
        });

        // 5) Observe danh sách đã lọc
        vm.events().observe(getViewLifecycleOwner(), adapter::submitList);

        // 6) Lần đầu tải
        vm.refresh();
    }

    private void addChip(ChipGroup g, String text, boolean checked){
        Chip c = (Chip) getLayoutInflater().inflate(R.layout.item_chip_choice, g, false);

        c.setText(text);
        c.setCheckable(true);
        c.setChecked(checked);
        g.addView(c);
    }
}
