package com.example.myapplication;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.attendee.ticket.TicketNavigationHost;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements TicketNavigationHost {

    private ActivityMainBinding binding;
    private AppBarConfiguration appBarConfiguration;
    private NavController navController;

    public static final String EXTRA_START_DEST = "EXTRA_START_DEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Status bar / nav bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setSupportActionBar(binding.toolbar);

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.sp_black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.sp_black));

        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);
        wic.setAppearanceLightNavigationBars(false);

        // NavController
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // NavGraph
        NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.nav_graph);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            navGraph.setStartDestination(R.id.loginFragment);
        } else {
            navGraph.setStartDestination(R.id.homeFragment);
        }
        navController.setGraph(navGraph);

        // Top-level destinations
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.exploreFragment,
                R.id.ticketsFragment,
                R.id.profileFragment,
                R.id.loginFragment,
                R.id.registerFragment
        ).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Bottom nav + navController
        BottomNavigationView bottomNav = binding.bottomNav;
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Ẩn bottom nav khi login / register
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            if (destId == R.id.loginFragment || destId == R.id.registerFragment) {
                binding.bottomNav.setVisibility(View.GONE);
            } else {
                binding.bottomNav.setVisibility(View.VISIBLE);
            }
        });

        // 👉 Chỉ override tab nếu:
        // - ĐÃ login
        // - Intent có EXTRA_START_DEST (ví dụ từ OrderSuccessActivity)
        int requestedDest = getIntent().getIntExtra(EXTRA_START_DEST, -1);
        if (currentUser != null && requestedDest != -1) {
            bottomNav.setSelectedItemId(requestedDest);
        }
    }

    // Xử lý nút "Mua vé ngay" ở ticket tab (đi sang Explore)
    @Override
    public void onBuyTicketClicked() {
        if (navController == null) return;
        // Chỉ cần setSelectedItemId, NavigationUI sẽ tự navigate
        binding.bottomNav.setSelectedItemId(R.id.exploreFragment);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();

        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
