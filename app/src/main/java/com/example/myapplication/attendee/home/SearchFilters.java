package com.example.myapplication.attendee.home;

import android.content.Context;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class SearchFilters implements Serializable {
    @Nullable public Long fromUtcMs;
    @Nullable public Long toUtcMs;
    public String cityCode;
    public boolean onlyFree;
    public String categoryCode;

    public static SearchFilters defaultAll() {
        SearchFilters f = new SearchFilters();
        f.cityCode = "all";
        f.categoryCode = "all";
        f.onlyFree = false;
        return f;
    }

    public String getDateSummary(String anyText) {
        if (fromUtcMs == null && toUtcMs == null) return anyText;
        SimpleDateFormat df = new SimpleDateFormat("dd/MM", Locale.getDefault());
        String from = fromUtcMs == null ? "…" : df.format(fromUtcMs);
        String to   = toUtcMs   == null ? "…" : df.format(toUtcMs);
        return from + " - " + to;
    }

    public String getFilterSummary(Context ctx) {
        String city;
        switch (cityCode == null ? "all" : cityCode) {
            case "hanoi": city = "Hà Nội"; break;
            case "hcm":   city = "TP.HCM"; break;
            case "dalat": city = "Đà Lạt"; break;
            case "other": city = ctx.getString(R.string.city_other); break;
            default:      city = ctx.getString(R.string.nationwide);
        }
        String cat;
        switch (categoryCode == null ? "all" : categoryCode) {
            case "music": cat = ctx.getString(R.string.cat_music); break;
            case "art":   cat = ctx.getString(R.string.cat_art); break;
            case "sport": cat = ctx.getString(R.string.cat_sport); break;
            case "other": cat = ctx.getString(R.string.cat_other); break;
            default:      cat = ctx.getString(R.string.all_categories);
        }
        return (onlyFree ? "Miễn phí · " : "") + city + " · " + cat;
    }
}
