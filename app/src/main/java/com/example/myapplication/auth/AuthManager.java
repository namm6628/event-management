package com.example.myapplication.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class AuthManager {
    private static final String PREF = "auth_pref";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";

    private static final String KEY_AVATAR_URI = "avatar_uri";

    public static void setAvatarUri(Context c, String uri) {
        prefs(c).edit().putString(KEY_AVATAR_URI, uri).apply();
    }
    @Nullable

    public static String getAvatarUri(Context c) {
        return prefs(c).getString(KEY_AVATAR_URI, null);
    }


    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(Context c) { return prefs(c).getBoolean(KEY_LOGGED_IN, false); }
    public static String getName(Context c)     { return prefs(c).getString(KEY_NAME, "Khách mới"); }
    public static String getEmail(Context c)    { return prefs(c).getString(KEY_EMAIL, "Đăng nhập để sử dụng toàn bộ dịch vụ"); }

    public static void login(Context c, String name, String email) {
        prefs(c).edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_NAME, name)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    public static void logout(Context c) {
        prefs(c).edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .apply();
    }

}
