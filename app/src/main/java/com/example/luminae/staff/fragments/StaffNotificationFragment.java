package com.example.luminae.staff.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.luminae.R;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.databinding.FragmentStaffNotificationBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StaffNotificationFragment extends Fragment {

    private FragmentStaffNotificationBinding b;
    private ListenerRegistration notificationsReg;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private NotifAdapter adapter;

    private List<DocumentSnapshot> all       = new ArrayList<>();
    private List<DocumentSnapshot> filtered = new ArrayList<>();
    private String filterRead = "All";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b    = FragmentStaffNotificationBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new NotifAdapter();
        b.recyclerNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recyclerNotifications.setAdapter(adapter);

        b.chipAll.setOnClickListener(v -> { filterRead = "All"; applyFilter(); });
        b.chipUnread.setOnClickListener(v -> { filterRead = "Unread"; applyFilter(); });
        b.chipRead.setOnClickListener(v -> { filterRead = "Read"; applyFilter(); });

        b.btnMarkAllRead.setOnClickListener(v -> markAllRead());

        loadNotifications();
        return b.getRoot();
    }

    private void loadNotifications() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            if (!isAdded() || b == null) return;
            String campus  = userDoc.getString("campus");
            String college = userDoc.getString("college");
            String course  = userDoc.getString("course");

            if (notificationsReg != null) {
                notificationsReg.remove();
                notificationsReg = null;
            }
            notificationsReg = db.collection("notifications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener((snap, e) -> {
                        if (snap == null || !isAdded() || b == null) return;
                        all.clear();
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String targetUid     = doc.getString("targetUid");
                            String targetCampus  = doc.getString("targetCampus");
                            String targetCollege = doc.getString("targetCollege");
                            String targetCourse  = doc.getString("targetCourse");

                            boolean isForMe = uid.equals(targetUid)
                                    || (campus  != null && campus.equals(targetCampus))
                                    || (college != null && college.equals(targetCollege))
                                    || (course  != null && course.equals(targetCourse))
                                    || (targetUid == null && targetCampus == null
                                        && targetCollege == null && targetCourse == null);
                            if (isForMe) all.add(doc);
                        }
                        applyFilter();
                        updateUnreadBadge();
                    });
        });
    }

    private void applyFilter() {
        if (b == null) return;
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            Boolean read = doc.getBoolean("read");
            boolean isRead = read != null && read;
            if (filterRead.equals("All")
                    || (filterRead.equals("Read")   &&  isRead)
                    || (filterRead.equals("Unread") && !isRead)) {
                filtered.add(doc);
            }
        }
        b.tvCount.setText(filtered.size() + " notification(s)");
        adapter.notifyDataSetChanged();
    }

    private void updateUnreadBadge() {
        if (b == null) return;
        long unread = 0;
        for (DocumentSnapshot d : all) {
            Boolean r = d.getBoolean("read");
            if (r == null || !r) unread++;
        }
        b.tvUnreadCount.setText(unread > 0 ? String.valueOf(unread) : "");
        b.tvUnreadCount.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
    }

    private void markAllRead() {
        for (DocumentSnapshot doc : all) {
            Boolean read = doc.getBoolean("read");
            if (read == null || !read) {
                doc.getReference().update("read", true);
            }
        }
    }

    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvMessage, tvSubject, tvTime, tvUnreadDot;
            View cardRoot;

            VH(View v) {
                super(v);
                cardRoot    = v.findViewById(R.id.card_root);
                tvMessage   = v.findViewById(R.id.tv_message);
                tvSubject   = v.findViewById(R.id.tv_subject);
                tvTime      = v.findViewById(R.id.tv_time);
                tvUnreadDot = v.findViewById(R.id.tv_unread_dot);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_notification, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault());

            String message = doc.getString("message") != null ? doc.getString("message") : "";
            String subject = doc.getString("subject") != null ? doc.getString("subject") : "";
            Timestamp ts   = doc.getTimestamp("timestamp");
            Boolean read   = doc.getBoolean("read");
            boolean isRead = read != null && read;

            h.tvMessage.setText(message);
            h.tvSubject.setText(subject);
            h.tvTime.setText(ts != null ? sdf.format(ts.toDate()) : "");
            h.tvUnreadDot.setVisibility(isRead ? View.GONE : View.VISIBLE);

            h.cardRoot.setAlpha(isRead ? 0.75f : 1f);

            h.cardRoot.setOnClickListener(v -> {
                if (!isRead) doc.getReference().update("read", true);

                String refId  = doc.getString("refId");
                String refCol = doc.getString("refCollection");
                if (refId != null && refCol != null) {
                    Intent i = new Intent(getActivity(), PostDetailActivity.class);
                    i.putExtra("docId", refId);
                    i.putExtra("type", refCol.contains("event") ? "Event" : "Announcement");
                    startActivity(i);
                }
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() {
        if (notificationsReg != null) {
            notificationsReg.remove();
            notificationsReg = null;
        }
        super.onDestroyView();
        b = null;
    }
}
