package com.example.luminae.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import android.util.Base64;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityStaffFormBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class StaffFormActivity extends AppCompatActivity {

    private ActivityStaffFormBinding b;
    private FirebaseFirestore db;
    private String existingDocId = null;

    private final List<DocumentSnapshot> campusDocs  = new ArrayList<>();
    private final List<String>           campusNames = new ArrayList<>();
    private String selectedCampusId   = null;
    private String selectedCampusName = null;

    private final List<DocumentSnapshot> collegeDocs  = new ArrayList<>();
    private final List<String>           collegeNames = new ArrayList<>();
    private String selectedCollegeId   = null;
    private String selectedCollegeName = null;

    private final List<DocumentSnapshot> courseDocs  = new ArrayList<>();
    private final List<String>           courseNames = new ArrayList<>();
    private String selectedCourseId   = null;
    private String selectedCourseName = null;
    private String selectedPhotoBase64 = null;
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStaffFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());
        showPhotoPlaceholder();

        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    String base64 = compressToBase64(uri, 280, 75);
                    if (base64 == null) {
                        Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedPhotoBase64 = base64;
                    loadBase64Image(base64);
                });
        b.ivProfilePhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.tvChangePicture.setOnClickListener(v -> photoPicker.launch("image/*"));

        existingDocId = getIntent().getStringExtra("docId");

        if (existingDocId != null) {
            b.toolbar.setTitle("Edit Staff");
            b.btnSave.setText("UPDATE");
        } else {
            b.toolbar.setTitle("Add Staff");
            b.btnSave.setText("SAVE");
        }

        setDropdownEnabled(b.tilCollege, b.acvCollege, false);
        setDropdownEnabled(b.tilCourse,  b.acvCourse,  false);

        loadCampuses();

        b.btnSave.setOnClickListener(v -> saveStaff());
        b.btnCancel.setOnClickListener(v -> finish());
    }

    // =========================================================================
    // CAMPUS
    // =========================================================================

    private void loadCampuses() {
        b.llCampusLoading.setVisibility(android.view.View.VISIBLE);

        db.collection("campuses")
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCampusLoading.setVisibility(android.view.View.GONE);
                    campusDocs.clear();
                    campusNames.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCampus.setError("No campuses found");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        campusDocs.add(doc);
                        campusNames.add(doc.getString("name"));
                    }

                    setAdapter(b.acvCampus, campusNames);
                    b.tilCampus.setError(null);

                    b.acvCampus.setOnItemClickListener((parent, view, position, id) -> {
                        DocumentSnapshot chosen = campusDocs.get(position);
                        if (chosen.getId().equals(selectedCampusId)) return;
                        selectedCampusId   = chosen.getId();
                        selectedCampusName = campusNames.get(position);
                        resetCollege();
                        resetCourse();
                        loadColleges(selectedCampusId);
                    });

                    if (existingDocId != null) loadExistingData();
                })
                .addOnFailureListener(e -> {
                    b.llCampusLoading.setVisibility(android.view.View.GONE);
                    b.tilCampus.setError("Failed to load campuses: " + e.getMessage());
                });
    }

    // =========================================================================
    // COLLEGE
    // =========================================================================

    private void loadColleges(String campusId) {
        b.llCollegeLoading.setVisibility(android.view.View.VISIBLE);
        setDropdownEnabled(b.tilCollege, b.acvCollege, false);

        db.collection("colleges")
                .whereEqualTo("campus", campusId)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCollegeLoading.setVisibility(android.view.View.GONE);
                    collegeDocs.clear();
                    collegeNames.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCollege.setError("No colleges found for this campus");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        collegeDocs.add(doc);
                        collegeNames.add(buildLabel(doc));
                    }

                    setAdapter(b.acvCollege, collegeNames);
                    setDropdownEnabled(b.tilCollege, b.acvCollege, true);
                    b.tilCollege.setError(null);

                    b.acvCollege.setOnItemClickListener((parent, view, position, id) -> {
                        DocumentSnapshot chosen = collegeDocs.get(position);
                        if (chosen.getId().equals(selectedCollegeId)) return;
                        selectedCollegeId   = chosen.getId();
                        selectedCollegeName = collegeNames.get(position);
                        resetCourse();
                        loadCourses(selectedCollegeId);
                    });
                })
                .addOnFailureListener(e -> {
                    b.llCollegeLoading.setVisibility(android.view.View.GONE);
                    b.tilCollege.setError("Failed to load colleges: " + e.getMessage());
                });
    }

    // =========================================================================
    // COURSE
    // =========================================================================

    private void loadCourses(String collegeId) {
        b.llCourseLoading.setVisibility(android.view.View.VISIBLE);
        setDropdownEnabled(b.tilCourse, b.acvCourse, false);

        db.collection("courses")
                .whereEqualTo("college", collegeId)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCourseLoading.setVisibility(android.view.View.GONE);
                    courseDocs.clear();
                    courseNames.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCourse.setError("No courses found for this college");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        courseDocs.add(doc);
                        courseNames.add(buildLabel(doc));
                    }

                    setAdapter(b.acvCourse, courseNames);
                    setDropdownEnabled(b.tilCourse, b.acvCourse, true);
                    b.tilCourse.setError(null);

                    b.acvCourse.setOnItemClickListener((parent, view, position, id) -> {
                        selectedCourseId   = courseDocs.get(position).getId();
                        selectedCourseName = courseNames.get(position);
                        b.tilCourse.setError(null);
                    });
                })
                .addOnFailureListener(e -> {
                    b.llCourseLoading.setVisibility(android.view.View.GONE);
                    b.tilCourse.setError("Failed to load courses: " + e.getMessage());
                });
    }

    // =========================================================================
    // RESET HELPERS
    // =========================================================================

    private void resetCollege() {
        selectedCollegeId   = null;
        selectedCollegeName = null;
        collegeDocs.clear();
        collegeNames.clear();
        b.acvCollege.setText("", false);
        b.tilCollege.setError(null);
        setDropdownEnabled(b.tilCollege, b.acvCollege, false);
    }

    private void resetCourse() {
        selectedCourseId   = null;
        selectedCourseName = null;
        courseDocs.clear();
        courseNames.clear();
        b.acvCourse.setText("", false);
        b.tilCourse.setError(null);
        setDropdownEnabled(b.tilCourse, b.acvCourse, false);
    }

    // =========================================================================
    // LOAD EXISTING DATA (edit mode)
    // =========================================================================

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
                    selectedPhotoBase64 = doc.getString("photoBase64");
                    if (selectedPhotoBase64 != null && !selectedPhotoBase64.isEmpty()) {
                        loadBase64Image(selectedPhotoBase64);
                    } else {
                        showPhotoPlaceholder();
                    }

                    String savedCampusId  = doc.getString("campusId");
                    String savedCollegeId = doc.getString("collegeId");
                    String savedCourseId  = doc.getString("courseId");

                    if (savedCampusId == null) return;

                    // Restore campus
                    for (int i = 0; i < campusDocs.size(); i++) {
                        if (campusDocs.get(i).getId().equals(savedCampusId)) {
                            b.acvCampus.setText(campusNames.get(i), false);
                            selectedCampusId   = savedCampusId;
                            selectedCampusName = campusNames.get(i);
                            break;
                        }
                    }

                    if (selectedCampusId == null) return;

                    // Load colleges, then restore college + course
                    b.llCollegeLoading.setVisibility(android.view.View.VISIBLE);
                    db.collection("colleges")
                            .whereEqualTo("campus", savedCampusId)
                            .whereEqualTo("status", "Active")
                            .get()
                            .addOnSuccessListener(cqs -> {
                                b.llCollegeLoading.setVisibility(android.view.View.GONE);
                                collegeDocs.clear();
                                collegeNames.clear();
                                if (cqs == null || cqs.isEmpty()) return;

                                for (DocumentSnapshot d : cqs.getDocuments()) {
                                    collegeDocs.add(d);
                                    collegeNames.add(buildLabel(d));
                                }
                                setAdapter(b.acvCollege, collegeNames);
                                setDropdownEnabled(b.tilCollege, b.acvCollege, true);

                                b.acvCollege.setOnItemClickListener((p, v, pos, aid) -> {
                                    DocumentSnapshot chosen = collegeDocs.get(pos);
                                    if (chosen.getId().equals(selectedCollegeId)) return;
                                    selectedCollegeId   = chosen.getId();
                                    selectedCollegeName = collegeNames.get(pos);
                                    resetCourse();
                                    loadCourses(selectedCollegeId);
                                });

                                if (savedCollegeId == null) return;

                                for (int i = 0; i < collegeDocs.size(); i++) {
                                    if (collegeDocs.get(i).getId().equals(savedCollegeId)) {
                                        b.acvCollege.setText(collegeNames.get(i), false);
                                        selectedCollegeId   = savedCollegeId;
                                        selectedCollegeName = collegeNames.get(i);
                                        break;
                                    }
                                }

                                if (selectedCollegeId == null || savedCourseId == null) return;

                                // Load courses, then restore course
                                b.llCourseLoading.setVisibility(android.view.View.VISIBLE);
                                db.collection("courses")
                                        .whereEqualTo("college", savedCollegeId)
                                        .whereEqualTo("status", "Active")
                                        .get()
                                        .addOnSuccessListener(rqs -> {
                                            b.llCourseLoading.setVisibility(android.view.View.GONE);
                                            courseDocs.clear();
                                            courseNames.clear();
                                            if (rqs == null || rqs.isEmpty()) return;

                                            for (DocumentSnapshot d : rqs.getDocuments()) {
                                                courseDocs.add(d);
                                                courseNames.add(buildLabel(d));
                                            }
                                            setAdapter(b.acvCourse, courseNames);
                                            setDropdownEnabled(b.tilCourse, b.acvCourse, true);

                                            b.acvCourse.setOnItemClickListener((p, v, pos, aid) -> {
                                                selectedCourseId   = courseDocs.get(pos).getId();
                                                selectedCourseName = courseNames.get(pos);
                                                b.tilCourse.setError(null);
                                            });

                                            for (int i = 0; i < courseDocs.size(); i++) {
                                                if (courseDocs.get(i).getId().equals(savedCourseId)) {
                                                    b.acvCourse.setText(courseNames.get(i), false);
                                                    selectedCourseId   = savedCourseId;
                                                    selectedCourseName = courseNames.get(i);
                                                    break;
                                                }
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            b.llCourseLoading.setVisibility(android.view.View.GONE);
                                            b.tilCourse.setError("Failed to load courses");
                                        });
                            })
                            .addOnFailureListener(e -> {
                                b.llCollegeLoading.setVisibility(android.view.View.GONE);
                                b.tilCollege.setError("Failed to load colleges");
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Failed to load staff data");
                });
    }

    // =========================================================================
    // SAVE
    // =========================================================================

    private void saveStaff() {
        String fName = b.etFirstName.getText().toString().trim();
        String lName = b.etLastName.getText().toString().trim();
        String designation = b.etDesignation.getText().toString().trim();
        String email = b.etEmail.getText().toString().trim();

        if (fName.isEmpty()) { b.tilFirstName.setError("Required"); return; }
        else b.tilFirstName.setError(null);

        if (lName.isEmpty()) { b.tilLastName.setError("Required"); return; }
        else b.tilLastName.setError(null);

        if (designation.isEmpty()) { b.tilDesignation.setError("Required"); return; }
        else b.tilDesignation.setError(null);

        if (selectedCampusId == null) { b.tilCampus.setError("Please select a campus"); return; }
        else b.tilCampus.setError(null);

        // College and course are optional. Campus is the only required audience scope.
        b.tilCollege.setError(null);
        b.tilCourse.setError(null);

        if (email.isEmpty()) { b.tilEmail.setError("Required"); return; }
        else b.tilEmail.setError(null);

        if (!email.matches("^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$")) {
            b.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return;
        } else b.tilEmail.setError(null);

        setLoading(true);
        String adminUid = FirebaseAuth.getInstance().getUid();

        if (existingDocId == null) {
            String tempPassword = "BulSU@" + System.currentTimeMillis();
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, tempPassword)
                    .addOnSuccessListener(authResult -> {
                        String newUid = authResult.getUser().getUid();
                        saveToFirestore(newUid, fName, lName, email, adminUid, true);
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        showError(e.getMessage());
                    });
        } else {
            saveToFirestore(existingDocId, fName, lName, email, adminUid, false);
        }
    }

    private void saveToFirestore(String docId,
                                 String fName, String lName,
                                 String email, String adminUid, boolean isNew) {
        Map<String, Object> data = new HashMap<>();
        data.put("fName",        fName);
        data.put("lName",        lName);
        data.put("designation", b.etDesignation.getText().toString().trim());
        data.put("campusId",     selectedCampusId);
        data.put("campusLabel",  selectedCampusName);
        data.put("collegeId",    selectedCollegeId != null ? selectedCollegeId : "");
        data.put("collegeLabel", selectedCollegeName != null ? selectedCollegeName : "");
        data.put("courseId",     selectedCourseId != null ? selectedCourseId : "");
        data.put("courseLabel",  selectedCourseName != null ? selectedCourseName : "");
        data.put("email",        email);
        data.put("photoBase64",  selectedPhotoBase64 != null ? selectedPhotoBase64 : "");

        if (isNew) {
            data.put("role",      "staff");
            data.put("status",    "Active");
            data.put("createdAt", Timestamp.now());
            data.put("createdBy", adminUid);
        } else {
            data.put("modifiedAt", Timestamp.now());
            data.put("modifiedBy", adminUid);
        }

        db.collection("users").document(docId)
                .set(data, SetOptions.merge())
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

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private String buildLabel(DocumentSnapshot doc) {
        String name    = doc.getString("name");
        String acronym = doc.getString("acronym");
        if (name == null) name = "(unnamed)";
        return (acronym != null && !acronym.isEmpty()) ? name + " (" + acronym + ")" : name;
    }

    private void setAdapter(AutoCompleteTextView acv, List<String> items) {
        acv.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(items)));
    }

    private void setDropdownEnabled(
            com.google.android.material.textfield.TextInputLayout til,
            AutoCompleteTextView acv, boolean enabled) {
        til.setEnabled(enabled);
        acv.setEnabled(enabled);
        acv.setFocusable(false);
    }

    private void setLoading(boolean on) {
        b.btnSave.setEnabled(!on);
        b.btnCancel.setEnabled(!on);
        b.progressBar.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
        b.tvError.setVisibility(android.view.View.GONE);
    }

    private void showError(String msg) {
        b.tvError.setVisibility(android.view.View.VISIBLE);
        b.tvError.setText(msg);
    }

    private String compressToBase64(Uri uri, int maxWidth, int quality) {
        try {
            Bitmap original = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), uri);
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                original = Bitmap.createScaledBitmap(original, maxWidth,
                        Math.round(original.getHeight() * ratio), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadBase64Image(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            b.ivProfilePhoto.setImageTintList(null);
            b.ivProfilePhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } catch (Exception e) {
            showPhotoPlaceholder();
        }
    }

    private void showPhotoPlaceholder() {
        b.ivProfilePhoto.setImageResource(com.example.luminae.R.drawable.profile_pic);
        b.ivProfilePhoto.setImageTintList(null);
    }
}