package com.example.luminae.staff.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.luminae.R;
import com.example.luminae.activities.AnnouncementFormActivity;
import com.example.luminae.activities.EventFormActivity;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.activities.UserProfileActivity;
import com.example.luminae.admin.fragments.ActivityLogger;
import com.example.luminae.utils.NotificationHelper;
import com.example.luminae.databinding.FragmentStaffPostBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StaffPostFragment extends Fragment {

    private FragmentStaffPostBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FeedAdapter adapter;
    private List<FeedItem> allItems = new ArrayList<>(), filtered = new ArrayList<>();
    private String currentType  = "All";
    private String currentOrder = "desc";
    private String staffUid         = "";
    private String staffName        = "";
    private String staffDesignation = "";
    private boolean staffInfoLoaded = false;
    private String staffPhotoBase64   = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b        = FragmentStaffPostBinding.inflate(inflater, container, false);
        db       = FirebaseFirestore.getInstance();
        auth     = FirebaseAuth.getInstance();
        staffUid = auth.getUid() != null ? auth.getUid() : "";

        loadStaffInfo();
        setupFilters();
        loadFeed();

        b.fabPost.setOnClickListener(v -> {
            if (!staffInfoLoaded) {
                Toast.makeText(getContext(), "Loading profile, please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            Class<?> target = "Event".equals(currentType) ? EventFormActivity.class : AnnouncementFormActivity.class;
            Intent i = new Intent(getActivity(), target);
            i.putExtra("collection", "Event".equals(currentType) ? "events" : "announcements");
            startActivity(i);
        });

        return b.getRoot();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Staff info
    // ─────────────────────────────────────────────────────────────────────────
    private void loadStaffInfo() {
        if (staffUid.isEmpty()) return;
        db.collection("users").document(staffUid).get().addOnSuccessListener(doc -> {
            if (doc == null || b == null) return;
            String fName = doc.getString("fName") != null ? doc.getString("fName") : "";
            String lName = doc.getString("lName") != null ? doc.getString("lName") : "";
            staffName        = (fName + " " + lName).trim();
            staffDesignation = doc.getString("designation") != null ? doc.getString("designation") : "Staff";
            if (staffName.isEmpty())
                staffName = doc.getString("email") != null ? doc.getString("email") : "Staff";
            // Cache the photo so feed cards load without extra reads
            String rawPhoto = doc.getString("photoBase64");
            staffPhotoBase64 = rawPhoto != null ? rawPhoto : "";
            staffInfoLoaded = true;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feed filters
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFilters() {
        b.chipAll.setOnClickListener(v          -> { currentType  = "All";          applyFilter(); });
        b.chipAnnouncement.setOnClickListener(v -> { currentType  = "Announcement"; applyFilter(); });
        b.chipEvent.setOnClickListener(v        -> { currentType  = "Event";        applyFilter(); });
        b.chipDesc.setOnClickListener(v         -> { currentOrder = "desc";         applyFilter(); });
        b.chipAsc.setOnClickListener(v          -> { currentOrder = "asc";          applyFilter(); });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feed loading
    // ─────────────────────────────────────────────────────────────────────────
    private void loadFeed() {
        b.progressFeed.setVisibility(View.VISIBLE);
        final int[] done = {0};

        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (int j = allItems.size() - 1; j >= 0; j--)
                        if ("Announcement".equals(allItems.get(j).type)) allItems.remove(j);
                    for (DocumentSnapshot doc : snap.getDocuments())
                        allItems.add(docToItem(doc, "Announcement"));
                    if (++done[0] >= 2) { b.progressFeed.setVisibility(View.GONE); applyFilter(); }
                });

        db.collection("events").orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (int j = allItems.size() - 1; j >= 0; j--)
                        if ("Event".equals(allItems.get(j).type)) allItems.remove(j);
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        FeedItem f = docToItem(doc, "Event");
                        f.location         = doc.getString("location")  != null ? doc.getString("location")  : "";
                        f.eventDate        = doc.getString("eventDate") != null ? doc.getString("eventDate") : "";
                        Long max = doc.getLong("maxParticipants");
                        Long cnt = doc.getLong("participantCount");
                        f.maxParticipants  = max != null ? max : 0;
                        f.participantCount = cnt != null ? cnt : 0;
                        allItems.add(f);
                    }
                    if (++done[0] >= 2) { b.progressFeed.setVisibility(View.GONE); applyFilter(); }
                });
    }

    private FeedItem docToItem(DocumentSnapshot doc, String type) {
        FeedItem f     = new FeedItem();
        f.docId        = doc.getId();
        f.type         = type;
        f.title        = doc.getString("title")        != null ? doc.getString("title")        : "";
        f.description  = doc.getString("description")  != null ? doc.getString("description")  : "";
        f.postedBy     = doc.getString("postedBy")     != null ? doc.getString("postedBy")     : "";
        f.postedByName = doc.getString("postedByName") != null ? doc.getString("postedByName") : "";
        f.audienceLabel= doc.getString("audienceLabel")!= null ? doc.getString("audienceLabel"): "Everyone";
        f.postedByPhoto = doc.getString("postedByPhoto");
        f.imageBase64  = doc.getString("imageBase64");
        Timestamp ts   = doc.getTimestamp("createdAt");
        f.createdAt    = ts != null ? ts.toDate() : new Date(0);
        Long l = doc.getLong("likeCount");
        Long c = doc.getLong("commentCount");
        f.likeCount    = l != null ? l.intValue() : 0;
        f.commentCount = c != null ? c.intValue() : 0;
        f.isMyPost     = staffUid.equals(f.postedBy);
        return f;
    }

    private void applyFilter() {
        filtered.clear();
        for (FeedItem item : allItems)
            if (currentType.equals("All") || currentType.equals(item.type)) filtered.add(item);
        filtered.sort((a, c) -> {
            if (a.createdAt == null || c.createdAt == null) return 0;
            return "desc".equals(currentOrder)
                    ? c.createdAt.compareTo(a.createdAt)
                    : a.createdAt.compareTo(c.createdAt);
        });
        if (adapter == null) {
            adapter = new FeedAdapter();
            b.recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
            b.recyclerFeed.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    // Creation and editing are now handled by dedicated form activities.

    // ─────────────────────────────────────────────────────────────────────────
    // Data model
    // ─────────────────────────────────────────────────────────────────────────
    static class FeedItem {
        String  docId, type, title, description, postedBy, postedByName, postedByPhoto;
        String  location, eventDate, audienceLabel, imageBase64;
        long    maxParticipants, participantCount;
        Date    createdAt;
        int     likeCount, commentCount;
        boolean isMyPost;
        boolean likedByMe = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView adapter
    // ─────────────────────────────────────────────────────────────────────────
    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView  tvTypeBadge, tvPosterName, tvTime, tvTitle, tvDescription;
            TextView  tvLikeCount, tvCommentCount, tvGoingInfo, tvMyPostBadge, tvAudience;
            TextView  btnLike;   // R.id.btn_like is a TextView in the layout
            ImageView ivPosterPhoto, ivPostImage, btnMore;
            View      cardRoot, btnComment;

            VH(View v) {
                super(v);
                cardRoot       = v.findViewById(R.id.card_root);
                tvTypeBadge    = v.findViewById(R.id.tv_type_badge);
                ivPosterPhoto  = v.findViewById(R.id.iv_poster_photo);
                ivPostImage    = v.findViewById(R.id.iv_post_image);
                btnMore        = v.findViewById(R.id.btn_more);
                tvPosterName   = v.findViewById(R.id.tv_poster_name);
                tvTime         = v.findViewById(R.id.tv_time);
                tvTitle        = v.findViewById(R.id.tv_title);
                tvDescription  = v.findViewById(R.id.tv_description);
                tvLikeCount    = v.findViewById(R.id.btn_like);   // same view as btnLike
                btnLike        = v.findViewById(R.id.btn_like);
                tvCommentCount = v.findViewById(R.id.btn_comment);
                btnComment     = v.findViewById(R.id.btn_comment);
                tvGoingInfo    = v.findViewById(R.id.tv_going_info);
                tvMyPostBadge  = v.findViewById(R.id.tv_my_post_badge);
                tvAudience     = v.findViewById(R.id.tv_audience);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_feed_post, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            FeedItem item = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d  •  h:mm a", Locale.getDefault());

            h.tvTypeBadge.setText(item.type);
            h.tvTypeBadge.setBackgroundResource(
                    "Announcement".equals(item.type) ? R.drawable.badge_announcement : R.drawable.badge_event);
            h.tvPosterName.setText(item.postedByName);
            loadProfilePhoto(h.ivPosterPhoto, item.postedByPhoto, item.postedBy);
            h.tvTime.setText(item.createdAt != null ? sdf.format(item.createdAt) : "");
            h.tvTitle.setText(item.title);
            h.tvDescription.setText(item.description);
            if (h.ivPostImage != null) {
                if (item.imageBase64 != null && !item.imageBase64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            h.ivPostImage.setImageBitmap(bmp);
                            h.ivPostImage.setVisibility(View.VISIBLE);
                        } else h.ivPostImage.setVisibility(View.GONE);
                    } catch (Exception ignored) { h.ivPostImage.setVisibility(View.GONE); }
                } else {
                    h.ivPostImage.setVisibility(View.GONE);
                }
            }

            // ── Like: check Firestore on bind so likedByMe is always accurate ──
            String uid  = auth.getUid();
            String col  = "Announcement".equals(item.type) ? "announcements" : "events";
            DocumentReference postRef = db.collection(col).document(item.docId);
            DocumentReference likeRef = uid != null
                    ? postRef.collection("likes").document(uid) : null;

            if (likeRef != null) {
                likeRef.get().addOnSuccessListener(likeDoc -> {
                    item.likedByMe = likeDoc.exists();
                    updateLikeUI(h, item);
                });
            }
            updateLikeUI(h, item);   // immediate render with cached value

            if (h.btnLike != null) {
                h.btnLike.setOnClickListener(v -> {
                    if (uid == null || likeRef == null) return;
                    // Always read current state from Firestore to prevent double-like
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        if (likeDoc.exists()) {
                            // Unlike
                            likeRef.delete();
                            postRef.update("likeCount", FieldValue.increment(-1));
                            item.likedByMe = false;
                            item.likeCount = Math.max(0, item.likeCount - 1);
                        } else {
                            // Like
                            Map<String, Object> likeData = new HashMap<>();
                            likeData.put("uid",     uid);
                            likeData.put("likedAt", Timestamp.now());
                            likeRef.set(likeData);
                            postRef.update("likeCount", FieldValue.increment(1));
                            item.likedByMe = true;
                            item.likeCount++;
                            // Audit log
                            ActivityLogger.log(
                                    "Announcement".equals(item.type)
                                            ? ActivityLogger.MODULE_ANNOUNCEMENT
                                            : ActivityLogger.MODULE_EVENT,
                                    "Liked", item.title);
                            // Notify poster
                            NotificationHelper.notifyLike(
                                    item.postedBy, staffName, item.title,
                                    item.docId,
                                    "Announcement".equals(item.type) ? "announcements" : "events");
                        }
                        updateLikeUI(h, item);
                    });
                });
            }

            // Comment count
            if (h.tvCommentCount != null) h.tvCommentCount.setText("Comments: " + item.commentCount);

            // Going info (events only)
            if ("Event".equals(item.type)) {
                if (h.tvGoingInfo != null) {
                    h.tvGoingInfo.setVisibility(View.VISIBLE);
                    String going = item.maxParticipants > 0
                            ? item.participantCount + " / " + item.maxParticipants + " going"
                            : item.participantCount + " going";
                    h.tvGoingInfo.setText("Going: " + going);
                }
            } else {
                if (h.tvGoingInfo != null) h.tvGoingInfo.setVisibility(View.GONE);
            }

            // Audience label
            if (h.tvAudience != null) {
                String label = item.audienceLabel != null && !item.audienceLabel.isEmpty()
                        ? item.audienceLabel : "Everyone";
                h.tvAudience.setText("Audience: " + label);
            }

            // My post badge + more button
            if (h.tvMyPostBadge != null) h.tvMyPostBadge.setVisibility(item.isMyPost ? View.VISIBLE : View.GONE);
            if (h.btnMore       != null) h.btnMore.setVisibility(item.isMyPost ? View.VISIBLE : View.GONE);

            // Poster photo + name → open UserProfileActivity
            View.OnClickListener openProfile = v -> openUserProfile(item.postedBy);
            if (h.ivPosterPhoto != null) h.ivPosterPhoto.setOnClickListener(openProfile);
            if (h.tvPosterName  != null) h.tvPosterName.setOnClickListener(openProfile);

            // Comment button → open PostDetailActivity (same as card click)
            if (h.btnComment != null) {
                h.btnComment.setOnClickListener(v -> openDetail(item));
            }

            // Card click → detail
            h.cardRoot.setOnClickListener(v -> openDetail(item));
            h.cardRoot.setOnLongClickListener(v -> {
                if (item.isMyPost) showPostOptions(item);
                return item.isMyPost;
            });

            if (h.btnMore != null) h.btnMore.setOnClickListener(v -> showPostOptions(item));
        }

        private void showPostOptions(FeedItem item) {
            String[] items = {"Edit", "Archive", "Delete"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Post Options")
                    .setItems(items, (dialog, which) -> {
                        if (which == 0) {
                            Intent i = new Intent(getActivity(),
                                    "Announcement".equals(item.type) ? AnnouncementFormActivity.class : EventFormActivity.class);
                            i.putExtra("doc_id", item.docId);
                            i.putExtra("collection", "Announcement".equals(item.type) ? "announcements" : "events");
                            startActivity(i);
                        } else if (which == 1) {
                            String col = "Announcement".equals(item.type) ? "announcements" : "events";
                            DocumentReference ref = db.collection(col).document(item.docId);
                            ref.get().addOnSuccessListener(doc -> {
                                String currentStatus = doc.getString("status");
                                boolean isArchived = "Archive".equalsIgnoreCase(currentStatus)
                                        || "Archived".equalsIgnoreCase(currentStatus);
                                ref.update("status", isArchived ? "Active" : "Archive");
                            });
                        } else {
                            String col = "Announcement".equals(item.type) ? "announcements" : "events";
                            db.collection(col).document(item.docId).delete();
                            ActivityLogger.log(
                                    "Announcement".equals(item.type)
                                            ? ActivityLogger.MODULE_ANNOUNCEMENT
                                            : ActivityLogger.MODULE_EVENT,
                                    ActivityLogger.ACTION_DELETE, item.title);
                        }
                    })
                    .show();
        }

        private void openDetail(FeedItem item) {
            Intent i = new Intent(getActivity(), PostDetailActivity.class);
            i.putExtra("docId", item.docId);
            i.putExtra("type",  item.type);
            startActivity(i);
        }

        private void openUserProfile(String uid) {
            if (uid == null || uid.isEmpty()) return;
            Intent i = new Intent(getActivity(), UserProfileActivity.class);
            i.putExtra("uid", uid);
            startActivity(i);
        }

        private void loadProfilePhoto(android.widget.ImageView iv, String photoBase64, String uid) {
            // First try the cached field on the post document (fast, no extra read)
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) { iv.setImageBitmap(bmp); return; }
                } catch (Exception ignored) {}
            }
            // Fallback: fetch from users collection
            if (uid == null || uid.isEmpty()) return;
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                String b64 = doc.getString("photoBase64");
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) iv.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }
            });
        }

        private void updateLikeUI(VH h, FeedItem item) {
            if (h.tvLikeCount == null) return;
            h.tvLikeCount.setText("♥ " + item.likeCount);
            int color = item.likedByMe ? 0xFFEF5350 : 0x88FFFFFF;
            h.tvLikeCount.setTextColor(color);
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}