package com.example.myapplication.attendee.home;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EventFilterSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "apply_filters";

    private MaterialAutoCompleteTextView autoCity, autoCategory, autoSortBy;
    private TextInputEditText edtMinPrice, edtMaxPrice;
    private MaterialCheckBox cbOnlyFree, cbHasTicket, cbAsc;
    private MaterialButton btnFrom, btnTo, btnReset, btnApply;

    private Long fromMs = null, toMs = null;

    // Nhận state hiện tại để fill lại UI khi mở
    private String initCity, initCategory, initSortBy;
    private Long initFrom, initTo;
    private Integer initMinPrice, initMaxPrice;
    private boolean initOnlyFree, initHasTicket, initAsc;

    public static EventFilterSheet newInstance(
            String city, String category, Long from, Long to, Integer minPrice, Integer maxPrice,
            boolean onlyFree, boolean hasTicket, String sortBy, boolean asc
    ) {
        EventFilterSheet f = new EventFilterSheet();
        Bundle args = new Bundle();
        if (!TextUtils.isEmpty(city))     args.putString("city", city);
        if (!TextUtils.isEmpty(category)) args.putString("category", category);
        if (from != null)     args.putLong("from", from);
        if (to != null)       args.putLong("to", to);
        if (minPrice != null) args.putInt("minPrice", minPrice);
        if (maxPrice != null) args.putInt("maxPrice", maxPrice);
        args.putBoolean("onlyFree", onlyFree);
        args.putBoolean("hasTicket", hasTicket);
        if (!TextUtils.isEmpty(sortBy)) args.putString("sortBy", sortBy);
        args.putBoolean("asc", asc);
        f.setArguments(args);
        return f;
    }

    public static EventFilterSheet newInstance() {
        return new EventFilterSheet();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inflater.inflate(R.layout.view_event_filter, container, false);

        autoCity     = v.findViewById(R.id.autoCity);
        autoCategory = v.findViewById(R.id.autoCategory);
        autoSortBy   = v.findViewById(R.id.autoSortBy);
        edtMinPrice  = v.findViewById(R.id.edtMinPrice);
        edtMaxPrice  = v.findViewById(R.id.edtMaxPrice);
        cbOnlyFree   = v.findViewById(R.id.cbOnlyFree);
        cbHasTicket  = v.findViewById(R.id.cbHasTicket);
        cbAsc        = v.findViewById(R.id.cbAsc);
        btnFrom      = v.findViewById(R.id.btnFrom);
        btnTo        = v.findViewById(R.id.btnTo);
        btnReset     = v.findViewById(R.id.btnReset);
        btnApply     = v.findViewById(R.id.btnApply);

        readInitState();

        // Date pickers
        btnFrom.setOnClickListener(view -> pickDate(true));
        btnTo.setOnClickListener(view -> pickDate(false));

        // Reset
        btnReset.setOnClickListener(view -> {
            autoCity.setText("");
            autoCategory.setText("");
            autoSortBy.setText(getString(R.string.sort_start_at));
            edtMinPrice.setText(null);
            edtMaxPrice.setText(null);
            cbOnlyFree.setChecked(false);
            cbHasTicket.setChecked(false);
            cbAsc.setChecked(true);
            fromMs = null; toMs = null;
            btnFrom.setText(R.string.from_date);
            btnTo.setText(R.string.to_date);
        });

        // Apply
        btnApply.setOnClickListener(view -> {
            Bundle out = new Bundle();
            String city = safeText(autoCity.getText());
            String category = safeText(autoCategory.getText());
            String sort = safeText(autoSortBy.getText());

            Integer minP = parseInt(safeText(edtMinPrice.getText()));
            Integer maxP = parseInt(safeText(edtMaxPrice.getText()));

            if (!TextUtils.isEmpty(city)) out.putString("city", city);
            if (!TextUtils.isEmpty(category)) out.putString("category", category);
            if (fromMs != null) out.putLong("from", fromMs);
            if (toMs != null)   out.putLong("to",   toMs);
            if (minP != null) out.putInt("minPrice", minP);
            if (maxP != null) out.putInt("maxPrice", maxP);
            out.putBoolean("onlyFree", cbOnlyFree.isChecked());
            out.putBoolean("hasTicket", cbHasTicket.isChecked());

            if (TextUtils.isEmpty(sort) || getString(R.string.sort_start_at).contentEquals(sort)) {
                out.putString("sortBy", "startAt");
            } else if (getString(R.string.sort_created_at).contentEquals(sort)) {
                out.putString("sortBy", "createdAt");
            } else if (getString(R.string.sort_price).contentEquals(sort)) {
                out.putString("sortBy", "price");
            } else {
                out.putString("sortBy", "startAt");
            }

            out.putBoolean("asc", cbAsc.isChecked());

            getParentFragmentManager().setFragmentResult(RESULT_KEY, out);
            dismiss();
        });

        return v;
    }

    private void readInitState() {
        Bundle a = getArguments();
        if (a == null) return;
        initCity      = a.getString("city", null);
        initCategory  = a.getString("category", null);
        initFrom      = a.containsKey("from") ? a.getLong("from") : null;
        initTo        = a.containsKey("to")   ? a.getLong("to")   : null;
        initMinPrice  = a.containsKey("minPrice") ? a.getInt("minPrice") : null;
        initMaxPrice  = a.containsKey("maxPrice") ? a.getInt("maxPrice") : null;
        initOnlyFree  = a.getBoolean("onlyFree", false);
        initHasTicket = a.getBoolean("hasTicket", false);
        initSortBy    = a.getString("sortBy", "startAt");
        initAsc       = a.getBoolean("asc", true);

        if (!TextUtils.isEmpty(initCity)) autoCity.setText(initCity);
        if (!TextUtils.isEmpty(initCategory)) autoCategory.setText(initCategory);
        if (initMinPrice != null) edtMinPrice.setText(String.valueOf(initMinPrice));
        if (initMaxPrice != null) edtMaxPrice.setText(String.valueOf(initMaxPrice));
        cbOnlyFree.setChecked(initOnlyFree);
        cbHasTicket.setChecked(initHasTicket);
        cbAsc.setChecked(initAsc);

        if ("createdAt".equals(initSortBy)) autoSortBy.setText(getString(R.string.sort_created_at), false);
        else if ("price".equals(initSortBy)) autoSortBy.setText(getString(R.string.sort_price), false);
        else autoSortBy.setText(getString(R.string.sort_start_at), false);

        fromMs = initFrom;
        toMs   = initTo;

        if (fromMs != null) btnFrom.setText(formatFrom(fromMs));
        if (toMs   != null) btnTo.setText(formatTo(toMs));
    }

    private void pickDate(boolean isFrom) {
        final Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(
                requireContext(),
                (view, y, m, d) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(Calendar.YEAR, y);
                    chosen.set(Calendar.MONTH, m);
                    chosen.set(Calendar.DAY_OF_MONTH, d);
                    chosen.set(Calendar.HOUR_OF_DAY, isFrom ? 0 : 23);
                    chosen.set(Calendar.MINUTE, isFrom ? 0 : 59);
                    chosen.set(Calendar.SECOND, isFrom ? 0 : 59);
                    long ms = chosen.getTimeInMillis();

                    if (isFrom) {
                        fromMs = ms;
                        btnFrom.setText(formatFrom(ms));
                    } else {
                        toMs = ms;
                        btnTo.setText(formatTo(ms));
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dlg.show();
    }

    private String formatFrom(long ms) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return getString(R.string.from_fmt, sdf.format(ms));
    }
    private String formatTo(long ms) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return getString(R.string.to_fmt, sdf.format(ms));
    }

    private static String safeText(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
    private static Integer parseInt(String s) {
        if (TextUtils.isEmpty(s)) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }
}
