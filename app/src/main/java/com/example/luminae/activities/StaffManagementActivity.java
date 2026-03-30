package com.example.luminae.activities;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.TextView;
import android.widget.ImageView;
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

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private ActivityStaffManagementBinding b;
    private FirebaseFirestore db;

    // Master list from Firestore, and the currently displayed subset after filtering.
    private List<DocumentSnapshot> allStaff = new ArrayList<>();
    private List<DocumentSnapshot> filtered = new ArrayList<>();

    private StaffAdapter adapter;

    // Tracks the currently selected status chip ("All", "Active", or "Blocked").
    private String filterStatus = "All";

    // ---------------------------------------------------------------------------
    // Callback interface used to receive the actor's full name asynchronously
    // before writing to Firestore, so that modifiedBy stores a human-readable
    // name instead of a raw Firebase UID.
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
        b  = ActivityStaffManagementBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        // Set up toolbar with back navigation.
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Set up the RecyclerView.
        adapter = new StaffAdapter();
        b.recyclerStaff.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerStaff.setAdapter(adapter);

        // Re-filter whenever the user types in the search box.
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) {
                applyFilter();
            }
        });

        // Re-filter whenever a status chip is toggled.
        b.chipGroupStatus.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActive.isChecked())  filterStatus = "Active";
            else if (b.chipBlocked.isChecked()) filterStatus = "Blocked";
            else                                filterStatus = "All";
            applyFilter();
        });

        // Navigate to the staff form for adding a new staff member.
        b.btnAddStaff.setOnClickListener(v -> openStaffForm(null));

        loadStaff();
    }

    // ---------------------------------------------------------------------------
    // Data loading
    // ---------------------------------------------------------------------------

    /**
     * Attaches a real-time Firestore listener that streams all users whose role
     * is "staff", ordered alphabetically by first name.
     */
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

    // ---------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------

    /**
     * Rebuilds the filtered list by applying both the text search query and the
     * status chip selection, then refreshes the adapter.
     */
    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : allStaff) {
            String name   = (doc.getString("fName") + " " + doc.getString("lName")).toLowerCase();
            String desig  = doc.getString("designation") != null
                    ? doc.getString("designation").toLowerCase() : "";
            String status = doc.getString("status") != null ? doc.getString("status") : "";

            boolean matchSearch = q.isEmpty() || name.contains(q) || desig.contains(q);
            boolean matchStatus = filterStatus.equals("All") || filterStatus.equals(status);
            if (matchSearch && matchStatus) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " staff member(s) found");
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    /**
     * Opens the StaffFormActivity.
     *
     * @param existing Pass null to add a new staff member, or a DocumentSnapshot
     *                 to edit an existing one (its ID is passed as an extra).
     */
    private void openStaffForm(DocumentSnapshot existing) {
        Intent i = new Intent(this, StaffFormActivity.class);
        if (existing != null) i.putExtra("docId", existing.getId());
        startActivity(i);
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

    private class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView ivProfilePhoto;
            TextView tvInitials, tvFullName, tvDesignation, tvStatus,
                    tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                    btnToggle, btnEdit, btnDelete;

            VH(View v) {
                super(v);
                ivProfilePhoto  = v.findViewById(R.id.iv_profile_photo);
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

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_staff, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String fName  = orDash(doc.getString("fName"));
            String lName  = orDash(doc.getString("lName"));
            String status = orDash(doc.getString("status"));
            String photoB64 = doc.getString("photoBase64");

            // Bind basic fields.
            h.tvInitials.setText(initials(fName, lName));
            loadProfileImage(photoB64, h.ivProfilePhoto);
            h.tvInitials.setVisibility(android.view.View.GONE);
            h.tvFullName.setText(fName + " " + lName);
            h.tvDesignation.setText(orDash(doc.getString("designation")));
            h.tvStatus.setText(status);

            // Style the status badge and toggle button label.
            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggle.setText("Block");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggle.setText("Unblock");
            }

            // Timestamps.
            Timestamp created  = doc.getTimestamp("createdAt");
            Timestamp modified = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(created  != null ? sdf.format(created.toDate())  : "—");
            h.tvDateModified.setText(modified != null ? sdf.format(modified.toDate()) : "—");

            // createdBy / modifiedBy now stores the full name.
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Toggle block / unblock with a confirmation dialog.
            // Resolves actor full name before writing to Firestore.
            h.btnToggle.setOnClickListener(v -> {
                String newStatus = "Active".equals(status) ? "Blocked" : "Active";
                new MaterialAlertDialogBuilder(StaffManagementActivity.this)
                        .setTitle(newStatus.equals("Blocked") ? "Block Staff" : "Unblock Staff")
                        .setMessage("Set " + fName + " " + lName + " as " + newStatus + "?")
                        .setPositiveButton("Confirm", (d, w) ->
                                getActorFullName(fullName -> {
                                    String uid = FirebaseAuth.getInstance().getUid();
                                    db.collection("users").document(doc.getId())
                                            .update(
                                                    "status",       newStatus,
                                                    "modifiedAt",   Timestamp.now(),
                                                    "modifiedBy",   fullName,   // full name, not UID
                                                    "modifiedById", uid);
                                }))
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // Edit opens the StaffFormActivity for the selected document.
            h.btnEdit.setOnClickListener(v -> openStaffForm(doc));

            // Delete with a confirmation dialog.
            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(StaffManagementActivity.this)
                            .setTitle("Delete Staff")
                            .setMessage("Permanently delete " + fName + " " + lName + "?")
                            .setPositiveButton("Delete", (d, w) ->
                                    db.collection("users").document(doc.getId()).delete())
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

    /** Builds a two-letter initials string from first and last name. */
    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String b = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + b;
    }

    private void loadProfileImage(String base64, ImageView imageView) {
        if (base64 == null || base64.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.profile_pic);
            return;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.profile_pic);
        }
    }
}