package com.example.luminae.student.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.luminae.R;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.databinding.FragmentStudentEventsBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StudentEventsFragment extends Fragment {

    private FragmentStudentEventsBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private MyEventsAdapter adapter;

    private List<EventItem> items = new ArrayList<>();
    private String sortOrder = "recent"; // recent | oldest

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b    = FragmentStudentEventsBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new MyEventsAdapter();
        b.recyclerMyEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recyclerMyEvents.setAdapter(adapter);

        b.chipRecent.setOnClickListener(v -> { sortOrder = "recent"; sortAndRefresh(); });
        b.chipOldest.setOnClickListener(v -> { sortOrder = "oldest"; sortAndRefresh(); });

        loadMyEvents();
        return b.getRoot();
    }

    private void loadMyEvents() {
        String uid = auth.getUid();
        if (uid == null) return;

        b.progressEvents.setVisibility(View.VISIBLE);

        // Query all events where this user is a participant
        db.collectionGroup("participants")
                .whereEqualTo("uid", uid)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) {
                        b.progressEvents.setVisibility(View.GONE);
                        return;
                    }
                    List<String> eventIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        // Parent path: events/{eventId}/participants/{uid}
                        String path = doc.getReference().getPath();
                        String[] parts = path.split("/");
                        if (parts.length >= 2) eventIds.add(parts[1]);
                    }

                    if (eventIds.isEmpty()) {
                        b.progressEvents.setVisibility(View.GONE);
                        items.clear();
                        b.tvEmptyState.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                        return;
                    }
                    b.tvEmptyState.setVisibility(View.GONE);
                    fetchEventDetails(eventIds);
                });
    }

    private void fetchEventDetails(List<String> eventIds) {
        items.clear();
        final int[] done = {0};
        for (String id : eventIds) {
            db.collection("events").document(id).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            EventItem item = new EventItem();
                            item.docId       = doc.getId();
                            item.title       = doc.getString("title")       != null ? doc.getString("title")       : "";
                            item.description = doc.getString("description") != null ? doc.getString("description") : "";
                            item.location    = doc.getString("location")    != null ? doc.getString("location")    : "";
                            item.eventDate   = doc.getString("eventDate")   != null ? doc.getString("eventDate")   : "";
                            item.postedByName= doc.getString("postedByName")!= null ? doc.getString("postedByName"): "";
                            Timestamp ts     = doc.getTimestamp("createdAt");
                            item.createdAt   = ts != null ? ts.toDate() : new Date(0);
                            Long max         = doc.getLong("maxParticipants");
                            Long cnt         = doc.getLong("participantCount");
                            item.maxParticipants  = max != null ? max : 0;
                            item.participantCount = cnt != null ? cnt : 0;
                            items.add(item);
                        }
                        done[0]++;
                        if (done[0] == eventIds.size()) {
                            b.progressEvents.setVisibility(View.GONE);
                            sortAndRefresh();
                        }
                    });
        }
    }

    private void sortAndRefresh() {
        items.sort((a, c) -> {
            if (a.createdAt == null || c.createdAt == null) return 0;
            return "recent".equals(sortOrder)
                    ? c.createdAt.compareTo(a.createdAt)
                    : a.createdAt.compareTo(c.createdAt);
        });
        b.tvCount.setText(items.size() + " event(s) joined");
        adapter.notifyDataSetChanged();
    }

    static class EventItem {
        String docId, title, description, location, eventDate, postedByName;
        Date   createdAt;
        long   maxParticipants, participantCount;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class MyEventsAdapter extends RecyclerView.Adapter<MyEventsAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDescription, tvLocation, tvEventDate;
            TextView tvPostedBy, tvParticipants, tvCreatedDate;
            View cardRoot;

            VH(View v) {
                super(v);
                cardRoot       = v.findViewById(R.id.card_root);
                tvTitle        = v.findViewById(R.id.tv_title);
                tvDescription  = v.findViewById(R.id.tv_description);
                tvLocation     = v.findViewById(R.id.tv_location);
                tvEventDate    = v.findViewById(R.id.tv_event_date);
                tvPostedBy     = v.findViewById(R.id.tv_posted_by);
                tvParticipants = v.findViewById(R.id.tv_participants);
                tvCreatedDate  = v.findViewById(R.id.tv_created_date);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_event_post, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            EventItem item = items.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            h.tvTitle.setText(item.title);
            h.tvDescription.setText(item.description);
            h.tvLocation.setText(item.location);
            h.tvEventDate.setText(item.eventDate);
            h.tvPostedBy.setText("By " + item.postedByName);
            h.tvCreatedDate.setText(item.createdAt != null ? sdf.format(item.createdAt) : "");

            String participants = item.maxParticipants > 0
                    ? item.participantCount + " / " + item.maxParticipants + " going"
                    : item.participantCount + " going";
            h.tvParticipants.setText(participants);

            h.cardRoot.setOnClickListener(v -> {
                Intent i = new Intent(getActivity(), PostDetailActivity.class);
                i.putExtra("docId", item.docId);
                i.putExtra("type", "Event");
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
