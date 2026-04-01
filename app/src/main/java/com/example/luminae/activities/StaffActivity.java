package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStaffBinding;
import com.example.luminae.staff.fragments.StaffDashboardFragment;
import com.example.luminae.staff.fragments.StaffNotificationFragment;
import com.example.luminae.staff.fragments.StaffPostFragment;
import com.example.luminae.staff.fragments.StaffProfileFragment;

public class StaffActivity extends AppCompatActivity {

    private ActivityStaffBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadFragment(new StaffDashboardFragment(), "dashboard");
        setActiveTab(binding.navDashboard);

        binding.navDashboard.setOnClickListener(v -> {
            loadFragment(new StaffDashboardFragment(), "dashboard");
            setActiveTab(v);
        });
        binding.navPost.setOnClickListener(v -> {
            loadFragment(new StaffPostFragment(), "post");
            setActiveTab(v);
        });
        binding.navNotifications.setOnClickListener(v -> {
            loadFragment(new StaffNotificationFragment(), "notifications");
            setActiveTab(v);
        });
        binding.navProfile.setOnClickListener(v -> {
            loadFragment(new StaffProfileFragment(), "profile");
            setActiveTab(v);
        });
    }

    private void loadFragment(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.staff_fragment_container, fragment, tag)
                .commit();
    }

    private void setActiveTab(View active) {
        binding.navDashboard.setSelected(active == binding.navDashboard);
        binding.navPost.setSelected(active == binding.navPost);
        binding.navNotifications.setSelected(active == binding.navNotifications);
        binding.navProfile.setSelected(active == binding.navProfile);
    }
}
