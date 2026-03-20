package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityStaffFormBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

public class StaffFormActivity extends AppCompatActivity {

    private ActivityStaffFormBinding b;
    private FirebaseFirestore db;
    private String existingDocId = null; // null = new staff, non-null = edit

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStaffFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Check if editing existing staff
        existingDocId = getIntent().getStringExtra("docId");

        if (existingDocId != null) {
            b.toolbar.setTitle("Edit Staff");
            b.btnSave.setText("UPDATE");
            loadExistingData();
        } else {
            b.toolbar.setTitle("Add Staff");
            b.btnSave.setText("SAVE");
        }

        b.btnSave.setOnClickListener(v -> saveStaff());
        b.btnCancel.setOnClickListener(v -> finish());
    }

    // ── Load existing staff data for editing ─────────────────────────────────
    private void loadExistingData() {
        setLoading(true);
        db.collection("users").document(existingDocId).get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    if (doc == null || !doc.exists()) return;
                    b.etFirstName.setText(doc.getString("fName"));
                    b.etLastName.setText(doc.getString("lName"));
                    b.etDesignation.setText(doc.getString("designation"));
                    b.etEmail.setText(doc.getString("email"));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Failed to load staff data");
                });
    }

    // ── Validate and save ────────────────────────────────────────────────────
    private void saveStaff() {
        String fName       = b.etFirstName.getText().toString().trim();
        String lName       = b.etLastName.getText().toString().trim();
        String designation = b.etDesignation.getText().toString().trim();
        String email       = b.etEmail.getText().toString().trim();

        // Validation
        if (fName.isEmpty()) {
            b.tilFirstName.setError("Required");
            return;
        } else b.tilFirstName.setError(null);

        if (lName.isEmpty()) {
            b.tilLastName.setError("Required");
            return;
        } else b.tilLastName.setError(null);

        if (designation.isEmpty()) {
            b.tilDesignation.setError("Required");
            return;
        } else b.tilDesignation.setError(null);

        if (email.isEmpty()) {
            b.tilEmail.setError("Required");
            return;
        } else b.tilEmail.setError(null);

        if (!email.matches("^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$")) {
            b.tilEmail.setError("Must be @ms.bulsu.edu.ph");
            return;
        } else b.tilEmail.setError(null);

        setLoading(true);
        String uid = FirebaseAuth.getInstance().getUid();

        if (existingDocId == null) {
            // ── Create new staff via Firebase Auth then save to Firestore ──
            // For new staff, create a Firebase Auth account first
            String tempPassword = "BulSU@" + System.currentTimeMillis(); // temp password
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, tempPassword)
                    .addOnSuccessListener(authResult -> {
                        String newUid = authResult.getUser().getUid();
                        saveToFirestore(newUid, fName, lName, designation, email, uid, true);
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        showError(e.getMessage());
                    });
        } else {
            // ── Update existing staff ──
            saveToFirestore(existingDocId, fName, lName, designation, email, uid, false);
        }
    }

    private void saveToFirestore(String docId, String fName, String lName,
                                 String designation, String email,
                                 String adminUid, boolean isNew) {
        Map<String, Object> data = new HashMap<>();
        data.put("fName", fName);
        data.put("lName", lName);
        data.put("designation", designation);
        data.put("email", email);

        if (isNew) {
            data.put("role", "staff");
            data.put("status", "Active");
            data.put("createdAt", Timestamp.now());
            data.put("createdBy", adminUid);
        } else {
            data.put("modifiedAt", Timestamp.now());
            data.put("modifiedBy", adminUid);
        }

        db.collection("users").document(docId)
                .set(data, isNew ? SetOptions.merge() : SetOptions.merge())
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    Toast.makeText(this,
                            isNew ? "Staff added successfully!" : "Staff updated successfully!",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Failed to save: " + e.getMessage());
                });
    }

    private void setLoading(boolean on) {
        b.btnSave.setEnabled(!on);
        b.btnCancel.setEnabled(!on);
        b.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        b.tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        b.tvError.setVisibility(View.VISIBLE);
        b.tvError.setText(msg);
    }
}