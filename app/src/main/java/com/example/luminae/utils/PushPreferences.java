package com.example.luminae.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Local toggle for push notifications; mirrored to Firestore for server-side FCM.
 */
public final class PushPreferences {

    private static final String PREFS = "lumina_prefs";
    private static final String KEY_PUSH = "push_notifications_enabled";

    private PushPreferences() {}

    public static boolean isPushEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PUSH, true);
    }

    public static void setPushEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PUSH, enabled).apply();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("pushNotificationsEnabled", enabled);
        FirebaseFirestore.getInstance().collection("users").document(u.getUid())
                .set(m, SetOptions.merge());
    }

    /** Apply value from Firestore when present (e.g. user changed on another device). */
    public static void mergeFromFirestore(Context context, Boolean pushEnabled) {
        if (pushEnabled == null) return;
        prefs(context).edit().putBoolean(KEY_PUSH, pushEnabled).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
