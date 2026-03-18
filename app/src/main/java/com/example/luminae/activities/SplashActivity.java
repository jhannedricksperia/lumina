package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.R;
import com.example.luminae.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager session = new SessionManager(this);

            Intent next;
            if (session.isLoggedIn()) {
                next = new Intent(this, MainActivity.class);
            } else {
                next = new Intent(this, LoginActivity.class);
            }
            startActivity(next);
            finish();

        }, 1500);
    }
}
