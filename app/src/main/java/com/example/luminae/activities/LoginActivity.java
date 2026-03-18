package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityLoginBinding;
import com.example.luminae.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivityLoginBinding binding;
    private SessionManager session;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);
        auth    = FirebaseAuth.getInstance();
        db      = FirebaseFirestore.getInstance();

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

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        binding.tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private boolean isValidEmail(String email) {
        return BULSU_EMAIL.matcher(email).matches();
    }

    private void attemptLogin() {
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty())      { binding.tilEmail.setError("Email is required"); return; }
        if (!isValidEmail(email)) { binding.tilEmail.setError("Must be @ms.bulsu.edu.ph"); return; }
        if (password.isEmpty())   { binding.tilPassword.setError("Password is required"); return; }

        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        setLoading(true);

        // Step 1 — Sign in with Firebase Auth
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Step 2 — Check status in Firestore
                    String uid = authResult.getUser().getUid();
                    checkUserStatus(uid, email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Incorrect email or password.");
                });
    }

    private void checkUserStatus(String uid, String email) {
        // Fetch user doc from Firestore to check role + status
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);

                    if (!doc.exists()) {
                        showError("Account not found. Contact an admin.");
                        auth.signOut();
                        return;
                    }

                    String status = doc.getString("status");

                    if ("pending".equals(status)) {
                        // Not approved yet — sign them out and block
                        auth.signOut();
                        showError("Your account is pending approval.\nPlease wait for an admin to activate it.");
                    } else if ("active".equals(status)) {
                        // Approved — save session and go in
                        session.save(
                                uid,
                                doc.getString("username"),
                                email,
                                doc.getString("role")
                        );
                        goToMain();
                    } else {
                        auth.signOut();
                        showError("Your account is inactive. Contact an admin.");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    auth.signOut();
                    showError("Error fetching account info: " + e.getMessage());
                });
    }


    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showError(String msg) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(msg);
    }

    private void setLoading(boolean on) {
        binding.btnLogin.setEnabled(!on);
        binding.tvGoToRegister.setEnabled(!on);
        binding.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.tvError.setVisibility(View.GONE);
    }
}
