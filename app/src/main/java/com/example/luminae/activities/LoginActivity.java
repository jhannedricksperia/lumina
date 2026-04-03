package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityLoginBinding;
import com.example.luminae.utils.FcmRegistrationHelper;
import com.example.luminae.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
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

        // Field accepts either email or username, so avoid email-only validation here.

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
        String rawId    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (rawId.isEmpty())    { binding.tilEmail.setError("Email or username is required"); return; }
        if (password.isEmpty()) { binding.tilPassword.setError("Password is required"); return; }

        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        setLoading(true);

        if (isValidEmail(rawId)) {
            signInWithEmail(rawId, password);
        } else {
            signInWithUsername(rawId, password);
        }
    }

    private void signInWithEmail(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    checkUserStatus(uid, email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Incorrect email/username or password.");
                });
    }

    private void signInWithUsername(String usernameInput, String password) {
        String u = usernameInput.trim();
        if (u.startsWith("@")) u = u.substring(1).trim();
        if (u.isEmpty()) {
            setLoading(false);
            binding.tilEmail.setError("Username is required");
            return;
        }

        db.collection("users")
                .whereEqualTo("username", u)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs == null || qs.isEmpty() || qs.getDocuments().isEmpty()) {
                        setLoading(false);
                        showError("Incorrect email/username or password.");
                        return;
                    }
                    String email = qs.getDocuments().get(0).getString("email");
                    if (email == null || email.trim().isEmpty()) {
                        setLoading(false);
                        showError("Account email missing. Contact an admin.");
                        return;
                    }
                    signInWithEmail(email.trim(), password);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Login failed: " + e.getMessage());
                });
    }

    private void checkUserStatus(String uid, String email) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);

                    if (!doc.exists()) {
                        showError("Account not found. Contact an admin.");
                        auth.signOut();
                        return;
                    }

                    String status = doc.getString("status");
                    String role   = doc.getString("role");

                    if ("pending".equals(status)) {
                        auth.signOut();
                        showError("Your account is pending approval.\nPlease wait for an admin to activate it.");
                    } else if ("Active".equals(status)) {
                        session.save(uid, doc.getString("username"), email, role);
                        routeByRole(role);
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

    private void routeByRole(String role) {
        FcmRegistrationHelper.register(this);
        Intent intent;
        if (role == null) role = "";
        switch (role.toLowerCase()) {
            case "admin":
                intent = new Intent(this, AdminActivity.class);
                break;
            case "staff":
                intent = new Intent(this, StaffActivity.class);
                break;
            case "student":
            default:
                intent = new Intent(this, StudentActivity.class);
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
