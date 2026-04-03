package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityChangePasswordBinding;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private ActivityChangePasswordBinding b;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        auth = FirebaseAuth.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.btnCancel.setOnClickListener(v -> finish());
        b.btnUpdate.setOnClickListener(v -> attemptChangePassword());
        b.btnForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, SignedInForgotPasswordActivity.class)));
    }

    private void attemptChangePassword() {
        String current = b.etCurrentPassword.getText().toString().trim();
        String newPw   = b.etNewPassword.getText().toString().trim();
        String confirm = b.etConfirmPassword.getText().toString().trim();

        b.tvError.setVisibility(View.GONE);
        b.tilCurrent.setError(null);
        b.tilNew.setError(null);
        b.tilConfirm.setError(null);

        boolean valid = true;
        if (current.isEmpty()) {
            b.tilCurrent.setError("Required");
            valid = false;
        }
        if (newPw.isEmpty()) {
            b.tilNew.setError("Required");
            valid = false;
        }
        if (confirm.isEmpty()) {
            b.tilConfirm.setError("Required");
            valid = false;
        }
        if (!valid) return;

        if (!newPw.equals(confirm)) {
            b.tvError.setVisibility(View.VISIBLE);
            b.tvError.setText("Passwords do not match");
            return;
        }
        if (newPw.length() < 6) {
            b.tvError.setVisibility(View.VISIBLE);
            b.tvError.setText("Minimum 6 characters");
            return;
        }

        setLoading(true);
        reauthAndChangePassword(current, newPw);
    }

    private void reauthAndChangePassword(String current, String newPw) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            setLoading(false);
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), current);
        user.reauthenticate(cred)
                .addOnSuccessListener(a -> user.updatePassword(newPw)
                        .addOnSuccessListener(v -> {
                            setLoading(false);
                            Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    b.tvError.setVisibility(View.VISIBLE);
                    b.tvError.setText("Current password incorrect");
                });
    }

    private void setLoading(boolean on) {
        b.btnUpdate.setEnabled(!on);
        b.btnCancel.setEnabled(!on);
        b.progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
    }
}

