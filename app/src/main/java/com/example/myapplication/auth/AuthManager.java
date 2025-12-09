package com.example.myapplication.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthManager {

    private static final String PREFS_AUTH = "auth_prefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_IS_ORGANIZER = "is_organizer";

    public interface BoolCallback {
        void onResult(boolean value);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
    }

    public static void login(Context ctx, String name, String email) {
        login(ctx, name, email, null);
    }

    public static void login(Context ctx,
                             String name,
                             String email,
                             @Nullable String phone) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        e.putBoolean(KEY_LOGGED_IN, true);
        e.putString(KEY_NAME, name);
        e.putString(KEY_EMAIL, email);
        if (phone != null) {
            e.putString(KEY_PHONE, phone);
        } else {
            e.remove(KEY_PHONE);
        }
        e.apply();
    }

    public static void logout(Context ctx) {
        SharedPreferences p = prefs(ctx);
        p.edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PHONE)
                .remove(KEY_AVATAR_URI)
                .remove(KEY_IS_ORGANIZER)
                .apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return prefs(ctx).getBoolean(KEY_LOGGED_IN, false);
    }

    @Nullable
    public static String getName(Context ctx) {
        return prefs(ctx).getString(KEY_NAME, "Khách mới");
    }

    @Nullable
    public static String getEmail(Context ctx) {
        return prefs(ctx).getString(KEY_EMAIL, null);
    }

    @Nullable
    public static String getPhone(Context ctx) {
        return prefs(ctx).getString(KEY_PHONE, null);
    }

    public static void setPhone(Context ctx, @Nullable String phone) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (phone == null) {
            e.remove(KEY_PHONE);
        } else {
            e.putString(KEY_PHONE, phone);
        }
        e.apply();
    }

    @Nullable
    public static String getAvatarUri(Context ctx) {
        return prefs(ctx).getString(KEY_AVATAR_URI, null);
    }

    public static void setAvatarUri(Context ctx, @Nullable String uri) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (uri == null) {
            e.remove(KEY_AVATAR_URI);
        } else {
            e.putString(KEY_AVATAR_URI, uri);
        }
        e.apply();
    }

    public static void cacheOrganizerFlag(Context ctx, boolean isOrganizer) {
        prefs(ctx).edit().putBoolean(KEY_IS_ORGANIZER, isOrganizer).apply();
    }

    public static boolean isOrganizerCached(Context ctx) {
        return prefs(ctx).getBoolean(KEY_IS_ORGANIZER, false);
    }

    public void refreshOrganizerStatus(Context ctx, BoolCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cacheOrganizerFlag(ctx, false);
            cb.onResult(false);
            return;
        }

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    boolean isOrg = false;
                    if (doc.exists()) {
                        Boolean v = doc.getBoolean("isOrganizer");
                        isOrg = (v != null && v);
                    }
                    cacheOrganizerFlag(ctx, isOrg);
                    cb.onResult(isOrg);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    boolean cached = isOrganizerCached(ctx);
                    cb.onResult(cached);
                });
    }

    public static void createUserDocumentIfNeeded(Context ctx) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("displayName", getName(ctx));
        data.put("email", getEmail(ctx));
        data.put("phone", getPhone(ctx));

        db.collection("users").document(uid).set(data);
    }
}
