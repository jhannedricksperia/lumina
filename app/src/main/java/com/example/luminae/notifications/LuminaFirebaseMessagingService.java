package com.example.luminae.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.luminae.R;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.utils.FcmRegistrationHelper;
import com.example.luminae.activities.SplashActivity;
import com.example.luminae.utils.PushPreferences;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class LuminaFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "lumina_push";

    @Override
    public void onNewToken(@NonNull String token) {
        FcmRegistrationHelper.saveTokenToUser(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        if (!PushPreferences.isPushEnabled(this)) return;

        RemoteMessage.Notification n = message.getNotification();
        String title = n != null ? n.getTitle() : "LUMINA";
        String body   = n != null ? n.getBody() : "";

        Map<String, String> data = message.getData();
        if ((title == null || title.isEmpty()) && data.containsKey("title"))
            title = data.get("title");
        if ((body == null || body.isEmpty()) && data.containsKey("body"))
            body = data.get("body");

        ensureChannel();

        Intent intent = buildTapIntent(data);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title != null ? title : "LUMINA")
                .setContentText(body != null ? body : "")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Ensure the notification vibrates (Android 8+ uses the channel config).
                .setVibrate(new long[]{0, 400, 200, 400});

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) System.currentTimeMillis(), b.build());
    }

    private Intent buildTapIntent(Map<String, String> data) {
        String refId = data.get("refId");
        String refCol = data.get("refCollection");
        if (refId != null && refCol != null) {
            Intent i = new Intent(this, PostDetailActivity.class);
            i.putExtra("docId", refId);
            i.putExtra("type", refCol.contains("event") ? "Event" : "Announcement");
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return i;
        }
        return new Intent(this, SplashActivity.class);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "LUMINA notifications",
                NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400});
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

}
