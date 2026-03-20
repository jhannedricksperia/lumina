package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityRegisterBinding;
import com.example.luminae.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

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
        if (campus.isEmpty())    { binding.tilCampus.setError("Required"); return; }
        if (college.isEmpty())   { binding.tilCollege.setError("Required"); return; }
        if (course.isEmpty())    { binding.tilCourse.setError("Required"); return; }
        if (password.isEmpty())  { binding.tilPassword.setError("Required"); return; }
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

    private void attemptRegister() {
        String fName = binding.etFirstName.getText().toString().trim();
        String lName  = binding.etLastName.getText().toString().trim();
        String email     = binding.etEmail.getText().toString().trim();
        String password  = binding.etPassword.getText().toString().trim();
        String username  = binding.etUsername.getText().toString().trim();
        String campus    = binding.etCampus.getText().toString().trim();
        String college   = binding.etCollege.getText().toString().trim();
        String course    = binding.etCourse.getText().toString().trim();

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveToFirestore(uid, fName, lName, username, email, campus, college, course);
                    setLoading(false);
                    startActivity(new Intent(this, LoginActivity.class));
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