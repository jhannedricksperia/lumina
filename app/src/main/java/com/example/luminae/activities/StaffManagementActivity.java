package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStaffManagementBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StaffManagementActivity extends AppCompatActivity {

    private ActivityStaffManagementBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> allStaff = new ArrayList<>(), filtered = new ArrayList<>();
    private StaffAdapter adapter;
    private String filterStatus = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStaffManagementBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new StaffAdapter();
        b.recyclerStaff.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerStaff.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });

        b.chipGroupStatus.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActive.isChecked())  filterStatus = "Active";
            else if (b.chipBlocked.isChecked()) filterStatus = "Blocked";
            else                                filterStatus = "All";
            applyFilter();
        });

        b.btnAddStaff.setOnClickListener(v -> openStaffForm(null));

        loadStaff();
    }

    private void loadStaff() {
        db.collection("users")
                .whereEqualTo("role", "staff")
                .orderBy("fName")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    allStaff = snap.getDocuments();
                    applyFilter();
                });
    }

    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : allStaff) {
            String name   = (doc.getString("fName") + " " + doc.getString("lName")).toLowerCase();
            String desig  = doc.getString("designation") != null ? doc.getString("designation").toLowerCase() : "";
            String status = doc.getString("status") != null ? doc.getString("status") : "";
            boolean matchSearch = q.isEmpty() || name.contains(q) || desig.contains(q);
            boolean matchStatus = filterStatus.equals("All") || filterStatus.equals(status);
            if (matchSearch && matchStatus) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " staff member(s) found");
        adapter.notifyDataSetChanged();
    }

    private void openStaffForm(DocumentSnapshot existing) {
        Intent i = new Intent(this, StaffFormActivity.class);
        if (existing != null) i.putExtra("docId", existing.getId());
        startActivity(i);
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    private class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvInitials, tvFullName, tvDesignation, tvStatus,
                     tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                     btnToggle, btnEdit, btnDelete;

            VH(View v) {
                super(v);
                tvInitials     = v.findViewById(R.id.tv_initials);
                tvFullName     = v.findViewById(R.id.tv_full_name);
                tvDesignation  = v.findViewById(R.id.tv_designation);
                tvStatus       = v.findViewById(R.id.tv_status);
                tvDateCreated  = v.findViewById(R.id.tv_date_created);
                tvCreatedBy    = v.findViewById(R.id.tv_created_by);
                tvDateModified = v.findViewById(R.id.tv_date_modified);
                tvModifiedBy   = v.findViewById(R.id.tv_modified_by);
                btnToggle      = v.findViewById(R.id.btn_toggle_status);
                btnEdit        = v.findViewById(R.id.btn_edit);
                btnDelete      = v.findViewById(R.id.btn_delete);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_staff, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            String fName  = orDash(doc.getString("fName"));
            String lName  = orDash(doc.getString("lName"));
            String status = orDash(doc.getString("status"));

            h.tvInitials.setText(initials(fName, lName));
            h.tvFullName.setText(fName + " " + lName);
            h.tvDesignation.setText(orDash(doc.getString("designation")));
            h.tvStatus.setText(status);

            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggle.setText("Block");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggle.setText("Unblock");
            }

            Timestamp created  = doc.getTimestamp("createdAt");
            Timestamp modified = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(created  != null ? sdf.format(created.toDate())  : "—");
            h.tvDateModified.setText(modified != null ? sdf.format(modified.toDate()) : "—");
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Toggle block
            h.btnToggle.setOnClickListener(v -> {
                String ns = "Active".equals(status) ? "Blocked" : "Active";
                new MaterialAlertDialogBuilder(StaffManagementActivity.this)
                        .setTitle(ns.equals("Blocked") ? "Block Staff" : "Unblock Staff")
                        .setMessage("Set " + fName + " " + lName + " as " + ns + "?")
                        .setPositiveButton("Confirm", (d, w) ->
                                db.collection("users").document(doc.getId())
                                        .update("status", ns, "modifiedAt", Timestamp.now(),
                                                "modifiedBy", FirebaseAuth.getInstance().getUid()))
                        .setNegativeButton("Cancel", null).show();
            });

            // Edit
            h.btnEdit.setOnClickListener(v -> openStaffForm(doc));

            // Delete
            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(StaffManagementActivity.this)
                            .setTitle("Delete Staff")
                            .setMessage("Permanently delete " + fName + " " + lName + "?")
                            .setPositiveButton("Delete", (d, w) ->
                                    db.collection("users").document(doc.getId()).delete())
                            .setNegativeButton("Cancel", null).show());
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private String orDash(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }
    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String b = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + b;
    }
}
