package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.myapplication.R;
import com.example.myapplication.databinding.BottomsheetDateRangeBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.MaterialDatePicker;

public class DateRangeSheet extends BottomSheetDialogFragment {
    public interface OnApply { void onDateApplied(SearchFilters f); }

    private BottomsheetDateRangeBinding vb;
    private SearchFilters temp;
    private OnApply onApply;

    public static void show(androidx.fragment.app.FragmentManager fm, SearchFilters current, OnApply cb) {
        DateRangeSheet s = new DateRangeSheet();
        Bundle b = new Bundle();
        b.putSerializable("filters", current);
        s.setArguments(b);
        s.onApply = cb;
        s.show(fm, "DateRangeSheet");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vb = BottomsheetDateRangeBinding.inflate(inflater, container, false);
        temp = (SearchFilters) getArguments().getSerializable("filters");
        if (temp == null) temp = SearchFilters.defaultAll();

        vb.btnPickRange.setOnClickListener(v -> {
            MaterialDatePicker.Builder<androidx.core.util.Pair<Long,Long>> builder =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText(R.string.pick_date_range);
            MaterialDatePicker<androidx.core.util.Pair<Long,Long>> picker = builder.build();
            picker.addOnPositiveButtonClickListener(sel -> {
                temp.fromUtcMs = sel.first;
                temp.toUtcMs   = sel.second;
                vb.tvSummary.setText(temp.getDateSummary(getString(R.string.date_any)));
            });
            picker.show(getParentFragmentManager(), "mdp-range");
        });

        vb.btnReset.setOnClickListener(v -> {
            temp.fromUtcMs = null; temp.toUtcMs = null;
            vb.tvSummary.setText(getString(R.string.date_any));
        });

        vb.btnApply.setOnClickListener(v -> { if (onApply != null) onApply.onDateApplied(temp); dismiss(); });
        vb.tvSummary.setText(temp.getDateSummary(getString(R.string.date_any)));
        return vb.getRoot();
    }
}
