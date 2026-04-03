package com.example.luminae.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivitySignedInForgotPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;

import java.util.regex.Pattern;

public class SignedInForgotPasswordActivity extends AppCompatActivity {

    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    private ActivitySignedInForgotPasswordBinding b;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySignedInForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        auth = FirebaseAuth.getInstance();

        String email = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;
        if (email == null || email.trim().isEmpty()) {
            b.tilEmail.setError("No signed-in email found");
            b.btnSendReset.setEnabled(false);
        } else {
            b.etEmail.setText(email);
            b.tilEmail.setError(null);
            if (!BULSU_EMAIL.matcher(email.trim()).matches()) {
                b.tilEmail.setError("Must be @ms.bulsu.edu.ph");
                b.btnSendReset.setEnabled(false);
            }
        }

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSendReset.setOnClickListener(v -> sendResetEmail());
        b.btnDone.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = b.etEmail.getText() != null ? b.etEmail.getText().toString().trim() : "";

        if (email.isEmpty()) {
            b.tilEmail.setError("Email is required");
            return;
        }
        if (!BULSU_EMAIL.matcher(email).matches()) {
            b.tilEmail.setError("Must be @ms.bulsu.edu.ph");
            return;
        }

        b.tilEmail.setError(null);
        setLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    showSuccessState(email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    b.tvMessage.setVisibility(View.VISIBLE);
                    b.tvMessage.setText("Could not send reset email.\nPlease try again.");
                });
    }

    private void showSuccessState(String email) {
        b.layoutForm.setVisibility(View.GONE);
        b.layoutSuccess.setVisibility(View.VISIBLE);
        b.tvSentTo.setText("Reset link sent to:\n" + email);
    }

    private void setLoading(boolean on) {
        b.btnSendReset.setEnabled(!on);
        b.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        b.tvMessage.setVisibility(View.GONE);
    }
}

