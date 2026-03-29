package com.example.luminae.activities;

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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CampusManagementActivity extends AppCompatActivity {

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
        getSupportActionBar().setTitle("Campus Management");
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

        // Open the add-campus dialog when the FAB / add button is tapped.
        b.btnAdd.setOnClickListener(v -> showFormDialog(null));

        // Listen for real-time updates from the campuses collection.
        db.collection("campuses")
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
     * Rebuilds the filtered list based on the current search query and refreshes
     * the adapter.
     */
    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            String name = doc.getString("name") != null
                    ? doc.getString("name").toLowerCase() : "";
            if (q.isEmpty() || name.contains(q)) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " campus(es)");
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------------------
    // Add / Edit dialog
    // ---------------------------------------------------------------------------

    /**
     * Shows the campus form dialog.
     *
     * @param existing Pass null to create a new campus, or a DocumentSnapshot
     *                 to edit an existing one.
     */
    private void showFormDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_campus_form, null);
        TextInputEditText etName = form.findViewById(R.id.et_name);
        TextInputEditText etDesc = form.findViewById(R.id.et_description);

        // Pre-fill fields when editing.
        if (existing != null) {
            etName.setText(existing.getString("name"));
            etDesc.setText(existing.getString("description"));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add Campus" : "Edit Campus")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Fetch the current admin's full name before saving so that
                    // createdBy / modifiedBy shows a readable name, not a UID.
                    getActorFullName(fullName -> {
                        String uid = FirebaseAuth.getInstance().getUid();

                        if (existing == null) {
                            // Create new campus document.
                            Map<String, Object> data = new HashMap<>();
                            data.put("name",        name);
                            data.put("description", desc);
                            data.put("status",      "Active");
                            data.put("createdAt",   Timestamp.now());
                            data.put("createdBy",   fullName);   // full name, not UID
                            data.put("createdById", uid);        // keep UID for reference
                            db.collection("campuses").add(data)
                                    .addOnSuccessListener(ref ->
                                            ActivityLogger.logCampus(
                                                    ActivityLogger.ACTION_CREATE, name));
                        } else {
                            // Update existing campus document.
                            existing.getReference().update(
                                            "name",         name,
                                            "description",  desc,
                                            "modifiedAt",   Timestamp.now(),
                                            "modifiedBy",   fullName,   // full name, not UID
                                            "modifiedById", uid)
                                    .addOnSuccessListener(v ->
                                            ActivityLogger.logCampus(
                                                    ActivityLogger.ACTION_MODIFIED, name));
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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

            String name   = orDash(doc.getString("name"));
            String status = doc.getString("status") != null ? doc.getString("status") : "Active";

            // Bind basic fields.
            h.tvName.setText(name);
            h.tvDesc.setText(orDash(doc.getString("description")));
            h.tvStatus.setText(status);
            h.tvAcronym.setVisibility(View.GONE); // campuses have no acronym

            // Style the status badge.
            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggle.setText("Disable");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggle.setText("Enable");
            }

            // Timestamps.
            Timestamp c = doc.getTimestamp("createdAt");
            Timestamp m = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(c != null ? sdf.format(c.toDate()) : "—");
            h.tvDateModified.setText(m != null ? sdf.format(m.toDate()) : "—");

            // createdBy / modifiedBy now stores the full name.
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Edit button opens the form pre-filled with existing data.
            h.btnEdit.setOnClickListener(v -> showFormDialog(doc));

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
                                    ActivityLogger.logCampus(
                                            ActivityLogger.ACTION_MODIFIED,
                                            name + " -> " + newStatus));
                });
            });

            // Delete button shows a confirmation dialog before removing the document.
            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(CampusManagementActivity.this)
                            .setTitle("Delete Campus")
                            .setMessage("Delete " + name + "? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) ->
                                    doc.getReference().delete()
                                            .addOnSuccessListener(unused ->
                                                    ActivityLogger.logCampus(
                                                            ActivityLogger.ACTION_DELETE, name)))
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
}