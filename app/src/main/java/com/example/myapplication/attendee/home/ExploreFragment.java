package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ExploreFragment extends Fragment {

    private ExploreViewModel vm;
    private EventsAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        RecyclerView recycler = v.findViewById(R.id.recyclerEvents);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EventsAdapter();
        recycler.setAdapter(adapter);

        vm = new ViewModelProvider(this, new ExploreVMFactory()).get(ExploreViewModel.class);
        vm.getEvents().observe(getViewLifecycleOwner(), adapter::submitList);
        vm.refresh();

        EditText search = v.findViewById(R.id.searchBar);
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    vm.setQuery(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        ChipGroup chipGroup = v.findViewById(R.id.chipGroup);
        if (chipGroup != null) {
            buildCategoryChips(chipGroup, Arrays.asList("Tất cả", "Music", "Hội thảo", "Sân khấu", "Thể thao"));
        }

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int last = lm.findLastVisibleItemPosition();
                if (total > 0 && last >= total - 4) vm.loadMore();
            }
        });
    }

    private void buildCategoryChips(ChipGroup chipGroup, List<String> labels) {
        chipGroup.removeAllViews();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            Chip c = new Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter);
            c.setText(label);
            c.setCheckable(true);
            c.setChecked(i == 0);
            c.setOnClickListener(view -> vm.setCategory(label));
            chipGroup.addView(c);
        }
    }

    /** Adapter: so sánh theo (id nếu có) hoặc (title+startTime-millis) */
    static class EventsAdapter extends ListAdapter<Event, EventsAdapter.VH> {
        protected EventsAdapter() { super(DIFF); }

        static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
            @Override public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                if (a.getId() != null && b.getId() != null) return a.getId().equals(b.getId());
                String ta = a.getTitle() == null ? "" : a.getTitle();
                String tb = b.getTitle() == null ? "" : b.getTitle();
                long sa = tsToMillis(a.getStartTime());
                long sb = tsToMillis(b.getStartTime());
                return ta.equals(tb) && sa == sb;
            }
            @Override public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                // đơn giản hóa: so title + startTime + location
                return eq(a.getTitle(), b.getTitle())
                        && tsToMillis(a.getStartTime()) == tsToMillis(b.getStartTime())
                        && eq(a.getLocation(), b.getLocation());
            }

            private boolean eq(String x, String y) { return (x == null ? "" : x).equals(y == null ? "" : y); }
            private long tsToMillis(Timestamp ts) { return ts == null ? 0L : ts.toDate().getTime(); }
        };

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.TextView title, subtitle;
            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Event e = getItem(position);
            h.title.setText(e.getTitle() == null ? "(No title)" : e.getTitle());
            long t = e.getStartTime() == null ? 0L : e.getStartTime().toDate().getTime();
            String time = t == 0L ? "" :
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(t);
            String loc = e.getLocation() == null ? "" : e.getLocation();
            h.subtitle.setText(time + (loc.isEmpty() ? "" : " • " + loc));
        }
    }
}
