package com.example.myapplication.attendee.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.SearchView;

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
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private ExploreViewModel vm;
    private EventsAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        RecyclerView rv = v.findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EventsAdapter();
        rv.setAdapter(adapter);

        // share state với Explore (dùng requireActivity); nếu muốn tách, đổi thành `this`
        vm = new ViewModelProvider(requireActivity(), new ExploreVMFactory())
                .get(ExploreViewModel.class);

        vm.getEvents().observe(getViewLifecycleOwner(), adapter::submitList);
        vm.refresh();

        // SearchView
        SearchView searchView = v.findViewById(R.id.searchView);
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) {
                    vm.setQuery(q);
                    return true;
                }
                @Override public boolean onQueryTextChange(String q) {
                    vm.setQuery(q);
                    return true;
                }
            });
        }


        // ChipGroup: gắn click cho từng Chip con có sẵn trong layout
        ChipGroup chipGroup = v.findViewById(R.id.chipGroup);
        if (chipGroup != null) {
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                View child = chipGroup.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child; // Java 8 compatible
                    chip.setOnClickListener(v1 -> vm.setCategory(chip.getText().toString()));
                }
            }
        }

        // Load-more
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int last = lm.findLastVisibleItemPosition();
                if (total > 0 && last >= total - 4) vm.loadMore();
            }
        });
    }

    // Adapter gọn (thay bằng card thật sau)
    static class EventsAdapter extends ListAdapter<Event, EventsAdapter.VH> {
        protected EventsAdapter() { super(DIFF); }
        static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
            @Override public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
                String ta = a.getTitle() == null ? "" : a.getTitle();
                String tb = b.getTitle() == null ? "" : b.getTitle();
                return ta.equals(tb);
            }
            @Override public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
                return a.equals(b);
            }
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
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Event e = getItem(pos);
            h.title.setText(e.getTitle() == null ? "(No title)" : e.getTitle());
            long t = e.getStartTime() == null ? 0L : e.getStartTime().toDate().getTime();
            String time = t == 0L ? "" :
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(t);
            h.subtitle.setText(time);
        }
    }
}
