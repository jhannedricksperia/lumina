package com.example.luminae.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.os.Build;
import com.example.luminae.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> notifPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notifPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> proceedToNextScreen()
        );

        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            proceedToNextScreen();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            proceedToNextScreen();
            return;
        }
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void proceedToNextScreen() {
        SessionManager session = new SessionManager(this);

        new Handler().postDelayed(() -> {
            Intent intent;
            if (session.isLoggedIn()) {
                String role = session.getRole();
                switch (role != null ? role : "") {
                    case "admin":
                        intent = new Intent(this, AdminActivity.class);
                        break;
                    case "staff":
                        intent = new Intent(this, StaffActivity.class);
                        break;
                    case "student":
                    default:
                        intent = new Intent(this, StudentActivity.class);
                        break;
                }
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1500);
    }
}
