package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStudentBinding;
import com.example.luminae.student.fragments.StudentFeedFragment;
import com.example.luminae.student.fragments.StudentNotificationFragment;
import com.example.luminae.student.fragments.StudentEventsFragment;
import com.example.luminae.student.fragments.StudentProfileFragment;

public class StudentActivity extends AppCompatActivity {

    private ActivityStudentBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadFragment(new StudentFeedFragment(), "feed");
        setActiveTab(binding.navFeed);

        binding.navFeed.setOnClickListener(v -> {
            loadFragment(new StudentFeedFragment(), "feed");
            setActiveTab(v);
        });
        binding.navNotifications.setOnClickListener(v -> {
            loadFragment(new StudentNotificationFragment(), "notifications");
            setActiveTab(v);
        });
        binding.navEvents.setOnClickListener(v -> {
            loadFragment(new StudentEventsFragment(), "events");
            setActiveTab(v);
        });
        binding.navProfile.setOnClickListener(v -> {
            loadFragment(new StudentProfileFragment(), "profile");
            setActiveTab(v);
        });
    }

    private void loadFragment(Fragment fragment, String tag) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.student_fragment_container, fragment, tag)
                .commit();
    }

    private void setActiveTab(View active) {
        binding.navFeed.setSelected(active == binding.navFeed);
        binding.navNotifications.setSelected(active == binding.navNotifications);
        binding.navEvents.setSelected(active == binding.navEvents);
        binding.navProfile.setSelected(active == binding.navProfile);
    }
}
