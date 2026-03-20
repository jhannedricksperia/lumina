package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);

        new Handler().postDelayed(() -> {
            Intent intent = null;
            if (session.isLoggedIn()) {
                // Route based on role
                String role = session.getRole();
                switch (role != null ? role : "") {
                    case "Admin":
                        intent = new Intent(this, AdminActivity.class);
                        break;
                    case "staff":
                     //   intent = new Intent(this, StaffActivity.class);
                        break;
                    default:
                     //   intent = new Intent(this, StudentActivity.class);
                        break;
                }
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1500); // 1.5 second splash delay
    }
}