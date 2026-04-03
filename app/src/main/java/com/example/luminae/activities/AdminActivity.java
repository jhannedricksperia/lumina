package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.luminae.R;
import com.example.luminae.admin.fragments.*;
import com.example.luminae.databinding.ActivityAdminBinding;

public class AdminActivity extends AppCompatActivity {

    private ActivityAdminBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadFragment(new AdminDashboardFragment(), "dashboard");
        setActiveTab(binding.navDashboard);

        binding.navDashboard.setOnClickListener(v -> {
            loadFragment(new AdminDashboardFragment(), "dashboard");
            setActiveTab(v);
        });
        binding.navFeed.setOnClickListener(v -> {
            loadFragment(new AdminFeedFragment(), "feed");
            setActiveTab(v);
        });
        binding.navManagement.setOnClickListener(v -> {
            loadFragment(new AdminManagementFragment(), "management");
            setActiveTab(v);
        });
        binding.navNotifications.setOnClickListener(v -> {
            loadFragment(new AdminNotificationFragment(), "notifications");
            setActiveTab(v);
        });
        binding.navProfile.setOnClickListener(v -> {
            loadFragment(new AdminProfileFragment(), "profile");
            setActiveTab(v);
        });
    }

    private void loadFragment(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.admin_fragment_container, fragment, tag)
                .commit();
    }

    private void setActiveTab(View active) {
        binding.navDashboard.setSelected(active == binding.navDashboard);
        binding.navFeed.setSelected(active == binding.navFeed);
        binding.navManagement.setSelected(active == binding.navManagement);
        binding.navNotifications.setSelected(active == binding.navNotifications);
        binding.navProfile.setSelected(active == binding.navProfile);
    }
}