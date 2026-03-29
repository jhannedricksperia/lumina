package com.example.luminae.admin.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.example.luminae.activities.*;
import com.example.luminae.databinding.FragmentAdminManagementBinding;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminManagementFragment extends Fragment {

    private FragmentAdminManagementBinding b;
    private FirebaseFirestore db;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle saved) {
        b  = FragmentAdminManagementBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();

        loadCounts();
        wireNavigation();
        return b.getRoot();
    }

    private void wireNavigation() {
        b.cardStudentMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), StudentManagementActivity.class)));
        b.cardStaffMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), StaffManagementActivity.class)));
        b.cardCampusMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CampusManagementActivity.class)));
        b.cardCollegeMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CollegeManagementActivity.class)));
        b.cardCourseMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CourseManagementActivity.class)));
        b.cardActivityLogMgmt.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ActivityLogActivity.class)));
    }

    private void loadCounts() {
        db.collection("users").get().addOnSuccessListener(s -> {
            if (b == null) return;
            long students = 0, staff = 0;
            for (var d : s.getDocuments()) {
                String role = d.getString("role");
                if ("student".equals(role)) students++;
                if ("staff".equals(role))   staff++;
            }
            b.tvStudentCount.setText(students + " students");
            b.tvStaffCount.setText(staff + " staff");
        });
        db.collection("campuses").get().addOnSuccessListener(s -> {
            if (b == null) return;
            b.tvCampusCount.setText(s.size() + " campuses");
        });
        db.collection("colleges").get().addOnSuccessListener(s -> {
            if (b == null) return;
            b.tvCollegeCount.setText(s.size() + " colleges");
        });
        db.collection("courses").get().addOnSuccessListener(s -> {
            if (b == null) return;
            b.tvCourseCount.setText(s.size() + " courses");
        });
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}