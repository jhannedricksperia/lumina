package com.example.luminae.activities;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.example.luminae.databinding.ActivityUserProfileBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class UserProfileActivity extends AppCompatActivity {

    private ActivityUserProfileBinding b;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b  = ActivityUserProfileBinding.inflate(getLayoutInflater());
        db = FirebaseFirestore.getInstance();
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        getSupportActionBar().setTitle("Profile");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        String uid = getIntent().getStringExtra("uid");
        if (uid != null) loadUser(uid);
    }

    private void loadUser(String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            String fName    = doc.getString("fName")       != null ? doc.getString("fName")       : "";
            String lName    = doc.getString("lName")       != null ? doc.getString("lName")       : "";
            String desig    = doc.getString("designation") != null ? doc.getString("designation") : "";
            String role     = doc.getString("role")        != null ? doc.getString("role")        : "";
            String photoB64 = doc.getString("photoBase64");
            Timestamp joined = doc.getTimestamp("createdAt");

            b.tvFullName.setText(fName + " " + lName);
            b.tvDesignation.setText(desig.isEmpty() ? role.toUpperCase() : desig);
            b.tvInitials.setText(initials(fName, lName));

            if (joined != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
                b.tvDateJoined.setText("Joined " + sdf.format(joined.toDate()));
            }

            if (photoB64 != null && !photoB64.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(photoB64, Base64.DEFAULT);
                    b.ivProfilePhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    b.ivProfilePhoto.setVisibility(View.VISIBLE);
                    b.tvInitials.setVisibility(View.GONE);
                } catch (Exception ignored) {}
            }
        });
    }

    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bv= l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bv;
    }
}
