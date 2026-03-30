package com.example.luminae.activities;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityCampusFormBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen form activity for creating or editing a Campus document.
 *
 * Launch extras:
 *   EXTRA_DOC_ID  (String, optional) – Firestore document ID to edit.
 *                                       Omit to create a new campus.
 */
public class CampusFormActivity extends AppCompatActivity {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    public static final String EXTRA_DOC_ID = "doc_id";

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private ActivityCampusFormBinding b;
    private FirebaseFirestore db;

    /** Non-null when editing an existing document. */
    private DocumentSnapshot existing;

    // ---------------------------------------------------------------------------
    // Callback interface used to receive the actor's full name asynchronously.
    // ---------------------------------------------------------------------------
    interface NameCallback { void onResult(String fullName); }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b  = ActivityCampusFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        // Toolbar
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        String docId = getIntent().getStringExtra(EXTRA_DOC_ID);

        if (docId != null) {
            // Edit mode: load the existing document and pre-fill the form.
            getSupportActionBar().setTitle("Edit Campus");
            db.collection("campuses").document(docId).get()
                    .addOnSuccessListener(doc -> {
                        existing = doc;
                        b.etName.setText(doc.getString("name"));
                        b.etDescription.setText(doc.getString("description"));
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to load campus", Toast.LENGTH_SHORT).show());
        } else {
            // Create mode.
            getSupportActionBar().setTitle("Add Campus");
        }

        b.btnCancel.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    private void save() {
        String name = b.etName.getText().toString().trim();
        String desc = b.etDescription.getText().toString().trim();

        if (name.isEmpty()) {
            b.tilName.setError("Campus name is required");
            return;
        }
        b.tilName.setError(null);

        getActorFullName(fullName -> {
            String uid = FirebaseAuth.getInstance().getUid();

            if (existing == null) {
                // ── Create ───────────────────────────────────────────────────
                Map<String, Object> data = new HashMap<>();
                data.put("name",        name);
                data.put("description", desc);
                data.put("status",      "Active");
                data.put("createdAt",   Timestamp.now());
                data.put("createdBy",   fullName);
                data.put("createdById", uid);

                db.collection("campuses").add(data)
                        .addOnSuccessListener(ref -> {
                            ActivityLogger.logCampus(ActivityLogger.ACTION_CREATE, name);
                            finish();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Save failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
            } else {
                // ── Update ───────────────────────────────────────────────────
                existing.getReference().update(
                                "name",         name,
                                "description",  desc,
                                "modifiedAt",   Timestamp.now(),
                                "modifiedBy",   fullName,
                                "modifiedById", uid)
                        .addOnSuccessListener(v -> {
                            ActivityLogger.logCampus(ActivityLogger.ACTION_MODIFIED, name);
                            finish();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Update failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------------------------------------------------------------------------
    // Helper: resolve the currently logged-in user's full name from Firestore
    // ---------------------------------------------------------------------------

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
}
