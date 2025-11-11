package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.databinding.BottomsheetFiltersBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

public class FilterSheet extends BottomSheetDialogFragment {
    public interface OnApply { void onFiltersApplied(SearchFilters f); }

    private BottomsheetFiltersBinding vb;
    private SearchFilters temp;
    private OnApply onApply;

    public static void show(androidx.fragment.app.FragmentManager fm, SearchFilters current, OnApply cb) {
        FilterSheet s = new FilterSheet();
        Bundle b = new Bundle();
        b.putSerializable("filters", current);
        s.setArguments(b);
        s.onApply = cb;
        s.show(fm, "FilterSheet");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vb = BottomsheetFiltersBinding.inflate(inflater, container, false);
        temp = (SearchFilters) getArguments().getSerializable("filters");
        if (temp == null) temp = SearchFilters.defaultAll();

        // City chips
        selectCityChip(temp.cityCode);
        vb.chipNationwide.setOnClickListener(v -> temp.cityCode = "all");
        vb.chipHanoi.setOnClickListener(v -> temp.cityCode = "hanoi");
        vb.chipHcm.setOnClickListener(v -> temp.cityCode = "hcm");
        vb.chipDalat.setOnClickListener(v -> temp.cityCode = "dalat");
        vb.chipOther.setOnClickListener(v -> temp.cityCode = "other");

        // Category chips
        selectCatChip(temp.categoryCode);
        vb.chipCatAll.setOnClickListener(v -> temp.categoryCode = "all");
        vb.chipCatMusic.setOnClickListener(v -> temp.categoryCode = "music");
        vb.chipCatArt.setOnClickListener(v -> temp.categoryCode = "art");
        vb.chipCatSport.setOnClickListener(v -> temp.categoryCode = "sport");
        vb.chipCatOther.setOnClickListener(v -> temp.categoryCode = "other");

        // Free switch
        vb.switchFree.setChecked(temp.onlyFree);
        vb.switchFree.setOnCheckedChangeListener((b, isChecked) -> temp.onlyFree = isChecked);

        // Buttons
        vb.btnReset.setOnClickListener(v -> {
            temp = SearchFilters.defaultAll();
            if (onApply != null) onApply.onFiltersApplied(temp);
            dismiss();
        });

        vb.btnApply.setOnClickListener(v -> { if (onApply != null) onApply.onFiltersApplied(temp); dismiss(); });

        return vb.getRoot();
    }

    private void selectCityChip(String code) {
        clearCity();
        if (code == null || code.equals("all")) vb.chipNationwide.setChecked(true);
        else if (code.equals("hanoi")) vb.chipHanoi.setChecked(true);
        else if (code.equals("hcm")) vb.chipHcm.setChecked(true);
        else if (code.equals("dalat")) vb.chipDalat.setChecked(true);
        else if (code.equals("other")) vb.chipOther.setChecked(true);
    }
    private void clearCity() {
        for (Chip c : new Chip[]{vb.chipNationwide, vb.chipHanoi, vb.chipHcm, vb.chipDalat, vb.chipOther}) c.setChecked(false);
    }

    private void selectCatChip(String code) {
        clearCat();
        if (code == null || code.equals("all")) vb.chipCatAll.setChecked(true);
        else if (code.equals("music")) vb.chipCatMusic.setChecked(true);
        else if (code.equals("art")) vb.chipCatArt.setChecked(true);
        else if (code.equals("sport")) vb.chipCatSport.setChecked(true);
        else if (code.equals("other")) vb.chipCatOther.setChecked(true);
    }
    private void clearCat() {
        for (Chip c : new Chip[]{vb.chipCatAll, vb.chipCatMusic, vb.chipCatArt, vb.chipCatSport, vb.chipCatOther}) c.setChecked(false);
    }
}
