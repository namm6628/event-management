package com.example.myapplication;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences p = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isDark = p.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Nếu bạn dùng Glide:
        try { com.bumptech.glide.Glide.get(this).trimMemory(level); } catch (Throwable ignore) {}
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        try { com.bumptech.glide.Glide.get(this).clearMemory(); } catch (Throwable ignore) {}
    }

}
