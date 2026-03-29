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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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

    // Track whether each dropdown loaded successfully
    private boolean campusLoaded  = false;
    private boolean collegeLoaded = false;
    private boolean courseLoaded  = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        loadDropdowns();

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

    // ── Dropdown loaders ─────────────────────────────────────────────────────

    private void loadDropdowns() {
        loadCampuses();
        loadColleges();
        loadCourses();
    }

    private void loadCampuses() {
        setDropdownLoading(binding.etCampus, binding.tilCampus, "Loading campuses…");

        db.collection("campuses")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<String> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        // Adjust "name" to whatever field stores the campus label in your collection
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) items.add(name);
                    }

                    if (items.isEmpty()) {
                        setDropdownError(binding.etCampus, binding.tilCampus,
                                "No campuses found. Contact your admin.");
                        campusLoaded = false;
                    } else {
                        applyAdapter(binding.etCampus, binding.tilCampus, items);
                        campusLoaded = true;
                    }
                })
                .addOnFailureListener(e -> {
                    setDropdownError(binding.etCampus, binding.tilCampus,
                            "Failed to load campuses. Check your connection.");
                    campusLoaded = false;
                });
    }

    private void loadColleges() {
        setDropdownLoading(binding.etCollege, binding.tilCollege, "Loading colleges…");

        db.collection("colleges")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<String> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) items.add(name);
                    }

                    if (items.isEmpty()) {
                        setDropdownError(binding.etCollege, binding.tilCollege,
                                "No colleges found. Contact your admin.");
                        collegeLoaded = false;
                    } else {
                        applyAdapter(binding.etCollege, binding.tilCollege, items);
                        collegeLoaded = true;
                    }
                })
                .addOnFailureListener(e -> {
                    setDropdownError(binding.etCollege, binding.tilCollege,
                            "Failed to load colleges. Check your connection.");
                    collegeLoaded = false;
                });
    }

    private void loadCourses() {
        setDropdownLoading(binding.etCourse, binding.tilCourse, "Loading courses…");

        db.collection("courses")
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<String> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) items.add(name);
                    }

                    if (items.isEmpty()) {
                        setDropdownError(binding.etCourse, binding.tilCourse,
                                "No courses found. Contact your admin.");
                        courseLoaded = false;
                    } else {
                        applyAdapter(binding.etCourse, binding.tilCourse, items);
                        courseLoaded = true;
                    }
                })
                .addOnFailureListener(e -> {
                    setDropdownError(binding.etCourse, binding.tilCourse,
                            "Failed to load courses. Check your connection.");
                    courseLoaded = false;
                });
    }

    // ── Dropdown helpers ──────────────────────────────────────────────────────

    /**
     * Show a placeholder hint while data is being fetched and disable the field.
     */
    private void setDropdownLoading(AutoCompleteTextView view,
                                    com.google.android.material.textfield.TextInputLayout layout,
                                    String hint) {
        layout.setError(null);
        layout.setHelperText(hint);
        view.setEnabled(false);
    }

    /**
     * Populate the dropdown adapter and re-enable the field.
     */
    private void applyAdapter(AutoCompleteTextView view,
                              com.google.android.material.textfield.TextInputLayout layout,
                              List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                items
        );
        view.setAdapter(adapter);
        view.setEnabled(true);
        layout.setHelperText(null);
        layout.setError(null);
    }

    /**
     * Show an error state and keep the field disabled so the user cannot
     * type a value that was never validated against the collection.
     * Tapping the field triggers a retry.
     */
    private void setDropdownError(AutoCompleteTextView view,
                                  com.google.android.material.textfield.TextInputLayout layout,
                                  String message) {
        layout.setHelperText(null);
        layout.setError(message);
        view.setEnabled(false);

        // Allow the user to tap the field to retry the fetch
        view.setOnClickListener(v -> {
            layout.setError(null);
            String hint = layout.getHint() != null ? layout.getHint().toString() : "";
            if (hint.equalsIgnoreCase("Campus"))  loadCampuses();
            else if (hint.equalsIgnoreCase("College")) loadColleges();
            else if (hint.equalsIgnoreCase("Course"))  loadCourses();
        });
    }

    // ── Validation & navigation ───────────────────────────────────────────────

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
        if (lastName.isEmpty())  { binding.tilLastName.setError("Required"); return; }
        if (username.isEmpty())  { binding.tilUsername.setError("Required"); return; }
        if (email.isEmpty())     { binding.tilEmail.setError("Required"); return; }
        if (!isValidEmail(email)){ binding.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return; }

        // Dropdown-specific validation
        if (!campusLoaded) {
            binding.tilCampus.setError("Campus list unavailable — tap to retry");
            return;
        }
        if (campus.isEmpty()) { binding.tilCampus.setError("Please select a campus"); return; }

        if (!collegeLoaded) {
            binding.tilCollege.setError("College list unavailable — tap to retry");
            return;
        }
        if (college.isEmpty()) { binding.tilCollege.setError("Please select a college"); return; }

        if (!courseLoaded) {
            binding.tilCourse.setError("Course list unavailable — tap to retry");
            return;
        }
        if (course.isEmpty()) { binding.tilCourse.setError("Please select a course"); return; }

        if (password.isEmpty())   { binding.tilPassword.setError("Required"); return; }
        if (password.length() < 6){ binding.tilPassword.setError("Minimum 6 characters"); return; }
        if (!password.equals(confirm)) { binding.tilConfirmPassword.setError("Passwords do not match"); return; }

        binding.tvReviewName.setText(firstName + " " + lastName);
        binding.tvReviewUsername.setText(username);
        binding.tvReviewEmail.setText(email);
        binding.tvReviewCampus.setText(campus);
        binding.tvReviewCollege.setText(college);
        binding.tvReviewCourse.setText(course);

        binding.scrollView.setVisibility(View.GONE);
        binding.scrollReview.setVisibility(View.VISIBLE);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void attemptRegister() {
        String fName    = binding.etFirstName.getText().toString().trim();
        String lName    = binding.etLastName.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String username = binding.etUsername.getText().toString().trim();
        String campus   = binding.etCampus.getText().toString().trim();
        String college  = binding.etCollege.getText().toString().trim();
        String course   = binding.etCourse.getText().toString().trim();

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveToFirestore(uid, fName, lName, username, email, campus, college, course);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    private void saveToFirestore(String uid, String fName, String lName, String username,
                                 String email, String campus, String college, String course) {
        User newUser = new User(fName, lName, username, email, campus, college, course, "student", "Active");

        db.collection("users")
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(v -> {
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
        // Navigate to login or a pending-approval screen as needed
        startActivity(new Intent(this, LoginActivity.class));
        finish();
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