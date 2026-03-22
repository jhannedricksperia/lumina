package com.example.luminae.staff.fragments;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.example.luminae.databinding.FragmentStaffDashboardBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StaffDashboardFragment extends Fragment {

    private FragmentStaffDashboardBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentFilter = "today";
    private Date customDate = null;
    private String staffUid = "";
    private String staffFirstName = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b    = FragmentStaffDashboardBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loadStaffInfo();
        setupChipFilter();
        setTodayDate();

        return b.getRoot();
    }

    private void loadStaffInfo() {
        staffUid = auth.getUid() != null ? auth.getUid() : "";
        if (staffUid.isEmpty()) return;

        db.collection("users").document(staffUid).get().addOnSuccessListener(doc -> {
            if (b == null || doc == null) return;
            staffFirstName = doc.getString("fName") != null ? doc.getString("fName") : "Staff";
            b.tvGreeting.setText("Hello, " + staffFirstName + "!");
            // FIX: guard on staffUid (not staffEmail) since postedBy stores UID
            loadData();
        });
    }

    private void setTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        b.tvTodayDate.setText(sdf.format(new Date()));
    }

    private void setupChipFilter() {
        b.chipToday.setOnClickListener(v -> {
            currentFilter = "today"; customDate = null;
            b.tvFilterLabel.setText("Today");
            loadData();
        });
        b.chipWeek.setOnClickListener(v -> {
            currentFilter = "week"; customDate = null;
            b.tvFilterLabel.setText("This Week");
            loadData();
        });
        b.chipMonth.setOnClickListener(v -> {
            currentFilter = "month"; customDate = null;
            b.tvFilterLabel.setText("This Month");
            loadData();
        });
        b.chipCustom.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            customDate = c.getTime();
            currentFilter = "custom";
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            b.tvFilterLabel.setText(sdf.format(customDate));
            loadData();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private Timestamp getFromTimestamp() {
        Calendar c = Calendar.getInstance();
        switch (currentFilter) {
            case "week":
                c.add(Calendar.DAY_OF_YEAR, -7);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                break;
            case "month":
                c.add(Calendar.MONTH, -1);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                break;
            case "custom":
                if (customDate != null) { c.setTime(customDate); }
                break;
            default: // today
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
        }
        return new Timestamp(c.getTime());
    }

    private void loadData() {
        // FIX: guard on staffUid, not staffEmail — postedBy field stores UID
        if (staffUid.isEmpty()) return;
        Timestamp from = getFromTimestamp();
        loadPostCounts(from);
        loadInteractionStats();
        loadPostsLineChart();
    }

    private void loadPostCounts(Timestamp from) {
        db.collection("announcements")
                .whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    if (b == null) return;
                    // Filter by date in code
                    int count = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null && ts.compareTo(from) >= 0) count++;
                    }
                    b.tvAnnouncementCount.setText(String.valueOf(count));
                });

        db.collection("events")
                .whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    if (b == null) return;
                    int count = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null && ts.compareTo(from) >= 0) count++;
                    }
                    b.tvEventCount.setText(String.valueOf(count));
                });
    }

    // ── Interaction Stats ─────────────────────────────────────────────────────
    // FIX: removed unused `from` param — these are lifetime totals across all posts
    private void loadInteractionStats() {
        final long[] totalLikes    = {0};
        final long[] totalComments = {0};
        final long[] totalGoers    = {0};
        final int[]  done          = {0};

        db.collection("announcements")
                .whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Long l = doc.getLong("likeCount");
                        Long c = doc.getLong("commentCount");
                        if (l != null) totalLikes[0]    += l;
                        if (c != null) totalComments[0] += c;
                    }
                    if (++done[0] == 2) updateInteractionUI(totalLikes[0], totalComments[0], totalGoers[0]);
                });

        db.collection("events")
                .whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Long l = doc.getLong("likeCount");
                        Long c = doc.getLong("commentCount");
                        Long p = doc.getLong("participantCount");
                        if (l != null) totalLikes[0]    += l;
                        if (c != null) totalComments[0] += c;
                        if (p != null) totalGoers[0]    += p;
                    }
                    if (++done[0] == 2) updateInteractionUI(totalLikes[0], totalComments[0], totalGoers[0]);
                });
    }

    private void updateInteractionUI(long likes, long comments, long goers) {
        if (b == null) return;
        b.tvTotalLikes.setText(String.valueOf(likes));
        b.tvTotalComments.setText(String.valueOf(comments));
        b.tvTotalGoers.setText(String.valueOf(goers));
    }

    // ── Line Chart: My Posts Last 7 Days ─────────────────────────────────────
    private void loadPostsLineChart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        // FIX: 8 boundaries to define 7 day-buckets (days[0]..days[7])
        // days[0] = 6 days ago midnight, days[7] = tomorrow midnight (covers all of today)
        Date[] days = new Date[8];
        String[] dayLabels = new String[7];
        String[] dow = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        for (int i = 6; i >= 0; i--) {
            Calendar d = (Calendar) c.clone();
            d.add(Calendar.DAY_OF_YEAR, -i);
            days[6 - i]      = d.getTime();
            dayLabels[6 - i] = dow[d.get(Calendar.DAY_OF_WEEK) - 1];
        }
        // FIX: add tomorrow as the upper boundary so today's posts are included
        Calendar tomorrow = (Calendar) c.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        days[7] = tomorrow.getTime();

        int[] aCounts = new int[7], eCounts = new int[7];
        final int[] done = {0};

        db.collection("announcements").whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts == null) continue;
                        Date postDate = ts.toDate();
                        // FIX: loop i < 7 and check between days[i] and days[i+1]
                        for (int i = 0; i < 7; i++) {
                            if (!postDate.before(days[i]) && postDate.before(days[i + 1])) {
                                aCounts[i]++;
                                break;
                            }
                        }
                    }
                    if (++done[0] == 2) buildLineChart(aCounts, eCounts, dayLabels);
                });

        db.collection("events").whereEqualTo("postedBy", staffUid)
                .get().addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts == null) continue;
                        Date postDate = ts.toDate();
                        // FIX: loop i < 7 and check between days[i] and days[i+1]
                        for (int i = 0; i < 7; i++) {
                            if (!postDate.before(days[i]) && postDate.before(days[i + 1])) {
                                eCounts[i]++;
                                break;
                            }
                        }
                    }
                    if (++done[0] == 2) buildLineChart(aCounts, eCounts, dayLabels);
                });
    }

    private void buildLineChart(int[] aCounts, int[] eCounts, String[] labels) {
        if (b == null) return;
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

        b.chartMyPosts.setData(new LineData(aDs, eDs));
        b.chartMyPosts.setBackgroundColor(Color.TRANSPARENT);
        b.chartMyPosts.getDescription().setEnabled(false);
        b.chartMyPosts.getLegend().setTextColor(Color.WHITE);
        b.chartMyPosts.setDrawGridBackground(false);
        b.chartMyPosts.getAxisRight().setEnabled(false);
        b.chartMyPosts.getAxisLeft().setTextColor(Color.WHITE);
        b.chartMyPosts.getAxisLeft().setGridColor(0x22FFFFFF);
        XAxis x = b.chartMyPosts.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(Color.WHITE);
        x.setGridColor(Color.TRANSPARENT);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        b.chartMyPosts.invalidate();
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}