package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.luminae.R;
import com.example.luminae.admin.fragments.AdminAnnouncementFragment;
import com.example.luminae.admin.fragments.AdminDashboardFragment;
import com.example.luminae.admin.fragments.AdminEventFragment;
import com.example.luminae.admin.fragments.AdminProfileFragment;
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
        binding.navAnnouncements.setOnClickListener(v -> {
            loadFragment(new AdminAnnouncementFragment(), "announcements");
            setActiveTab(v);
        });
        binding.navEvents.setOnClickListener(v -> {
            loadFragment(new AdminEventFragment(), "events");
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
        binding.navAnnouncements.setSelected(active == binding.navAnnouncements);
        binding.navEvents.setSelected(active == binding.navEvents);
        binding.navProfile.setSelected(active == binding.navProfile);
    }
}
