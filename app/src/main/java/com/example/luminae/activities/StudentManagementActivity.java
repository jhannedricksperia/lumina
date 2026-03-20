package com.example.luminae.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStudentManagementBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StudentManagementActivity extends AppCompatActivity {

    private ActivityStudentManagementBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> allStudents = new ArrayList<>();
    private List<DocumentSnapshot> filtered = new ArrayList<>();
    private StudentAdapter adapter;
    private String filterStatus = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStudentManagementBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new StudentAdapter();
        b.recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerStudents.setAdapter(adapter);

        // Search
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });

        // Status filter chips
        b.chipGroupStatus.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActive.isChecked())  filterStatus = "Active";
            else if (b.chipBlocked.isChecked()) filterStatus = "Blocked";
            else                                filterStatus = "All";
            applyFilter();
        });

        loadStudents();
    }

    private void loadStudents() {
        db.collection("users")
                .whereEqualTo("role", "student")
                .orderBy("fName")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    allStudents = snap.getDocuments();
                    applyFilter();
                });
    }

    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : allStudents) {
            String name = (doc.getString("fName") + " " + doc.getString("lName")).toLowerCase();
            String email = doc.getString("email") != null ? doc.getString("email").toLowerCase() : "";
            String status = doc.getString("status") != null ? doc.getString("status") : "";
            boolean matchesSearch = q.isEmpty() || name.contains(q) || email.contains(q);
            boolean matchesStatus = filterStatus.equals("All") || filterStatus.equals(status);
            if (matchesSearch && matchesStatus) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " student(s) found");
        adapter.notifyDataSetChanged();
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    private class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvInitials, tvFullName, tvUsername, tvStatus, tvEmail,
                     tvCampus, tvCollege, tvCourse,
                     tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                     btnToggleStatus, btnView;

            VH(View v) {
                super(v);
                tvInitials      = v.findViewById(R.id.tv_initials);
                tvFullName      = v.findViewById(R.id.tv_full_name);
                tvUsername      = v.findViewById(R.id.tv_username);
                tvStatus        = v.findViewById(R.id.tv_status);
                tvEmail         = v.findViewById(R.id.tv_email);
                tvCampus        = v.findViewById(R.id.tv_campus);
                tvCollege       = v.findViewById(R.id.tv_college);
                tvCourse        = v.findViewById(R.id.tv_course);
                tvDateCreated   = v.findViewById(R.id.tv_date_created);
                tvCreatedBy     = v.findViewById(R.id.tv_created_by);
                tvDateModified  = v.findViewById(R.id.tv_date_modified);
                tvModifiedBy    = v.findViewById(R.id.tv_modified_by);
                btnToggleStatus = v.findViewById(R.id.btn_toggle_status);
                btnView         = v.findViewById(R.id.btn_view);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_student, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String fName = orDash(doc.getString("fName"));
            String lName = orDash(doc.getString("lName"));
            String full  = fName + " " + lName;
            String status = orDash(doc.getString("status"));

            h.tvInitials.setText(initials(fName, lName));
            h.tvFullName.setText(full);
            h.tvUsername.setText("@" + orDash(doc.getString("username")));
            h.tvEmail.setText(orDash(doc.getString("email")));
            h.tvCampus.setText(orDash(doc.getString("campus")));
            h.tvCollege.setText(orDash(doc.getString("college")));
            h.tvCourse.setText(orDash(doc.getString("course")));
            h.tvStatus.setText(status);

            // Status badge style
            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggleStatus.setText("Block");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggleStatus.setText("Unblock");
            }

            // Timestamps
            Timestamp created  = doc.getTimestamp("createdAt");
            Timestamp modified = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(created  != null ? sdf.format(created.toDate())  : "—");
            h.tvDateModified.setText(modified != null ? sdf.format(modified.toDate()) : "—");
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Toggle Block/Active
            h.btnToggleStatus.setOnClickListener(v -> {
                String newStatus = "Active".equals(status) ? "Blocked" : "Active";
                new MaterialAlertDialogBuilder(StudentManagementActivity.this)
                        .setTitle(newStatus.equals("Blocked") ? "Block Student" : "Unblock Student")
                        .setMessage("Set status to " + newStatus + " for " + full + "?")
                        .setPositiveButton("Confirm", (d, w) -> {
                            String adminUid = FirebaseAuth.getInstance().getUid();
                            db.collection("users").document(doc.getId())
                                    .update("status", newStatus,
                                            "modifiedAt", Timestamp.now(),
                                            "modifiedBy", adminUid);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // View detail (expand - could open detail activity)
            h.btnView.setOnClickListener(v -> {
                // TODO: navigate to student detail screen
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private String orDash(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }
    private String initials(String f, String l) {
        String a = (f.length() > 0) ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String b = (l.length() > 0) ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + b;
    }
}
