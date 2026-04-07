package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityEventParticipantsBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class EventParticipantsActivity extends AppCompatActivity {

    private ActivityEventParticipantsBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> participants = new ArrayList<>();
    private ParticipantAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEventParticipantsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        b.toolbar.setTitle("Event Participants");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ParticipantAdapter();
        b.recyclerParticipants.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerParticipants.setAdapter(adapter);

        String eventId = getIntent().getStringExtra("eventId");
        if (eventId != null) loadParticipants(eventId);
    }

    private void loadParticipants(String eventId) {
        db.collection("events")
                .document(eventId)
                .collection("participants")
                .orderBy("joinedAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    participants = snap.getDocuments();
                    b.tvCount.setText(participants.size() + " participant(s)");
                    adapter.notifyDataSetChanged();
                });
    }

    private class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView ivProfilePhoto;
            TextView  tvInitials, tvFullName, tvDate;
            VH(View v) {
                super(v);
                ivProfilePhoto = v.findViewById(R.id.iv_profile_photo);
                tvInitials     = v.findViewById(R.id.tv_initials);
                tvFullName     = v.findViewById(R.id.tv_full_name);
                tvDate         = v.findViewById(R.id.tv_date_joined);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_participant, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = participants.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            Timestamp joined = doc.getTimestamp("joinedAt");
            String uid = doc.getString("uid") != null ? doc.getString("uid") : "";

            h.tvFullName.setText("");
            h.tvDate.setText(joined != null ? sdf.format(joined.toDate()) : "—");
            h.tvInitials.setText("");
            h.ivProfilePhoto.setImageDrawable(null);
            h.ivProfilePhoto.setVisibility(View.GONE);

            if (!uid.isEmpty()) {
                bindUserInfo(h, uid);
            }
            View.OnClickListener openProfile = v -> {
                if (uid.isEmpty()) return;
                Intent i = new Intent(EventParticipantsActivity.this, UserProfileActivity.class);
                i.putExtra("uid", uid);
                startActivity(i);
            };
            h.itemView.setOnClickListener(openProfile);
            h.tvFullName.setOnClickListener(openProfile);
        }

        @Override public int getItemCount() { return participants.size(); }
    }

    private void bindUserInfo(ParticipantAdapter.VH h, String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String fName  = doc.getString("fName") != null ? doc.getString("fName") : "";
            String lName  = doc.getString("lName") != null ? doc.getString("lName") : "";

            h.tvFullName.setText((fName + " " + lName).trim());
            h.tvInitials.setText(initials(fName, lName));

            ImageView iv = h.ivProfilePhoto;
            TextView initialsView = h.tvInitials;

            String b64 = doc.getString("photoBase64");
            if (b64 == null || b64.isEmpty()) return;
            try {
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    iv.setImageBitmap(bmp);
                    iv.setVisibility(View.VISIBLE);
                    if (initialsView != null) initialsView.setVisibility(View.GONE);
                }
            } catch (Exception ignored) { }
        });
    }

    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bv = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bv;
    }
}
