package com.example.myapplication.attendee.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.DummyData;
import com.example.myapplication.common.model.Event;

import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
import java.util.Comparator;


public class ExploreViewModel extends ViewModel {
    private final SavedStateHandle state;
    private final MutableLiveData<List<Event>> events = new MutableLiveData<>();
    private List<Event> source = new ArrayList<>();

    // Keys lưu trạng thái
    private static final String K_Q = "query";
    private static final String K_CAT = "category";
    private static final String K_CITY = "city";

    public ExploreViewModel(SavedStateHandle handle) {
        this.state = handle;
        if (!state.contains(K_Q)) state.set(K_Q, "");
        if (!state.contains(K_CAT)) state.set(K_CAT, "");
        if (!state.contains(K_CITY)) state.set(K_CITY, "");
    }

    public LiveData<List<Event>> getEvents() { return events; }

    public void initIfNeeded() {
        if (source.isEmpty()) source = DummyData.getEvents();
        applyAll();
    }

    public void setQuery(String q) {
        state.set(K_Q, q == null ? "" : q.trim());
        applyAll();
    }

    public void setCategory(String c) {
        state.set(K_CAT, c == null ? "" : c.trim());
        applyAll();
    }

    public void setCity(String city) {
        state.set(K_CITY, city == null ? "" : city.trim());
        applyAll();
    }

    public String getQuery() { return state.get(K_Q); }
    public String getCategory() { return state.get(K_CAT); }
    public String getCity() { return state.get(K_CITY); }

    private static String norm(String s){
        if (s == null) return "";
        // chuẩn hoá: lower + bỏ khoảng trắng dư + bỏ dấu tiếng Việt
        String base = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","")       // bỏ diacritics
                .replace('đ','d').replace('Đ','d');
        return base;
    }

    private static int score(Event e, String q){ // q đã norm
        if (q.isEmpty()) return 0;
        String title = norm(e.title);
        String cat   = norm(e.category);
        int s = 0;
        if (title.startsWith(q))  s += 3;
        else if (title.contains(q)) s += 2;
        if (cat.contains(q))      s += 1;
        return s;
    }

    // parse dd/MM/yyyy [HH:mm], fallback nếu thiếu giờ/phút
    private static long parseMillis(String date){
        if (date == null) return Long.MAX_VALUE;
        String[] fmts = {"dd/MM/yyyy HH:mm","dd/MM/yyyy"};
        for (String f: fmts){
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, java.util.Locale.getDefault());
                sdf.setLenient(false);
                return sdf.parse(date).getTime();
            } catch (Exception ignore){}
        }
        return Long.MAX_VALUE;
    }

    private void applyAll() {
        String qNorm    = norm(getQuery());
        String catNorm  = norm(getCategory());   // "" khi “Tất cả”
        String cityNorm = norm(getCity());

        java.util.List<Event> out = new java.util.ArrayList<>();
        for (Event e : source) {
            boolean ok = true;
            if (!catNorm.isEmpty())  ok &= norm(e.category).equals(catNorm);
            if (!cityNorm.isEmpty()) ok &= norm(e.venue).contains(cityNorm);
            if (!ok) continue;

            // nếu có query: chỉ giữ item match (score>0). Nếu không có query: giữ hết
            int s = score(e, qNorm);
            if (qNorm.isEmpty() || s > 0) {
                // tạm nhúng điểm & millis bằng transient fields (không cần setter)
                e.matchScore = s;               // thêm: public transient int matchScore;
                e.whenMillis = parseMillis(e.date); // thêm: public transient long whenMillis;
                out.add(e);
            }
        }

        // Sắp xếp:
        // - Nếu có query: theo score desc, tie-break theo ngày gần nhất asc, rồi title asc
        // - Nếu KHÔNG query (ví dụ chip hoặc “Tất cả”): theo ngày gần nhất asc, rồi title asc
        out.sort((a,b) -> {
            if (!qNorm.isEmpty()){
                int cmp = Integer.compare(b.matchScore, a.matchScore);
                if (cmp != 0) return cmp;
            }
            int t = java.lang.Long.compare(a.whenMillis, b.whenMillis);
            if (t != 0) return t;
            return a.title.compareToIgnoreCase(b.title);
        });

        // phát bản sao mới để DiffUtil/adapter nhận thay đổi chắc chắn
        events.setValue(new java.util.ArrayList<>(out));
    }




    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }

}
