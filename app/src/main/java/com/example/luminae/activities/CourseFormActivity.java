package com.example.luminae.activities;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityCourseFormBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class CourseFormActivity extends AppCompatActivity {

    public static final String EXTRA_DOC_ID = "doc_id";

    private ActivityCourseFormBinding b;
    private FirebaseFirestore db;

    private DocumentSnapshot existing;

    private final List<DocumentSnapshot> campusDocs  = new ArrayList<>();
    private final List<String>           campusNames = new ArrayList<>();
    private String selectedCampusId   = null;
    private String selectedCampusName = null;

    private final List<DocumentSnapshot> collegeDocs  = new ArrayList<>();
    private final List<String>           collegeNames = new ArrayList<>();
    private String selectedCollegeId   = null;
    private String selectedCollegeName = null;

    interface NameCallback { void onResult(String fullName); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b  = ActivityCourseFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.tilCollege.setEnabled(false);

        String docId = getIntent().getStringExtra(EXTRA_DOC_ID);

        if (docId != null) {
            getSupportActionBar().setTitle("Edit Course");
            db.collection("courses").document(docId).get()
                    .addOnSuccessListener(doc -> {
                        existing = doc;
                        b.etName.setText(doc.getString("name"));
                        b.etAcronym.setText(doc.getString("acronym"));
                        b.etDescription.setText(doc.getString("description"));
                        loadCampuses();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to load course", Toast.LENGTH_SHORT).show());
        } else {
            getSupportActionBar().setTitle("Add Course");
            loadCampuses();
        }

        b.btnCancel.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
    }

    // ---------------------------------------------------------------------------
    // Campus dropdown
    // ---------------------------------------------------------------------------

    private void loadCampuses() {
        db.collection("campuses")
                .whereEqualTo("status", "Active")
                // .orderBy("name") removed — requires composite index
                .get()
                .addOnSuccessListener(snap -> {
                    campusDocs.clear();
                    campusNames.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        campusDocs.add(doc);
                        campusNames.add(doc.getString("name"));
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            campusNames);
                    b.acvCampus.setAdapter(adapter);
                    b.acvCampus.setThreshold(1);
                    b.acvCampus.setOnClickListener(v -> b.acvCampus.showDropDown());

                    if (existing != null) {
                        String savedCampusId = existing.getString("campus");
                        for (int i = 0; i < campusDocs.size(); i++) {
                            if (campusDocs.get(i).getId().equals(savedCampusId)) {
                                b.acvCampus.setText(campusNames.get(i), false);
                                selectedCampusId   = campusDocs.get(i).getId();
                                selectedCampusName = campusNames.get(i);
                                loadCollegesForCampus(selectedCampusId, existing.getString("college"));
                                break;
                            }
                        }
                    }

                    b.acvCampus.setOnItemClickListener((parent, view, position, id) -> {
                        DocumentSnapshot chosen = campusDocs.get(position);
                        if (!chosen.getId().equals(selectedCampusId)) {
                            selectedCampusId   = chosen.getId();
                            selectedCampusName = campusNames.get(position);
                            selectedCollegeId   = null;
                            selectedCollegeName = null;
                            b.acvCollege.setText("", false);
                            b.tilCampus.setError(null);
                            loadCollegesForCampus(selectedCampusId, null);
                        }
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load campuses", Toast.LENGTH_SHORT).show());
    }

    // ---------------------------------------------------------------------------
    // College dropdown
    // ---------------------------------------------------------------------------

    private void loadCollegesForCampus(String campusId, String preSelectCollegeId) {
        b.tilCollege.setEnabled(false);
        b.acvCollege.setText("", false);

        db.collection("colleges")
                .whereEqualTo("campus", campusId)
                .whereEqualTo("status", "Active")
                // .orderBy("name") removed — requires composite index
                .get()
                .addOnSuccessListener(snap -> {
                    collegeDocs.clear();
                    collegeNames.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        collegeDocs.add(doc);
                        collegeNames.add(doc.getString("name"));
                    }

                    if (collegeDocs.isEmpty()) {
                        Toast.makeText(this, "No colleges found for this campus",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            collegeNames);
                    b.acvCollege.setAdapter(adapter);
                    b.acvCollege.setThreshold(1);
                    b.tilCollege.setEnabled(true);
                    b.tilCollege.setError(null);
                    b.acvCollege.setOnClickListener(v -> b.acvCollege.showDropDown());

                    if (preSelectCollegeId != null) {
                        for (int i = 0; i < collegeDocs.size(); i++) {
                            if (collegeDocs.get(i).getId().equals(preSelectCollegeId)) {
                                b.acvCollege.setText(collegeNames.get(i), false);
                                selectedCollegeId   = collegeDocs.get(i).getId();
                                selectedCollegeName = collegeNames.get(i);
                                break;
                            }
                        }
                    }

                    b.acvCollege.setOnItemClickListener((parent, view, position, id) -> {
                        selectedCollegeId   = collegeDocs.get(position).getId();
                        selectedCollegeName = collegeNames.get(position);
                        b.tilCollege.setError(null);
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load colleges", Toast.LENGTH_SHORT).show());
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    private void save() {
        String name    = b.etName.getText().toString().trim();
        String acronym = b.etAcronym.getText().toString().trim();
        String desc    = b.etDescription.getText().toString().trim();

        boolean valid = true;

        if (selectedCampusId == null) {
            b.tilCampus.setError("Please select a campus");
            valid = false;
        } else {
            b.tilCampus.setError(null);
        }

        if (selectedCollegeId == null) {
            b.tilCollege.setError("Please select a college");
            valid = false;
        } else {
            b.tilCollege.setError(null);
        }

        if (name.isEmpty()) {
            b.tilName.setError("Course name is required");
            valid = false;
        } else {
            b.tilName.setError(null);
        }

        if (!valid) return;

        String logSubject = !acronym.isEmpty() ? acronym : name;

        getActorFullName(fullName -> {
            String uid = FirebaseAuth.getInstance().getUid();

            if (existing == null) {
                Map<String, Object> data = new HashMap<>();
                data.put("name",        name);
                data.put("acronym",     acronym);
                data.put("description", desc);
                data.put("campus",      selectedCampusId);
                data.put("campusName",  selectedCampusName);
                data.put("college",     selectedCollegeId);
                data.put("collegeName", selectedCollegeName);
                data.put("status",      "Active");
                data.put("createdAt",   Timestamp.now());
                data.put("createdBy",   fullName);
                data.put("createdById", uid);

                db.collection("courses").add(data)
                        .addOnSuccessListener(ref -> {
                            ActivityLogger.logCourse(ActivityLogger.ACTION_CREATE, logSubject);
                            finish();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Save failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
            } else {
                existing.getReference().update(
                                "name",         name,
                                "acronym",      acronym,
                                "description",  desc,
                                "campus",       selectedCampusId,
                                "campusName",   selectedCampusName,
                                "college",      selectedCollegeId,
                                "collegeName",  selectedCollegeName,
                                "modifiedAt",   Timestamp.now(),
                                "modifiedBy",   fullName,
                                "modifiedById", uid)
                        .addOnSuccessListener(v -> {
                            ActivityLogger.logCourse(ActivityLogger.ACTION_MODIFIED, logSubject);
                            finish();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Update failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---------------------------------------------------------------------------
    // Helper
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