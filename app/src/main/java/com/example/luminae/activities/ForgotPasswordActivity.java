package com.example.luminae.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityForgotPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;

import java.util.regex.Pattern;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        binding.btnSendReset.setOnClickListener(v -> sendResetEmail());

        // Back to login
        binding.tvBackToLogin.setOnClickListener(v -> finish());
        binding.btnDone.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = binding.etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            binding.tilEmail.setError("Email is required");
            return;
        }
        if (!isValidEmail(email)) {
            binding.tilEmail.setError("Must be @ms.bulsu.edu.ph");
            return;
        }

        binding.tilEmail.setError(null);
        setLoading(true);

        // Firebase sends the reset link to this email
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    showSuccessState(email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    binding.tvMessage.setVisibility(View.VISIBLE);
                    binding.tvMessage.setText("Could not send reset email.\nMake sure this email is registered.");
                });
    }

    private void showSuccessState(String email) {
        // Hide form, show success message
        binding.layoutForm.setVisibility(View.GONE);
        binding.layoutSuccess.setVisibility(View.VISIBLE);
        binding.tvSentTo.setText("Reset link sent to:\n" + email);
    }

    private boolean isValidEmail(String email) {
        return BULSU_EMAIL.matcher(email).matches();
    }

    private void setLoading(boolean on) {
        binding.btnSendReset.setEnabled(!on);
        binding.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.tvMessage.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
