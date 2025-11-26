package com.example.myapplication.attendee.ticket;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myapplication.R;
import com.example.myapplication.attendee.notification.AttendeeNotificationsActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Tab "Vé của tôi" trong bottom nav.
 * Layout giống TicketBox: Tab luôn hiển thị, empty nằm trong từng tab.
 */
public class TicketsFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    public TicketsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Dùng layout activity_my_tickets nhưng bỏ phần empty-all
        View root = inflater.inflate(R.layout.activity_my_tickets, container, false);

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle("Vé của tôi");
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuClick);

        tabLayout = root.findViewById(R.id.tabLayout);
        viewPager = root.findViewById(R.id.viewPager);

        // ẨN empty-all fullscreen HOÀN TOÀN, để empty trong tab lo
        View emptyAll = root.findViewById(R.id.layoutEmptyAll);
        if (emptyAll != null) emptyAll.setVisibility(View.GONE);

        setupViewPager();

        return root;
    }

    private boolean onToolbarMenuClick(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            startActivity(
                    new android.content.Intent(
                            requireContext(),
                            AttendeeNotificationsActivity.class
                    )
            );
            return true;
        }
        return false;
    }

    private void setupViewPager() {

        FragmentStateAdapter adapter = new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) return new UpcomingTicketsFragment();
                return new PastTicketsFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        };

        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, pos) -> {
            if (pos == 0) tab.setText("Sắp diễn ra");
            else tab.setText("Đã kết thúc");
        }).attach();
    }
}
