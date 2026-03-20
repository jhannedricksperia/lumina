package com.example.luminae.admin.fragments;

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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.activities.LoginActivity;
import com.example.luminae.databinding.FragmentAdminProfileBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class AdminProfileFragment extends Fragment {

    private FragmentAdminProfileBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ActivityResultLauncher<String> photoPicker;
    private ActivityAdapter activityAdapter;
    private List<DocumentSnapshot> activityLogs = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b = FragmentAdminProfileBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Photo picker — compress and save to Firestore as Base64
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) uploadProfilePhoto(uri); });

        activityAdapter = new ActivityAdapter();
        b.recyclerActivity.setLayoutManager(new LinearLayoutManager(getContext()));
        b.recyclerActivity.setAdapter(activityAdapter);

        loadProfile();
        loadActivityLog();

        b.ivProfilePhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.btnEditPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        b.btnLogout.setOnClickListener(v -> showLogoutDialog());

        return b.getRoot();
    }

    // ── Load Profile ─────────────────────────────────────────────────────────
    private void loadProfile() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String fName   = doc.getString("fName")  != null ? doc.getString("fName")  : "";
                    String lName   = doc.getString("lName")  != null ? doc.getString("lName")  : "";
                    String email   = doc.getString("email")  != null ? doc.getString("email")  : "";
                    String role    = doc.getString("role")   != null ? doc.getString("role")   : "admin";
                    String photoB64 = doc.getString("photoBase64");

                    b.tvFullName.setText(fName + " " + lName);
                    b.tvEmail.setText(email);
                    b.tvRole.setText(role.toUpperCase());
                    b.tvInitials.setText(initials(fName, lName));

                    // Load profile photo from Base64
                    if (photoB64 != null && !photoB64.isEmpty()) {
                        loadBase64Image(photoB64, b.ivProfilePhoto);
                        b.tvInitials.setVisibility(View.GONE);
                    } else {
                        b.tvInitials.setVisibility(View.VISIBLE);
                    }
                });
    }

    // ── Upload Profile Photo (Base64) ────────────────────────────────────────
    private void uploadProfilePhoto(Uri uri) {
        String uid = auth.getUid();
        if (uid == null) return;

        b.progressPhoto.setVisibility(View.VISIBLE);

        String base64 = compressToBase64(uri, 200, 70); // 200px, 70% quality for avatar
        if (base64 == null) {
            b.progressPhoto.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(uid)
                .update("photoBase64", base64)
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

    // ── Compress Uri → Base64 ────────────────────────────────────────────────
    private String compressToBase64(Uri uri, int maxWidth, int quality) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(), uri);

            // Scale down if needed
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                int newHeight = Math.round(original.getHeight() * ratio);
                original = Bitmap.createScaledBitmap(original, maxWidth, newHeight, true);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── Decode Base64 → ImageView ────────────────────────────────────────────
    private void loadBase64Image(String base64, ImageView imageView) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            // Crop to circle for profile photo
            imageView.setImageBitmap(bmp);
            imageView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            imageView.setVisibility(View.GONE);
        }
    }

    // ── Change Password Dialog ───────────────────────────────────────────────
    private void showChangePasswordDialog() {
        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_change_password, null);
        EditText etCurrent = form.findViewById(R.id.et_current_password);
        EditText etNew     = form.findViewById(R.id.et_new_password);
        EditText etConfirm = form.findViewById(R.id.et_confirm_password);
        TextView tvError   = form.findViewById(R.id.tv_error);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Password")
                .setView(form)
                .setPositiveButton("Update", (d, w) -> {
                    String current = etCurrent.getText().toString().trim();
                    String newPw   = etNew.getText().toString().trim();
                    String confirm = etConfirm.getText().toString().trim();

                    if (current.isEmpty() || newPw.isEmpty()) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("All fields required");
                        return;
                    }
                    if (!newPw.equals(confirm)) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Passwords do not match");
                        return;
                    }
                    if (newPw.length() < 6) {
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Minimum 6 characters");
                        return;
                    }
                    reauthAndChangePassword(current, newPw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reauthAndChangePassword(String current, String newPw) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), current);
        user.reauthenticate(credential)
                .addOnSuccessListener(a -> user.updatePassword(newPw)
                        .addOnSuccessListener(v ->
                                Toast.makeText(getContext(),
                                        "Password updated!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Current password incorrect", Toast.LENGTH_SHORT).show());
    }

    // ── Activity Log ─────────────────────────────────────────────────────────
    private void loadActivityLog() {
        String uid = auth.getUid();
        if (uid == null) return;
        db.collection("activityLogs")
                .whereEqualTo("uid", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    activityLogs = snap.getDocuments();
                    activityAdapter.notifyDataSetChanged();
                });
    }

    // ── Logout ───────────────────────────────────────────────────────────────
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
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Activity Adapter ─────────────────────────────────────────────────────
    private class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvAction, tvTimestamp;
            VH(View v) {
                super(v);
                tvAction    = v.findViewById(R.id.tv_action);
                tvTimestamp = v.findViewById(R.id.tv_timestamp);
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_activity_log, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = activityLogs.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault());
            h.tvAction.setText(doc.getString("action") != null ? doc.getString("action") : "—");
            Timestamp ts = doc.getTimestamp("timestamp");
            h.tvTimestamp.setText(ts != null ? sdf.format(ts.toDate()) : "—");
        }
        @Override public int getItemCount() { return activityLogs.size(); }
    }

    private String initials(String f, String l) {
        String a  = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bv = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bv;
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}