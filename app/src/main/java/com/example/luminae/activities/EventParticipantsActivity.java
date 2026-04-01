package com.example.luminae.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        String eventId    = getIntent().getStringExtra("eventId");
        String eventTitle = getIntent().getStringExtra("eventTitle");

        setSupportActionBar(b.toolbar);
        b.toolbar.setTitle(eventTitle != null ? eventTitle : "Participants");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ParticipantAdapter();
        b.recyclerParticipants.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerParticipants.setAdapter(adapter);

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
            TextView tvInitials, tvFullName, tvCampus, tvCollege, tvCourse, tvDate;
            VH(View v) {
                super(v);
                tvInitials = v.findViewById(R.id.tv_initials);
                tvFullName = v.findViewById(R.id.tv_full_name);
                tvCampus   = v.findViewById(R.id.tv_campus);
                tvCollege  = v.findViewById(R.id.tv_college);
                tvCourse   = v.findViewById(R.id.tv_course);
                tvDate     = v.findViewById(R.id.tv_date_joined);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_participant, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = participants.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String fName  = doc.getString("fName")  != null ? doc.getString("fName")  : "";
            String lName  = doc.getString("lName")   != null ? doc.getString("lName")  : "";
            String campus = doc.getString("campus")  != null ? doc.getString("campus") : "—";
            String college= doc.getString("college") != null ? doc.getString("college"): "—";
            String course = doc.getString("course")  != null ? doc.getString("course") : "—";
            Timestamp joined = doc.getTimestamp("joinedAt");

            h.tvFullName.setText(fName + " " + lName);
            h.tvCampus.setText(campus);
            h.tvCollege.setText(college);
            h.tvCourse.setText(course);
            h.tvDate.setText(joined != null ? sdf.format(joined.toDate()) : "—");
            h.tvInitials.setText(initials(fName, lName));

            String uid = doc.getString("uid") != null ? doc.getString("uid") : "";
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

    private String initials(String f, String l) {
        String a = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bv = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bv;
    }
}
