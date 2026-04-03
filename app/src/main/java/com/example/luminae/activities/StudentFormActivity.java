package com.example.luminae.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.util.Base64;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityStudentFormBinding;
import com.example.luminae.models.User;
import com.example.luminae.utils.ActivityLogger;
import com.example.luminae.utils.MailSender;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class StudentFormActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    // ── Binding & Firebase ────────────────────────────────────────────────────
    private ActivityStudentFormBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ── Campus state ──────────────────────────────────────────────────────────
    private final List<DocumentSnapshot> campusDocs  = new ArrayList<>();
    private final List<String> campusLabels = new ArrayList<>();
    private String selectedCampusId         = null;

    // ── College state ─────────────────────────────────────────────────────────
    private final List<DocumentSnapshot> collegeDocs  = new ArrayList<>();
    private final List<String> collegeLabels = new ArrayList<>();
    private String selectedCollegeId         = null;

    // ── Course state ──────────────────────────────────────────────────────────
    private final List<String> courseLabels = new ArrayList<>();
    private final List<String> courseIds    = new ArrayList<>();
    private String selectedPhotoBase64      = null;
    private ActivityResultLauncher<String> photoPicker;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b    = ActivityStudentFormBinding.inflate(getLayoutInflater());
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        setContentView(b.getRoot());

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

        // Lock College and Course until their parent is chosen
        setDropdownEnabled(b.tilCollege, b.acvCollege, false);
        setDropdownEnabled(b.tilCourse,  b.acvCourse,  false);

        // Inline email domain validation
        b.etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) {
                String typed = s.toString().trim();
                if (!typed.isEmpty() && !BULSU_EMAIL.matcher(typed).matches())
                    b.tilEmail.setError("Must be @ms.bulsu.edu.ph");
                else
                    b.tilEmail.setError(null);
            }
        });

        loadCampuses();

        b.btnRegister.setOnClickListener(v -> registerStudent());
        b.btnCancel.setOnClickListener(v -> finish());
    }

    // =========================================================================
    // CAMPUS
    // =========================================================================

    private void loadCampuses() {
        b.llCampusLoading.setVisibility(View.VISIBLE);

        db.collection("campuses")
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCampusLoading.setVisibility(View.GONE);
                    campusDocs.clear();
                    campusLabels.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCampus.setError("No campuses found");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        campusDocs.add(doc);
                        campusLabels.add(buildLabel(doc));
                    }

                    setAdapter(b.acvCampus, campusLabels);
                    b.tilCampus.setError(null);

                    b.acvCampus.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= campusDocs.size()) return;
                        String newCampusId = campusDocs.get(position).getId();
                        if (newCampusId.equals(selectedCampusId)) return;
                        selectedCampusId = newCampusId;
                        b.tilCampus.setError(null);
                        resetCollege();
                        resetCourse();
                        loadColleges(selectedCampusId);
                    });
                })
                .addOnFailureListener(e -> {
                    b.llCampusLoading.setVisibility(View.GONE);
                    b.tilCampus.setError("Failed to load campuses: " + e.getMessage());
                });
    }

    // =========================================================================
    // COLLEGE
    // =========================================================================

    private void loadColleges(String campusId) {
        b.llCollegeLoading.setVisibility(View.VISIBLE);
        setDropdownEnabled(b.tilCollege, b.acvCollege, false);

        db.collection("colleges")
                .whereEqualTo("campus", campusId)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCollegeLoading.setVisibility(View.GONE);
                    collegeDocs.clear();
                    collegeLabels.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCollege.setError("No colleges found for this campus");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        collegeDocs.add(doc);
                        collegeLabels.add(buildLabel(doc));
                    }

                    setAdapter(b.acvCollege, collegeLabels);
                    setDropdownEnabled(b.tilCollege, b.acvCollege, true);
                    b.tilCollege.setError(null);

                    b.acvCollege.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= collegeDocs.size()) return;
                        String newCollegeId = collegeDocs.get(position).getId();
                        if (newCollegeId.equals(selectedCollegeId)) return;
                        selectedCollegeId = newCollegeId;
                        b.tilCollege.setError(null);
                        resetCourse();
                        loadCourses(selectedCollegeId);
                    });
                })
                .addOnFailureListener(e -> {
                    b.llCollegeLoading.setVisibility(View.GONE);
                    b.tilCollege.setError("Failed to load colleges: " + e.getMessage());
                    setDropdownEnabled(b.tilCollege, b.acvCollege, false);
                });
    }

    // =========================================================================
    // COURSE
    // =========================================================================

    private void loadCourses(String collegeId) {
        b.llCourseLoading.setVisibility(View.VISIBLE);
        setDropdownEnabled(b.tilCourse, b.acvCourse, false);

        db.collection("courses")
                .whereEqualTo("college", collegeId)
                .get()
                .addOnSuccessListener(qs -> {
                    b.llCourseLoading.setVisibility(View.GONE);
                    courseLabels.clear();
                    courseIds.clear();

                    if (qs == null || qs.isEmpty()) {
                        b.tilCourse.setError("No courses found for this college");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        courseLabels.add(buildLabel(doc));
                        courseIds.add(doc.getId());
                    }

                    setAdapter(b.acvCourse, courseLabels);
                    setDropdownEnabled(b.tilCourse, b.acvCourse, true);
                    b.tilCourse.setError(null);
                })
                .addOnFailureListener(e -> {
                    b.llCourseLoading.setVisibility(View.GONE);
                    b.tilCourse.setError("Failed to load courses: " + e.getMessage());
                    setDropdownEnabled(b.tilCourse, b.acvCourse, false);
                });
    }

    // =========================================================================
    // RESET HELPERS
    // =========================================================================

    private void resetCollege() {
        selectedCollegeId = null;
        collegeDocs.clear();
        collegeLabels.clear();
        b.acvCollege.setText("", false);
        b.tilCollege.setError(null);
        setDropdownEnabled(b.tilCollege, b.acvCollege, false);
    }

    private void resetCourse() {
        courseLabels.clear();
        courseIds.clear();
        b.acvCourse.setText("", false);
        b.tilCourse.setError(null);
        setDropdownEnabled(b.tilCourse, b.acvCourse, false);
    }

    // =========================================================================
    // REGISTER
    // =========================================================================

    private void registerStudent() {
        String fName       = b.etFirstName.getText().toString().trim();
        String lName       = b.etLastName.getText().toString().trim();
        String email       = b.etEmail.getText().toString().trim();
        String campusLabel = b.acvCampus.getText().toString().trim();
        String collegeLabel= b.acvCollege.getText().toString().trim();
        String courseLabel = b.acvCourse.getText().toString().trim();

        // ── Validation ──────────────────────────────────────────────────────
        boolean valid = true;

        if (fName.isEmpty()) {
            b.tilFirstName.setError("Required"); valid = false;
        } else b.tilFirstName.setError(null);

        if (lName.isEmpty()) {
            b.tilLastName.setError("Required"); valid = false;
        } else b.tilLastName.setError(null);

        if (email.isEmpty()) {
            b.tilEmail.setError("Required"); valid = false;
        } else if (!BULSU_EMAIL.matcher(email).matches()) {
            b.tilEmail.setError("Must be @ms.bulsu.edu.ph"); valid = false;
        } else b.tilEmail.setError(null);

        if (campusLabel.isEmpty() || selectedCampusId == null) {
            b.tilCampus.setError("Please select a campus"); valid = false;
        } else b.tilCampus.setError(null);

        if (collegeLabel.isEmpty() || selectedCollegeId == null) {
            b.tilCollege.setError("Please select a college"); valid = false;
        } else b.tilCollege.setError(null);

        int courseIndex = courseLabels.indexOf(courseLabel);
        if (courseLabel.isEmpty() || courseIndex < 0) {
            b.tilCourse.setError("Please select a course"); valid = false;
        } else b.tilCourse.setError(null);

        if (!valid) return;

        String selectedCourseId = courseIds.get(courseIndex);

        // ── Persist ─────────────────────────────────────────────────────────
        setLoading(true);

        String username = email.substring(0, email.indexOf('@'));
        String password = generatePassword(8);
        String fullName = fName + " " + lName;

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();

                    // Build the user model — campus/college/course store both label and ID
                    User newUser = new User(fName, lName, username, email,
                            campusLabel, collegeLabel, courseLabel, "student", "Active");

                    // Write to Firestore — also persist IDs for future filtering
                    db.collection("users").document(uid)
                            .set(newUser)
                            .addOnSuccessListener(unused -> {
                                // Append the IDs and audit fields in a second merge
                                // (avoids changing the User model if it's shared elsewhere)
                                db.collection("users").document(uid)
                                        .update(
                                                "campusId",  selectedCampusId,
                                                "collegeId", selectedCollegeId,
                                                "courseId",  selectedCourseId,
                                                "photoBase64", selectedPhotoBase64 != null ? selectedPhotoBase64 : "",
                                                "createdAt", Timestamp.now(),
                                                "createdBy", FirebaseAuth.getInstance().getUid()
                                        );

                                ActivityLogger.logStudent(ActivityLogger.ACTION_CREATE, email);

                                MailSender.sendWelcomeEmail(email, fullName, username, password,
                                        new MailSender.Callback() {
                                            @Override public void onSuccess() {
                                                runOnUiThread(() -> {
                                                    setLoading(false);
                                                    Toast.makeText(StudentFormActivity.this,
                                                            fullName + " registered! Credentials sent to " + email,
                                                            Toast.LENGTH_LONG).show();
                                                    finish();
                                                });
                                            }
                                            @Override public void onFailure(Exception e) {
                                                runOnUiThread(() -> {
                                                    setLoading(false);
                                                    Toast.makeText(StudentFormActivity.this,
                                                            fullName + " registered, but email failed: " + e.getMessage(),
                                                            Toast.LENGTH_LONG).show();
                                                    finish();
                                                });
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showError("Profile save failed: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    /** "Name (Acronym)" or just "Name" if acronym is absent. */
    private String buildLabel(DocumentSnapshot doc) {
        String name    = doc.getString("name");
        String acronym = doc.getString("acronym");
        if (name == null) name = "(unnamed)";
        return (acronym != null && !acronym.isEmpty())
                ? name + " (" + acronym + ")"
                : name;
    }

    private void setAdapter(android.widget.AutoCompleteTextView acv, List<String> items) {
        acv.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(items)));
    }

    private void setDropdownEnabled(
            com.google.android.material.textfield.TextInputLayout til,
            android.widget.AutoCompleteTextView acv,
            boolean enabled) {
        til.setEnabled(enabled);
        acv.setEnabled(enabled);
        acv.setFocusable(false);
    }

    private String generatePassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private void setLoading(boolean on) {
        b.btnRegister.setEnabled(!on);
        b.btnCancel.setEnabled(!on);
        b.btnRegister.setText(on ? "Registering…" : "REGISTER");
        b.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        b.tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        b.tvError.setVisibility(View.VISIBLE);
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