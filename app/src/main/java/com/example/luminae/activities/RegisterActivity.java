package com.example.luminae.activities;

import android.os.Bundle;
import android.view.MenuItem;
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

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create Account");
        }

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

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnBackToLogin.setOnClickListener(v -> finish());
    }

    private boolean isValidEmail(String email) {
        return BULSU_EMAIL.matcher(email).matches();
    }

    private void attemptRegister() {
        String fullName = binding.etFullName.getText().toString().trim();
        String username = binding.etUsername.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirm  = binding.etConfirmPassword.getText().toString().trim();

        if (fullName.isEmpty()) { binding.tilFullName.setError("Required"); return; }
        if (username.isEmpty()) { binding.tilUsername.setError("Required"); return; }
        if (email.isEmpty())    { binding.tilEmail.setError("Required"); return; }
        if (!isValidEmail(email)) { binding.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return; }
        if (password.isEmpty())   { binding.tilPassword.setError("Required"); return; }
        if (password.length() < 6){ binding.tilPassword.setError("Minimum 6 characters"); return; }
        if (!password.equals(confirm)) { binding.tilConfirmPassword.setError("Passwords do not match"); return; }

        setLoading(true);

        // Step 1 — Create account in Firebase Auth
        // Firebase Auth stores the email + password securely
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Step 2 — Save extra info to Firestore
                    // Document ID = Firebase Auth UID (links Auth and Firestore together)
                    String uid = authResult.getUser().getUid();
                    saveToFirestore(uid, fullName, username, email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // Firebase gives useful messages like "email already in use"
                    showError(e.getMessage());
                });
    }

    private void saveToFirestore(String uid, String fullName, String username, String email) {
        // No password stored here — Firebase Auth handles that
        User newUser = new User(fullName, username, email, "student", "pending");

        db.collection("users")
                .document(uid) // use Auth UID as document ID
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
        binding.scrollView.setVisibility(View.GONE);
        binding.layoutPending.setVisibility(View.VISIBLE);
    }

    private void showError(String msg) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(msg);
    }

    private void setLoading(boolean on) {
        binding.btnRegister.setEnabled(!on);
        binding.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.tvError.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
