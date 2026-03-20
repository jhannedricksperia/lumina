package com.example.luminae.admin.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.luminae.activities.*;
import com.example.luminae.databinding.FragmentAdminDashboardBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;

public class AdminDashboardFragment extends Fragment {

    private FragmentAdminDashboardBinding b;
    private FirebaseFirestore db;
    private String currentFilter = "today"; // today | week | month | custom
    private Date customDate = null;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b = FragmentAdminDashboardBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();

        setupChipFilter();
        setupManagementCards();
        loadData();
        return b.getRoot();
    }

    // ── Filter Chips ────────────────────────────────────────────────────────
    private void setupChipFilter() {
        b.chipToday.setOnClickListener(v  -> { currentFilter = "today";  customDate = null; loadData(); });
        b.chipWeek.setOnClickListener(v   -> { currentFilter = "week";   customDate = null; loadData(); });
        b.chipMonth.setOnClickListener(v  -> { currentFilter = "month";  customDate = null; loadData(); });
        b.chipCustom.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            c.set(y, m, d, 0, 0, 0);
            customDate = c.getTime();
            currentFilter = "custom";
            b.tvDateLabel.setText(d + "/" + (m + 1) + "/" + y);
            loadData();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ── Management card navigation ───────────────────────────────────────────
    private void setupManagementCards() {
        b.cardStudentMgmt.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), StudentManagementActivity.class)));
        b.cardStaffMgmt.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), StaffManagementActivity.class)));
        b.cardCampusMgmt.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), CampusManagementActivity.class)));
        b.cardCollegeMgmt.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), CollegeManagementActivity.class)));
        b.cardCourseMgmt.setOnClickListener(v ->
                startActivity(new android.content.Intent(getActivity(), CourseManagementActivity.class)));
    }

    // ── Data Loading ─────────────────────────────────────────────────────────
    private void loadData() {
        Timestamp from = getFromTimestamp();
        loadCounts(from);
        loadCampusBarChart(from);
        loadPostsLineChart(from);
        loadRolesPieChart();
    }

    private Timestamp getFromTimestamp() {
        Calendar c = Calendar.getInstance();
        switch (currentFilter) {
            case "week":   c.add(Calendar.DAY_OF_YEAR, -7); break;
            case "month":  c.add(Calendar.MONTH, -1); break;
            case "custom": if (customDate != null) { c.setTime(customDate); } break;
            default: // today
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
        }
        return new Timestamp(c.getTime());
    }

    private void loadCounts(Timestamp from) {
        // Total users
        db.collection("users").get().addOnSuccessListener(s -> {
            b.tvTotalUsers.setText(String.valueOf(s.size()));
            long active = s.getDocuments().stream()
                    .filter(d -> "Active".equals(d.getString("status"))).count();
            b.tvActiveUsers.setText(String.valueOf(active));
            long students = s.getDocuments().stream()
                    .filter(d -> "student".equals(d.getString("role"))).count();
            long staff = s.getDocuments().stream()
                    .filter(d -> "staff".equals(d.getString("role"))).count();
            b.tvStudentCount.setText(students + " students");
            b.tvStaffCount.setText(staff + " staff");
        });

        // Announcements
        db.collection("announcements")
                .whereGreaterThanOrEqualTo("createdAt", from)
                .get().addOnSuccessListener(s -> b.tvTotalAnnouncements.setText(String.valueOf(s.size())));

        // Events
        db.collection("events")
                .whereGreaterThanOrEqualTo("createdAt", from)
                .get().addOnSuccessListener(s -> b.tvTotalEvents.setText(String.valueOf(s.size())));
    }

    // ── Bar Chart: Users per Campus ──────────────────────────────────────────
    private void loadCampusBarChart(Timestamp from) {
        db.collection("users").get().addOnSuccessListener(snap -> {
            Map<String, Integer> campusMap = new LinkedHashMap<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                String campus = doc.getString("campus");
                if (campus != null && !campus.isEmpty())
                    campusMap.put(campus, campusMap.getOrDefault(campus, 0) + 1);
            }
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>(campusMap.keySet());
            for (int i = 0; i < labels.size(); i++)
                entries.add(new BarEntry(i, campusMap.get(labels.get(i))));

            BarDataSet ds = new BarDataSet(entries, "Users");
            ds.setColor(0xFFB71C1C);
            ds.setValueTextColor(Color.WHITE);
            ds.setValueTextSize(10f);

            BarData data = new BarData(ds);
            data.setBarWidth(0.6f);
            b.chartUsersCampus.setData(data);
            styleBarChart(labels);
        });
    }

    private void styleBarChart(List<String> labels) {
        b.chartUsersCampus.setBackgroundColor(Color.TRANSPARENT);
        b.chartUsersCampus.getDescription().setEnabled(false);
        b.chartUsersCampus.getLegend().setEnabled(false);
        b.chartUsersCampus.setDrawGridBackground(false);
        b.chartUsersCampus.getAxisRight().setEnabled(false);
        b.chartUsersCampus.getAxisLeft().setTextColor(Color.WHITE);
        b.chartUsersCampus.getAxisLeft().setGridColor(0x22FFFFFF);
        XAxis x = b.chartUsersCampus.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setGridColor(Color.TRANSPARENT);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        b.chartUsersCampus.invalidate();
    }

    // ── Line Chart: Posts over time (last 7 days) ────────────────────────────
    private void loadPostsLineChart(Timestamp from) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
        Date[] days = new Date[7];
        String[] dayLabels = new String[7];
        String[] dow = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        for (int i = 6; i >= 0; i--) {
            Calendar d = (Calendar) c.clone();
            d.add(Calendar.DAY_OF_YEAR, -(i));
            days[6 - i] = d.getTime();
            dayLabels[6 - i] = dow[d.get(Calendar.DAY_OF_WEEK) - 1];
        }

        final int[] announceCounts = new int[7];
        final int[] eventCounts = new int[7];
        final int[] done = {0};

        db.collection("announcements").get().addOnSuccessListener(snap -> {
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Timestamp ts = doc.getTimestamp("createdAt");
                if (ts == null) continue;
                for (int i = 0; i < 6; i++) {
                    if (ts.toDate().after(days[i]) && ts.toDate().before(days[i + 1]))
                        announceCounts[i]++;
                }
            }
            if (++done[0] == 2) buildLineChart(announceCounts, eventCounts, dayLabels);
        });

        db.collection("events").get().addOnSuccessListener(snap -> {
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Timestamp ts = doc.getTimestamp("createdAt");
                if (ts == null) continue;
                for (int i = 0; i < 6; i++) {
                    if (ts.toDate().after(days[i]) && ts.toDate().before(days[i + 1]))
                        eventCounts[i]++;
                }
            }
            if (++done[0] == 2) buildLineChart(announceCounts, eventCounts, dayLabels);
        });
    }

    private void buildLineChart(int[] aCounts, int[] eCounts, String[] labels) {
        List<Entry> aEntries = new ArrayList<>(), eEntries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            aEntries.add(new Entry(i, aCounts[i]));
            eEntries.add(new Entry(i, eCounts[i]));
        }
        LineDataSet aDs = new LineDataSet(aEntries, "Announcements");
        aDs.setColor(0xFFFFD700); aDs.setCircleColor(0xFFFFD700);
        aDs.setValueTextColor(Color.WHITE); aDs.setLineWidth(2f);
        aDs.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet eDs = new LineDataSet(eEntries, "Events");
        eDs.setColor(0xFFEF9A9A); eDs.setCircleColor(0xFFEF9A9A);
        eDs.setValueTextColor(Color.WHITE); eDs.setLineWidth(2f);
        eDs.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        b.chartPostsTimeline.setData(new LineData(aDs, eDs));
        b.chartPostsTimeline.setBackgroundColor(Color.TRANSPARENT);
        b.chartPostsTimeline.getDescription().setEnabled(false);
        b.chartPostsTimeline.getLegend().setTextColor(Color.WHITE);
        b.chartPostsTimeline.setDrawGridBackground(false);
        b.chartPostsTimeline.getAxisRight().setEnabled(false);
        b.chartPostsTimeline.getAxisLeft().setTextColor(Color.WHITE);
        b.chartPostsTimeline.getAxisLeft().setGridColor(0x22FFFFFF);
        XAxis x = b.chartPostsTimeline.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setGridColor(Color.TRANSPARENT);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        b.chartPostsTimeline.invalidate();
    }

    // ── Pie Chart: Roles ─────────────────────────────────────────────────────
    private void loadRolesPieChart() {
        db.collection("users").get().addOnSuccessListener(snap -> {
            int students = 0, staff = 0, admins = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                String role = d.getString("role");
                if ("student".equals(role)) students++;
                else if ("staff".equals(role)) staff++;
                else if ("admin".equals(role)) admins++;
            }
            List<PieEntry> entries = new ArrayList<>();
            if (students > 0) entries.add(new PieEntry(students, "Students"));
            if (staff    > 0) entries.add(new PieEntry(staff,    "Staff"));
            if (admins   > 0) entries.add(new PieEntry(admins,   "Admin"));

            PieDataSet ds = new PieDataSet(entries, "");
            ds.setColors(0xFFB71C1C, 0xFFFFD700, 0xFF90CAF9);
            ds.setValueTextColor(Color.WHITE);
            ds.setValueTextSize(11f);

            b.chartUserRoles.setData(new PieData(ds));
            b.chartUserRoles.setBackgroundColor(Color.TRANSPARENT);
            b.chartUserRoles.getDescription().setEnabled(false);
            b.chartUserRoles.getLegend().setTextColor(Color.WHITE);
            b.chartUserRoles.setHoleColor(Color.TRANSPARENT);
            b.chartUserRoles.setCenterText("Roles");
            b.chartUserRoles.setCenterTextColor(Color.WHITE);
            b.chartUserRoles.invalidate();
        });
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
