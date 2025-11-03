package com.example.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.myapplication.databinding.ActivityMainBinding;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;


import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import com.google.firebase.firestore.Source;


import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;

    private static final String TAG = "FB_TEST";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());


        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);


        setSupportActionBar(binding.toolbar);

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.sp_black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.sp_black));

// Icon bar màu sáng (trắng)
        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);     // false = icon sáng trên nền tối
        wic.setAppearanceLightNavigationBars(false);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host != null) {
            NavController navController = host.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        } else {
            Log.w(TAG, "NavHostFragment not found. Check layout id=nav_host_fragment");
        }

        Log.d(TAG, "onCreate: app started");   // mốc nhìn trên Logcat
        testFirestore();

    }

    private void testFirestore() {
        Log.d(TAG, "testFirestore: begin");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("events").get(Source.SERVER)   // <-- ép server
                .addOnSuccessListener(snap -> {
                    Log.d(TAG, "read OK (SERVER), size=" + snap.size()
                            + ", fromCache=" + snap.getMetadata().isFromCache());
                    for (QueryDocumentSnapshot doc : snap) {
                        Log.d(TAG, "event: " + doc.getId() + " | " + doc.getString("title"));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "read FAIL", e));
    }



}
