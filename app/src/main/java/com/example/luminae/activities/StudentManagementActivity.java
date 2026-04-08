package com.example.luminae.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStudentManagementBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class StudentManagementActivity extends AppCompatActivity {

    // ── Fields ────────────────────────────────────────────────────────────────
    private ActivityStudentManagementBinding b;
    private FirebaseFirestore db;

    private List<DocumentSnapshot> allStudents = new ArrayList<>();
    private List<DocumentSnapshot> filtered    = new ArrayList<>();
    private StudentAdapter adapter;
    private String filterStatus = "All";

    interface NameCallback { void onResult(String fullName); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b  = ActivityStudentManagementBinding.inflate(getLayoutInflater());
        db = FirebaseFirestore.getInstance();
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new StudentAdapter();
        b.recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerStudents.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });
        b.btnSearch.setOnClickListener(v -> {
            hideKeyboard();
            applyFilter();
        });
        b.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                applyFilter();
                return true;
            }
            return false;
        });

        b.chipGroupStatus.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActive.isChecked())  filterStatus = "Active";
            else if (b.chipBlocked.isChecked()) filterStatus = "Blocked";
            else                                filterStatus = "All";
            applyFilter();
        });

        // ── Launch AddStudentActivity ────────────
        b.btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, StudentFormActivity.class)));

        loadStudents();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.etSearch.getWindowToken(), 0);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadStudents() {
        db.collection("users")
                .whereEqualTo("role", "student")
                .orderBy("fName")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        // Common failure here is a missing composite index.
                        // Fall back to a simpler query so the list still loads.
                        loadStudentsFallback();
                        return;
                    }
                    if (snap == null) return;
                    allStudents = snap.getDocuments();
                    applyFilter();
                });
    }

    private void loadStudentsFallback() {
        db.collection("users")
                .whereEqualTo("role", "student")
                .get()
                .addOnSuccessListener(snap -> {
                    allStudents = snap.getDocuments();
                    applyFilter();
                })
                .addOnFailureListener(err -> {
                    b.tvCount.setText("Failed to load students");
                });
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : allStudents) {
            String name   = (doc.getString("fName") + " " + doc.getString("lName")).toLowerCase();
            String email  = doc.getString("email")  != null ? doc.getString("email").toLowerCase()  : "";
            String status = doc.getString("status") != null ? doc.getString("status") : "";

            boolean matchesSearch = q.isEmpty() || name.contains(q) || email.contains(q);
            boolean matchesStatus = filterStatus.equals("All") || filterStatus.equals(status);
            if (matchesSearch && matchesStatus) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " student(s) found");
        adapter.notifyDataSetChanged();
    }

    // ── Actor name helper ─────────────────────────────────────────────────────

    private void getActorFullName(NameCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { callback.onResult("Unknown"); return; }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String fName = doc.getString("fName") != null ? doc.getString("fName") : "";
                    String lName = doc.getString("lName") != null ? doc.getString("lName") : "";
                    String full  = (fName + " " + lName).trim();
                    callback.onResult(full.isEmpty() ? "Unknown" : full);
                })
                .addOnFailureListener(e -> callback.onResult("Unknown"));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String orDash(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }

    private String initials(String f, String l) {
        String a  = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bb = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bb;
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    private class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView ivProfilePhoto;
            TextView tvInitials, tvFullName, tvUsername, tvStatus, tvEmail,
                    tvCampus, tvCollege, tvCourse,
                    tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                    btnToggleStatus, btnView;

            VH(View v) {
                super(v);
                ivProfilePhoto  = v.findViewById(R.id.iv_profile_photo);
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

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String fName  = orDash(doc.getString("fName"));
            String lName  = orDash(doc.getString("lName"));
            String full   = fName + " " + lName;
            String email  = orDash(doc.getString("email"));
            String status = orDash(doc.getString("status"));
            String photoB64 = doc.getString("photoBase64");

            h.tvInitials.setText(initials(fName, lName));
            loadProfileImage(photoB64, h.ivProfilePhoto);
            h.tvInitials.setVisibility(View.GONE);
            h.tvFullName.setText(full);
            h.tvUsername.setText("@" + orDash(doc.getString("username")));
            h.tvEmail.setText(email);
            h.tvCampus.setText(orDash(doc.getString("campus")));
            h.tvCollege.setText(orDash(doc.getString("college")));
            h.tvCourse.setText(orDash(doc.getString("course")));
            h.tvStatus.setText(status);

            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggleStatus.setText("Block");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggleStatus.setText("Unblock");
            }

            Timestamp created  = doc.getTimestamp("createdAt");
            Timestamp modified = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(created  != null ? sdf.format(created.toDate())  : "—");
            h.tvDateModified.setText(modified != null ? sdf.format(modified.toDate()) : "—");
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            h.btnToggleStatus.setOnClickListener(v -> {
                String newStatus = "Active".equals(status) ? "Blocked" : "Active";
                new MaterialAlertDialogBuilder(StudentManagementActivity.this)
                        .setTitle(newStatus.equals("Blocked") ? "Block Student" : "Unblock Student")
                        .setMessage("Set status to " + newStatus + " for " + full + "?")
                        .setPositiveButton("Confirm", (d, w) ->
                                getActorFullName(actorName -> {
                                    String uid = FirebaseAuth.getInstance().getUid();
                                    db.collection("users").document(doc.getId())
                                            .update(
                                                    "status",       newStatus,
                                                    "modifiedAt",   Timestamp.now(),
                                                    "modifiedBy",   actorName,
                                                    "modifiedById", uid)
                                            .addOnSuccessListener(unused ->
                                                    ActivityLogger.logStudent(
                                                            ActivityLogger.ACTION_MODIFIED,
                                                            email + " -> " + newStatus));
                                }))
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            h.btnView.setOnClickListener(v ->
                    ActivityLogger.logStudent(ActivityLogger.ACTION_VIEWED, email));
        }

        @Override
        public int getItemCount() { return filtered.size(); }
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