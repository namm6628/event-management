package com.example.myapplication.attendee.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.R;
import com.example.myapplication.databinding.ActivitySearchBinding;

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_FILTERS = "extra_filters";
    public static final String EXTRA_QUERY = "extra_query";

    private ActivitySearchBinding vb;
    private SearchFilters filters = SearchFilters.defaultAll();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        setSupportActionBar(vb.topAppBar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        renderButtons();

        vb.btnDate.setOnClickListener(v ->
                DateRangeSheet.show(getSupportFragmentManager(), filters, f -> {
                    filters.fromUtcMs = f.fromUtcMs;
                    filters.toUtcMs = f.toUtcMs;
                    renderButtons();
                })
        );

        vb.btnFilter.setOnClickListener(v ->
                FilterSheet.show(getSupportFragmentManager(), filters, f -> {
                    filters = f;
                    renderButtons();
                })
        );

        vb.edtQuery.setOnEditorActionListener((tv, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { finishWithResult(); return true; }
            return false;
        });

        vb.btnApply.setOnClickListener(v -> finishWithResult());
        vb.btnClear.setOnClickListener(v -> vb.edtQuery.setText(""));
    }

    private void renderButtons() {
        vb.btnDate.setText(filters.getDateSummary(getString(R.string.date_any)));
        vb.btnFilter.setText(filters.getFilterSummary(this));
    }

    private void finishWithResult() {
        Intent out = new Intent();
        out.putExtra(EXTRA_QUERY, TextUtils.isEmpty(vb.edtQuery.getText()) ? "" : vb.edtQuery.getText().toString().trim());
        out.putExtra(EXTRA_FILTERS, filters);
        setResult(RESULT_OK, out);
        finish();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
