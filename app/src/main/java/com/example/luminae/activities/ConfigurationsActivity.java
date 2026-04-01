package com.example.luminae.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityConfigurationsBinding;
import com.example.luminae.utils.PushPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ConfigurationsActivity extends AppCompatActivity {

    private ActivityConfigurationsBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityConfigurationsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.switchPush.setChecked(PushPreferences.isPushEnabled(this));

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && doc.contains("pushNotificationsEnabled")) {
                            Boolean pe = doc.getBoolean("pushNotificationsEnabled");
                            if (pe != null) {
                                PushPreferences.mergeFromFirestore(this, pe);
                                b.switchPush.setChecked(pe);
                            }
                        }
                    });
        }

        b.switchPush.setOnCheckedChangeListener((buttonView, isChecked) ->
                PushPreferences.setPushEnabled(this, isChecked));
    }
}
