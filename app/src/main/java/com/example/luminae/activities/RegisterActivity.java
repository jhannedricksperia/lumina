package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.R;
import com.example.luminae.databinding.ActivityRegisterBinding;
import com.example.luminae.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // ── Campus state ──────────────────────────────────────────────────────────
    private final List<String> campusLabels = new ArrayList<>();
    private final List<String> campusIds    = new ArrayList<>();
    private String selectedCampusId         = null;

    // ── College state ─────────────────────────────────────────────────────────
    private final List<String> collegeLabels = new ArrayList<>();
    private final List<String> collegeIds    = new ArrayList<>();
    private String selectedCollegeId         = null;

    // ── Course state ──────────────────────────────────────────────────────────
    private final List<String> courseLabels = new ArrayList<>();
    private final List<String> courseIds    = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Lock college and course until parent is selected
        setDropdownEnabled(binding.tilCollege, binding.etCollege, false);
        setDropdownEnabled(binding.tilCourse,  binding.etCourse,  false);

        loadCampuses();

        // Live email validation
        binding.etEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String typed = s.toString().trim();
                if (!typed.isEmpty() && !isValidEmail(typed))
                    binding.tilEmail.setError("Must be @ms.bulsu.edu.ph");
                else
                    binding.tilEmail.setError(null);
            }
        });

        // Live password match
        binding.etConfirmPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String pw1 = binding.etPassword.getText().toString();
                if (!s.toString().isEmpty() && !pw1.equals(s.toString()))
                    binding.tilConfirmPassword.setError("Passwords do not match");
                else
                    binding.tilConfirmPassword.setError(null);
            }
        });

        binding.btnNext.setOnClickListener(v -> showReview());
        binding.btnBack.setOnClickListener(v -> {
            binding.scrollReview.setVisibility(View.GONE);
            binding.scrollView.setVisibility(View.VISIBLE);
        });
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvGoToLogin.setOnClickListener(v -> finish());
    }

    // =========================================================================
    // CAMPUS
    // =========================================================================

    private void loadCampuses() {
        binding.tilCampus.setError(null);

        db.collection("campuses")
                .orderBy("name")
                .get()
                .addOnSuccessListener(qs -> {
                    campusLabels.clear();
                    campusIds.clear();

                    if (qs == null || qs.isEmpty()) {
                        binding.tilCampus.setError("No campuses found");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        campusLabels.add(buildLabel(doc));
                        campusIds.add(doc.getId());
                    }

                    setAdapter(binding.etCampus, campusLabels);
                    binding.tilCampus.setError(null);

                    binding.etCampus.setOnItemClickListener((parent, view, position, id) -> {
                        String newCampusId = campusIds.get(position);
                        if (newCampusId.equals(selectedCampusId)) return;
                        selectedCampusId = newCampusId;
                        binding.tilCampus.setError(null);
                        resetCollege();
                        resetCourse();
                        loadColleges(selectedCampusId);
                    });
                })
                .addOnFailureListener(e ->
                        binding.tilCampus.setError("Failed to load campuses. Tap to retry."));
    }

    // =========================================================================
    // COLLEGE
    // =========================================================================

    private void loadColleges(String campusId) {
        setDropdownEnabled(binding.tilCollege, binding.etCollege, false);
        binding.tilCollege.setError(null);

        db.collection("colleges")
                .whereEqualTo("campus", campusId)
                .orderBy("name")
                .get()
                .addOnSuccessListener(qs -> {
                    collegeLabels.clear();
                    collegeIds.clear();

                    if (qs == null || qs.isEmpty()) {
                        binding.tilCollege.setError("No colleges found for this campus");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        collegeLabels.add(buildLabel(doc));
                        collegeIds.add(doc.getId());
                    }

                    setAdapter(binding.etCollege, collegeLabels);
                    setDropdownEnabled(binding.tilCollege, binding.etCollege, true);
                    binding.tilCollege.setError(null);

                    binding.etCollege.setOnItemClickListener((parent, view, position, id) -> {
                        String newCollegeId = collegeIds.get(position);
                        if (newCollegeId.equals(selectedCollegeId)) return;
                        selectedCollegeId = newCollegeId;
                        binding.tilCollege.setError(null);
                        resetCourse();
                        loadCourses(selectedCollegeId);
                    });
                })
                .addOnFailureListener(e -> {
                    binding.tilCollege.setError("Failed to load colleges.");
                    setDropdownEnabled(binding.tilCollege, binding.etCollege, false);
                });
    }

    // =========================================================================
    // COURSE
    // =========================================================================

    private void loadCourses(String collegeId) {
        setDropdownEnabled(binding.tilCourse, binding.etCourse, false);
        binding.tilCourse.setError(null);

        db.collection("courses")
                .whereEqualTo("college", collegeId)
                .orderBy("name")
                .get()
                .addOnSuccessListener(qs -> {
                    courseLabels.clear();
                    courseIds.clear();

                    if (qs == null || qs.isEmpty()) {
                        binding.tilCourse.setError("No courses found for this college");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        courseLabels.add(buildLabel(doc));
                        courseIds.add(doc.getId());
                    }

                    setAdapter(binding.etCourse, courseLabels);
                    setDropdownEnabled(binding.tilCourse, binding.etCourse, true);
                    binding.tilCourse.setError(null);
                })
                .addOnFailureListener(e -> {
                    binding.tilCourse.setError("Failed to load courses.");
                    setDropdownEnabled(binding.tilCourse, binding.etCourse, false);
                });
    }

    // =========================================================================
    // RESET HELPERS
    // =========================================================================

    private void resetCollege() {
        selectedCollegeId = null;
        collegeLabels.clear();
        collegeIds.clear();
        binding.etCollege.setText("", false);
        binding.tilCollege.setError(null);
        setDropdownEnabled(binding.tilCollege, binding.etCollege, false);
    }

    private void resetCourse() {
        courseLabels.clear();
        courseIds.clear();
        binding.etCourse.setText("", false);
        binding.tilCourse.setError(null);
        setDropdownEnabled(binding.tilCourse, binding.etCourse, false);
    }

    // =========================================================================
    // VALIDATION & NAVIGATION
    // =========================================================================

    private boolean isValidEmail(String email) {
        return BULSU_EMAIL.matcher(email).matches();
    }

    private void showReview() {
        String firstName = binding.etFirstName.getText().toString().trim();
        String lastName  = binding.etLastName.getText().toString().trim();
        String username  = binding.etUsername.getText().toString().trim();
        String email     = binding.etEmail.getText().toString().trim();
        String campus    = binding.etCampus.getText().toString().trim();
        String college   = binding.etCollege.getText().toString().trim();
        String course    = binding.etCourse.getText().toString().trim();
        String password  = binding.etPassword.getText().toString().trim();
        String confirm   = binding.etConfirmPassword.getText().toString().trim();

        if (firstName.isEmpty()) { binding.tilFirstName.setError("Required"); return; }
        binding.tilFirstName.setError(null);
        if (lastName.isEmpty())  { binding.tilLastName.setError("Required"); return; }
        binding.tilLastName.setError(null);
        if (username.isEmpty())  { binding.tilUsername.setError("Required"); return; }
        binding.tilUsername.setError(null);
        if (email.isEmpty())     { binding.tilEmail.setError("Required"); return; }
        if (!isValidEmail(email)){ binding.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return; }
        binding.tilEmail.setError(null);

        if (campus.isEmpty() || selectedCampusId == null) {
            binding.tilCampus.setError("Please select a campus"); return;
        }
        binding.tilCampus.setError(null);

        if (college.isEmpty() || selectedCollegeId == null) {
            binding.tilCollege.setError("Please select a college"); return;
        }
        binding.tilCollege.setError(null);

        int courseIndex = courseLabels.indexOf(course);
        if (course.isEmpty() || courseIndex < 0) {
            binding.tilCourse.setError("Please select a course"); return;
        }
        binding.tilCourse.setError(null);

        if (password.isEmpty())    { binding.tilPassword.setError("Required"); return; }
        if (password.length() < 6) { binding.tilPassword.setError("Minimum 6 characters"); return; }
        binding.tilPassword.setError(null);
        if (!password.equals(confirm)) { binding.tilConfirmPassword.setError("Passwords do not match"); return; }
        binding.tilConfirmPassword.setError(null);

        binding.tvReviewName.setText(firstName + " " + lastName);
        binding.tvReviewUsername.setText(username);
        binding.tvReviewEmail.setText(email);
        binding.tvReviewCampus.setText(campus);
        binding.tvReviewCollege.setText(college);
        binding.tvReviewCourse.setText(course);

        binding.scrollView.setVisibility(View.GONE);
        binding.scrollReview.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    private void attemptRegister() {
        String fName    = binding.etFirstName.getText().toString().trim();
        String lName    = binding.etLastName.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String username = binding.etUsername.getText().toString().trim();
        String campus   = binding.etCampus.getText().toString().trim();
        String college  = binding.etCollege.getText().toString().trim();
        String course   = binding.etCourse.getText().toString().trim();

        int courseIndex = courseLabels.indexOf(course);
        String selectedCourseId = (courseIndex >= 0) ? courseIds.get(courseIndex) : "";

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveToFirestore(uid, fName, lName, username, email,
                            campus, college, course, selectedCourseId);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    private void saveToFirestore(String uid, String fName, String lName, String username,
                                 String email, String campus, String college, String course,
                                 String courseId) {
        User newUser = new User(fName, lName, username, email,
                campus, college, course, "student", "Active");

        db.collection("users")
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(v -> {
                    // Persist IDs for future filtering
                    db.collection("users").document(uid)
                            .update(
                                    "campusId",  selectedCampusId,
                                    "collegeId", selectedCollegeId,
                                    "courseId",  courseId
                            );
                    setLoading(false);
                    showPendingScreen();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Account created but profile save failed: " + e.getMessage());
                });
    }

    private void showPendingScreen() {
        binding.scrollReview.setVisibility(View.GONE);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
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

    private void setAdapter(AutoCompleteTextView acv, List<String> items) {
        acv.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(items)));
    }

    private void setDropdownEnabled(
            com.google.android.material.textfield.TextInputLayout til,
            AutoCompleteTextView acv,
            boolean enabled) {
        til.setEnabled(enabled);
        acv.setEnabled(enabled);
        acv.setFocusable(false);
    }

    private void showError(String msg) {
        binding.tvErrorReview.setVisibility(View.VISIBLE);
        binding.tvErrorReview.setText(msg);
    }

    private void setLoading(boolean on) {
        binding.btnRegister.setEnabled(!on);
        binding.btnBack.setEnabled(!on);
        binding.progressBarReview.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.tvErrorReview.setVisibility(View.GONE);
    }
}