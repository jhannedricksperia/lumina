package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityGenericManagementBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CollegeManagementActivity extends AppCompatActivity {

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private ActivityGenericManagementBinding b;
    private FirebaseFirestore db;

    // Master list from Firestore, and the currently displayed subset after filtering.
    private List<DocumentSnapshot> all      = new ArrayList<>();
    private List<DocumentSnapshot> filtered = new ArrayList<>();

    private CrudAdapter adapter;

    // ---------------------------------------------------------------------------
    // Callback interface used to receive the actor's full name asynchronously
    // before writing to Firestore, so that createdBy / modifiedBy stores a
    // human-readable name instead of a raw Firebase UID.
    // ---------------------------------------------------------------------------
    interface NameCallback {
        void onResult(String fullName);
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b  = ActivityGenericManagementBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        // Set up toolbar with back navigation.
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setTitle("College Management");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Set up the RecyclerView.
        adapter = new CrudAdapter();
        b.recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerItems.setAdapter(adapter);

        // Re-filter whenever the user types in the search box.
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) {
                applyFilter();
            }
        });

        // Open the add-college dialog when the FAB / add button is tapped.
        b.btnAdd.setOnClickListener(v -> {
            startActivity(new Intent(this, CollegeFormActivity.class));
        });

        // Listen for real-time updates from the colleges collection.
        db.collection("colleges")
                .orderBy("name")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    all = snap.getDocuments();
                    applyFilter();
                });
    }

    // ---------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------

    /**
     * Rebuilds the filtered list based on the current search query, matching
     * against both the college name and its acronym.
     */
    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            String name    = doc.getString("name")    != null ? doc.getString("name").toLowerCase()    : "";
            String acronym = doc.getString("acronym") != null ? doc.getString("acronym").toLowerCase() : "";
            String campus  = resolveCampusName(doc).toLowerCase();
            if (q.isEmpty() || name.contains(q) || acronym.contains(q) || campus.contains(q)) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " college(s)");
        adapter.notifyDataSetChanged();
    }


    // ---------------------------------------------------------------------------
    // Helper: resolve the currently logged-in user's full name from Firestore
    // ---------------------------------------------------------------------------

    /**
     * Looks up the current user's document in the "users" collection and returns
     * their concatenated fName + lName via the callback.  Falls back to "Unknown"
     * if the document cannot be retrieved.
     */
    private void getActorFullName(NameCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onResult("Unknown");
            return;
        }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String fName = doc.getString("fName") != null ? doc.getString("fName") : "";
                    String lName = doc.getString("lName") != null ? doc.getString("lName") : "";
                    String full  = (fName + " " + lName).trim();
                    callback.onResult(full.isEmpty() ? "Unknown" : full);
                })
                .addOnFailureListener(e -> callback.onResult("Unknown"));
    }

    // ---------------------------------------------------------------------------
    // RecyclerView Adapter
    // ---------------------------------------------------------------------------

    private class CrudAdapter extends RecyclerView.Adapter<CrudAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus, tvAcronym, tvDesc,
                    tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                    btnEdit, btnToggle, btnDelete;

            VH(View v) {
                super(v);
                tvName         = v.findViewById(R.id.tv_name);
                tvStatus       = v.findViewById(R.id.tv_status);
                tvAcronym      = v.findViewById(R.id.tv_acronym);
                tvDesc         = v.findViewById(R.id.tv_description);
                tvDateCreated  = v.findViewById(R.id.tv_date_created);
                tvCreatedBy    = v.findViewById(R.id.tv_created_by);
                tvDateModified = v.findViewById(R.id.tv_date_modified);
                tvModifiedBy   = v.findViewById(R.id.tv_modified_by);
                btnEdit        = v.findViewById(R.id.btn_edit);
                btnToggle      = v.findViewById(R.id.btn_toggle_status);
                btnDelete      = v.findViewById(R.id.btn_delete);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_crud_entity, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String status     = doc.getString("status")  != null ? doc.getString("status")  : "Active";
            String acronym    = doc.getString("acronym") != null ? doc.getString("acronym") : "";
            // Use acronym as the activity-log subject; fall back to name if blank.
            String logSubject = !acronym.isEmpty() ? acronym : orDash(doc.getString("name"));

            // Bind basic fields.
            h.tvName.setText(orDash(doc.getString("name")));
            String campus = resolveCampusName(doc);
            String desc = orDash(doc.getString("description"));
            if (!"—".equals(campus)) {
                desc = desc + "\nCampus: " + campus;
            }
            h.tvDesc.setText(desc);
            h.tvStatus.setText(status);

            // Show the acronym badge only when one exists.
            if (!acronym.isEmpty()) {
                h.tvAcronym.setVisibility(View.VISIBLE);
                h.tvAcronym.setText(acronym);
            } else {
                h.tvAcronym.setVisibility(View.GONE);
            }

            // Style the status badge.
            h.tvStatus.setBackgroundResource(
                    "Active".equals(status) ? R.drawable.badge_active : R.drawable.badge_blocked);
            h.btnToggle.setText("Active".equals(status) ? "Disable" : "Enable");

            // Timestamps.
            Timestamp c = doc.getTimestamp("createdAt");
            Timestamp m = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(c != null ? sdf.format(c.toDate()) : "—");
            h.tvDateModified.setText(m != null ? sdf.format(m.toDate()) : "—");

            // createdBy / modifiedBy now stores the full name.
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Edit button opens the form pre-filled with existing data.
            h.btnEdit.setOnClickListener(v -> {
                Intent i = new Intent(CollegeManagementActivity.this, CollegeFormActivity.class);
                i.putExtra(CollegeFormActivity.EXTRA_DOC_ID, doc.getId());
                startActivity(i);
            });

            // Toggle status button resolves the actor name before updating.
            h.btnToggle.setOnClickListener(v -> {
                String newStatus = "Active".equals(status) ? "Inactive" : "Active";
                getActorFullName(fullName -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    doc.getReference().update(
                                    "status",       newStatus,
                                    "modifiedAt",   Timestamp.now(),
                                    "modifiedBy",   fullName,   // full name
                                    "modifiedById", uid)
                            .addOnSuccessListener(unused ->
                                    ActivityLogger.logCollege(
                                            ActivityLogger.ACTION_MODIFIED,
                                            logSubject + " -> " + newStatus));
                });
            });

            // Delete button shows a confirmation dialog before removing the document.
            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(CollegeManagementActivity.this)
                            .setTitle("Delete College")
                            .setMessage("Delete " + doc.getString("name") + "?")
                            .setPositiveButton("Delete", (d, w) ->
                                    doc.getReference().delete()
                                            .addOnSuccessListener(unused ->
                                                    ActivityLogger.logCollege(
                                                            ActivityLogger.ACTION_DELETE, logSubject)))
                            .setNegativeButton("Cancel", null)
                            .show());
        }

        @Override
        public int getItemCount() { return filtered.size(); }
    }

    // ---------------------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------------------

    /** Returns the value if non-null and non-empty, otherwise an em dash. */
    private String orDash(String s) {
        return (s != null && !s.isEmpty()) ? s : "—";
    }

    private String resolveCampusName(DocumentSnapshot doc) {
        String name = doc.getString("campusName");
        if (name == null || name.isEmpty()) name = doc.getString("campusLabel");
        if (name == null || name.isEmpty()) name = doc.getString("campus");
        return orDash(name);
    }
}