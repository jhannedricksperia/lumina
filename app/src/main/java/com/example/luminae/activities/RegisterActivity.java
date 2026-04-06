package com.example.luminae.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.R;
import com.example.luminae.databinding.ActivityRegisterBinding;
import com.example.luminae.models.User;
import com.example.luminae.utils.ActivityLogger;
import com.example.luminae.utils.MailSender;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private final List<DocumentSnapshot> campusDocs = new ArrayList<>();
    private final List<String> campusLabels = new ArrayList<>();
    private String selectedCampusId = null;

    private final List<DocumentSnapshot> collegeDocs = new ArrayList<>();
    private final List<String> collegeLabels = new ArrayList<>();
    private String selectedCollegeId = null;

    private final List<String> courseLabels = new ArrayList<>();
    private final List<String> courseIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setDropdownEnabled(binding.tilCollege, binding.acvCollege, false);
        setDropdownEnabled(binding.tilCourse, binding.acvCourse, false);

        loadCampuses();

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

        binding.btnNext.setOnClickListener(v -> showReview());
        binding.btnBack.setOnClickListener(v -> {
            binding.scrollReview.setVisibility(View.GONE);
            binding.scrollView.setVisibility(View.VISIBLE);
        });
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void loadCampuses() {
        binding.llCampusLoading.setVisibility(View.VISIBLE);
        binding.tilCampus.setError(null);

        db.collection("campuses")
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    binding.llCampusLoading.setVisibility(View.GONE);
                    campusDocs.clear();
                    campusLabels.clear();

                    if (qs == null || qs.isEmpty()) {
                        binding.tilCampus.setError("No campuses found");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        campusDocs.add(doc);
                        campusLabels.add(buildLabel(doc));
                    }

                    setAdapter(binding.acvCampus, campusLabels);
                    binding.tilCampus.setError(null);

                    binding.acvCampus.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= campusDocs.size()) return;
                        String newCampusId = campusDocs.get(position).getId();
                        if (newCampusId.equals(selectedCampusId)) return;
                        selectedCampusId = newCampusId;
                        binding.tilCampus.setError(null);
                        resetCollege();
                        resetCourse();
                        loadColleges(selectedCampusId);
                    });
                })
                .addOnFailureListener(e -> {
                    binding.llCampusLoading.setVisibility(View.GONE);
                    binding.tilCampus.setError("Failed to load campuses: " + e.getMessage());
                });
    }

    private void loadColleges(String campusId) {
        binding.llCollegeLoading.setVisibility(View.VISIBLE);
        setDropdownEnabled(binding.tilCollege, binding.acvCollege, false);
        binding.tilCollege.setError(null);

        db.collection("colleges")
                .whereEqualTo("campus", campusId)
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(qs -> {
                    binding.llCollegeLoading.setVisibility(View.GONE);
                    collegeDocs.clear();
                    collegeLabels.clear();

                    if (qs == null || qs.isEmpty()) {
                        binding.tilCollege.setError("No colleges found for this campus");
                        return;
                    }

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        collegeDocs.add(doc);
                        collegeLabels.add(buildLabel(doc));
                    }

                    setAdapter(binding.acvCollege, collegeLabels);
                    setDropdownEnabled(binding.tilCollege, binding.acvCollege, true);
                    binding.tilCollege.setError(null);

                    binding.acvCollege.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= collegeDocs.size()) return;
                        String newCollegeId = collegeDocs.get(position).getId();
                        if (newCollegeId.equals(selectedCollegeId)) return;
                        selectedCollegeId = newCollegeId;
                        binding.tilCollege.setError(null);
                        resetCourse();
                        loadCourses(selectedCollegeId);
                    });
                })
                .addOnFailureListener(e -> {
                    binding.llCollegeLoading.setVisibility(View.GONE);
                    binding.tilCollege.setError("Failed to load colleges: " + e.getMessage());
                    setDropdownEnabled(binding.tilCollege, binding.acvCollege, false);
                });
    }

    private void loadCourses(String collegeId) {
        binding.llCourseLoading.setVisibility(View.VISIBLE);
        setDropdownEnabled(binding.tilCourse, binding.acvCourse, false);
        binding.tilCourse.setError(null);

        db.collection("courses")
                .whereEqualTo("college", collegeId)
                .get()
                .addOnSuccessListener(qs -> {
                    binding.llCourseLoading.setVisibility(View.GONE);
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

                    setAdapter(binding.acvCourse, courseLabels);
                    setDropdownEnabled(binding.tilCourse, binding.acvCourse, true);
                    binding.tilCourse.setError(null);
                })
                .addOnFailureListener(e -> {
                    binding.llCourseLoading.setVisibility(View.GONE);
                    binding.tilCourse.setError("Failed to load courses: " + e.getMessage());
                    setDropdownEnabled(binding.tilCourse, binding.acvCourse, false);
                });
    }

    private void resetCollege() {
        selectedCollegeId = null;
        collegeDocs.clear();
        collegeLabels.clear();
        binding.acvCollege.setText("", false);
        binding.tilCollege.setError(null);
        setDropdownEnabled(binding.tilCollege, binding.acvCollege, false);
    }

    private void resetCourse() {
        courseLabels.clear();
        courseIds.clear();
        binding.acvCourse.setText("", false);
        binding.tilCourse.setError(null);
        setDropdownEnabled(binding.tilCourse, binding.acvCourse, false);
    }

    private boolean isValidEmail(String email) {
        return BULSU_EMAIL.matcher(email).matches();
    }

    private String usernameFromEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private void showReview() {
        String firstName = binding.etFirstName.getText().toString().trim();
        String lastName = binding.etLastName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String campus = binding.acvCampus.getText().toString().trim();
        String college = binding.acvCollege.getText().toString().trim();
        String course = binding.acvCourse.getText().toString().trim();

        if (firstName.isEmpty()) { binding.tilFirstName.setError("Required"); return; }
        binding.tilFirstName.setError(null);
        if (lastName.isEmpty()) { binding.tilLastName.setError("Required"); return; }
        binding.tilLastName.setError(null);
        if (email.isEmpty()) { binding.tilEmail.setError("Required"); return; }
        if (!isValidEmail(email)) { binding.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return; }
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

        binding.tvReviewName.setText(firstName + " " + lastName);
        binding.tvReviewUsername.setText(usernameFromEmail(email));
        binding.tvReviewEmail.setText(email);
        binding.tvReviewCampus.setText(campus);
        binding.tvReviewCollege.setText(college);
        binding.tvReviewCourse.setText(course);

        binding.scrollView.setVisibility(View.GONE);
        binding.scrollReview.setVisibility(View.VISIBLE);
    }

    private void attemptRegister() {
        String fName = binding.etFirstName.getText().toString().trim();
        String lName = binding.etLastName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String campusLabel = binding.acvCampus.getText().toString().trim();
        String collegeLabel = binding.acvCollege.getText().toString().trim();
        String courseLabel = binding.acvCourse.getText().toString().trim();

        int courseIndex = courseLabels.indexOf(courseLabel);
        if (courseIndex < 0) {
            showErrorReview("Please go back and select a course again.");
            return;
        }
        String selectedCourseId = courseIds.get(courseIndex);

        String username = usernameFromEmail(email);
        String password = generatePassword(8);
        String fullName = fName + " " + lName;
        String photoBase64 = encodeDefaultProfilePic();

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    User newUser = new User(fName, lName, username, email,
                            campusLabel, collegeLabel, courseLabel, "student", "Active");

                    db.collection("users").document(uid)
                            .set(newUser)
                            .addOnSuccessListener(v -> db.collection("users").document(uid)
                                    .update(
                                            "campusId", selectedCampusId,
                                            "collegeId", selectedCollegeId,
                                            "courseId", selectedCourseId,
                                            "photoBase64", photoBase64 != null ? photoBase64 : "",
                                            "createdAt", Timestamp.now(),
                                            "createdBy", uid
                                    )
                                    .addOnSuccessListener(unused -> {
                                        ActivityLogger.logStudent(ActivityLogger.ACTION_CREATE, email);
                                        MailSender.sendWelcomeEmail(email, fullName, username, password,
                                                new MailSender.Callback() {
                                                    @Override
                                                    public void onSuccess() {
                                                        runOnUiThread(() -> {
                                                            setLoading(false);
                                                            Toast.makeText(RegisterActivity.this,
                                                                    "Registered! Check " + email + " for your password.",
                                                                    Toast.LENGTH_LONG).show();
                                                            showPendingScreen();
                                                        });
                                                    }

                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        runOnUiThread(() -> {
                                                            setLoading(false);
                                                            Toast.makeText(RegisterActivity.this,
                                                                    "Account created, but email failed: " + e.getMessage(),
                                                                    Toast.LENGTH_LONG).show();
                                                            showPendingScreen();
                                                        });
                                                    }
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        setLoading(false);
                                        showErrorReview("Profile update failed: " + e.getMessage());
                                    }))
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showErrorReview("Profile save failed: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showErrorReview(e.getMessage());
                });
    }

    private String encodeDefaultProfilePic() {
        try {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.profile_pic);
            if (bmp == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    private String generatePassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private void showPendingScreen() {
        binding.scrollReview.setVisibility(View.GONE);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private String buildLabel(DocumentSnapshot doc) {
        String name = doc.getString("name");
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

    private void showErrorReview(String msg) {
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
