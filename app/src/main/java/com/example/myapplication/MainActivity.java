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

import com.example.myapplication.attendee.ticket.TicketNavigationHost;  // 👈 thêm dòng này
import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements TicketNavigationHost { // 👈 implement interface

    private ActivityMainBinding binding;
    private AppBarConfiguration appBarConfiguration;
    private NavController navController; // 👈 moved lên biến global

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

        // Top-level destinations (không hiện back)
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.exploreFragment,
                R.id.ticketsFragment,
                R.id.profileFragment,
                R.id.loginFragment,
                R.id.registerFragment
        ).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Bottom nav
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        // Ẩn bottom nav khi login / register
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            if (destId == R.id.loginFragment || destId == R.id.registerFragment) {
                binding.bottomNav.setVisibility(View.GONE);
            } else {
                binding.bottomNav.setVisibility(View.VISIBLE);
            }
        });
    }

    // 🔥 Hàm xử lý nút "Mua vé ngay"
    @Override
    public void onBuyTicketClicked() {
        if (navController == null) return;

        // Chọn tab Explore ở bottom nav
        binding.bottomNav.setSelectedItemId(R.id.exploreFragment);

        // Điều hướng sang ExploreFragment
        navController.navigate(R.id.exploreFragment);
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
