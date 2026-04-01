package com.example.luminae.utils;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers FCM token on the signed-in user document for Cloud Messaging delivery.
 */
public final class FcmRegistrationHelper {

    private FcmRegistrationHelper() {}

    public static void register(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    saveTokenToUser(task.getResult());
                });
    }

    public static void saveTokenToUser(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || token == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);
        data.put("fcmTokenUpdatedAt", com.google.firebase.Timestamp.now());
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(data, SetOptions.merge());
    }

    /** Best-effort synchronous token refresh (for tests). */
    public static void registerBlocking(Context context) throws Exception {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);
        data.put("fcmTokenUpdatedAt", com.google.firebase.Timestamp.now());
        Tasks.await(FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(data, SetOptions.merge()));
    }
}
