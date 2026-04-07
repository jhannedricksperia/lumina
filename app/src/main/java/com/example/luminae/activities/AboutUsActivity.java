package com.example.luminae.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.luminae.databinding.ActivityAboutUsBinding;

public class AboutUsActivity extends AppCompatActivity {

    private ActivityAboutUsBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAboutUsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("About Us");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        b.tvAboutBody.setText(
                "University Announcement and Events Awareness App\n\n" +
                "Head Developer: \nJhann Edrick S. Peria\n\n" +
                "Assistant Developer: \nElohim Lee L. Manzano\n\n" +
                "UI/UX Designer: \nAshley Nicole C. Vargas\n\n" +
                "System Testers:\n" +
                "Kenneth Julian D. Aquino\n" +
                "Chester M. Avelino\n" +
                "Jayvee S. Clemente\n" +
                "John Mark V. Espineda\n\n" +
                "For other concerns regarding the system:\n\n" +
                "Email us at: applumina1@gmail.com\n\n" +
                "Reach us at: Lumina Office, City of Malolos, Bulacan, 3000, Philippines (8:00 AM to 5:00 PM)"
        );
    }
}

