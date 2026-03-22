package com.example.luminae.student.fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.luminae.R;
import com.example.luminae.activities.LoginActivity;
import com.example.luminae.databinding.FragmentStudentProfileBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;

public class StudentProfileFragment extends Fragment {

    private FragmentStudentProfileBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ActivityResultLauncher<String> photoPicker;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b    = FragmentStudentProfileBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) uploadProfilePhoto(uri); });

        loadProfile();

        b.ivProfilePhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.btnEditPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        b.btnLogout.setOnClickListener(v -> showLogoutDialog());

        return b.getRoot();
    }

    private void loadProfile() {
        String uid = auth.getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc == null || !doc.exists()) return;
            String fName    = doc.getString("fName")      != null ? doc.getString("fName")      : "";
            String lName    = doc.getString("lName")      != null ? doc.getString("lName")      : "";
            String email    = doc.getString("email")      != null ? doc.getString("email")      : "";
            String campus   = doc.getString("campus")     != null ? doc.getString("campus")     : "";
            String college  = doc.getString("college")    != null ? doc.getString("college")    : "";
            String course   = doc.getString("course")     != null ? doc.getString("course")     : "";
            String photoB64 = doc.getString("photoBase64");

            b.tvFullName.setText(fName + " " + lName);
            b.tvEmail.setText(email);
            b.tvRole.setText("STUDENT");
            b.tvCampus.setText(campus);
            b.tvCollege.setText(college);
            b.tvCourse.setText(course);
            b.tvInitials.setText(initials(fName, lName));

            if (photoB64 != null && !photoB64.isEmpty()) {
                loadBase64Image(photoB64, b.ivProfilePhoto);
                b.tvInitials.setVisibility(View.GONE);
            } else {
                b.tvInitials.setVisibility(View.VISIBLE);
            }
        });
    }

    private void uploadProfilePhoto(Uri uri) {
        String uid = auth.getUid();
        if (uid == null) return;
        b.progressPhoto.setVisibility(View.VISIBLE);
        String base64 = compressToBase64(uri, 200, 70);
        if (base64 == null) {
            b.progressPhoto.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("users").document(uid).update("photoBase64", base64)
                .addOnSuccessListener(v -> {
                    loadBase64Image(base64, b.ivProfilePhoto);
                    b.tvInitials.setVisibility(View.GONE);
                    b.progressPhoto.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Photo updated!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    b.progressPhoto.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String compressToBase64(Uri uri, int maxWidth, int quality) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(), uri);
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                original = Bitmap.createScaledBitmap(original, maxWidth,
                        Math.round(original.getHeight() * ratio), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) { return null; }
    }

    private void loadBase64Image(String base64, ImageView imageView) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            imageView.setVisibility(View.VISIBLE);
        } catch (Exception e) { imageView.setVisibility(View.GONE); }
    }

    private void showChangePasswordDialog() {
        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_change_password, null);
        EditText etCurrent = form.findViewById(R.id.et_current_password);
        EditText etNew     = form.findViewById(R.id.et_new_password);
        EditText etConfirm = form.findViewById(R.id.et_confirm_password);
        TextView tvError   = form.findViewById(R.id.tv_error);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Change Password");
        builder.setView(form);
        builder.setPositiveButton("Update", null);
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String current = etCurrent.getText().toString().trim();
            String newPw   = etNew.getText().toString().trim();
            String confirm = etConfirm.getText().toString().trim();
            if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
                tvError.setVisibility(View.VISIBLE); tvError.setText("All fields required"); return;
            }
            if (!newPw.equals(confirm)) {
                tvError.setVisibility(View.VISIBLE); tvError.setText("Passwords do not match"); return;
            }
            if (newPw.length() < 6) {
                tvError.setVisibility(View.VISIBLE); tvError.setText("Minimum 6 characters"); return;
            }
            dialog.dismiss();
            reauthAndChangePassword(current, newPw);
        });
    }

    private void reauthAndChangePassword(String current, String newPw) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;
        AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), current);
        user.reauthenticate(cred)
                .addOnSuccessListener(a -> user.updatePassword(newPw)
                        .addOnSuccessListener(v ->
                                Toast.makeText(getContext(), "Password updated!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Current password incorrect", Toast.LENGTH_SHORT).show());
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (d, w) -> {
                    auth.signOut();
                    Intent i = new Intent(getActivity(), LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bv= l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bv;
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
