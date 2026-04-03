package com.example.luminae.student.fragments;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.viewpager2.widget.ViewPager2;

import com.example.luminae.R;
import com.example.luminae.activities.EventParticipantsActivity;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.activities.UserProfileActivity;
import com.example.luminae.utils.EventDisplayUtils;
import com.example.luminae.utils.FullscreenImageGallery;
import com.example.luminae.utils.LikeIconHelper;
import com.example.luminae.utils.PostImageCarouselBinder;
import com.example.luminae.utils.PostImageList;
import com.example.luminae.databinding.FragmentStudentFeedBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class StudentFeedFragment extends Fragment {

    private FragmentStudentFeedBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FeedAdapter adapter;

    private List<FeedItem> allItems    = new ArrayList<>();
    private List<FeedItem> filtered    = new ArrayList<>();
    private String currentType   = "All";   // All | Announcement | Event
    private String currentOrder  = "desc";  // desc | asc

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b    = FragmentStudentFeedBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new FeedAdapter();
        b.recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recyclerFeed.setAdapter(adapter);

        // Search
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });

        // Type chips
        b.chipAll.setOnClickListener(v -> { currentType = "All"; applyFilter(); });
        b.chipAnnouncement.setOnClickListener(v -> { currentType = "Announcement"; applyFilter(); });
        b.chipEvent.setOnClickListener(v -> { currentType = "Event"; applyFilter(); });

        // Sort chips
        b.chipDesc.setOnClickListener(v -> { currentOrder = "desc"; applyFilter(); });
        b.chipAsc.setOnClickListener(v -> { currentOrder = "asc"; applyFilter(); });

        if (b.btnSearch != null) {
            b.btnSearch.setOnClickListener(v -> {
                hideKeyboard();
                applyFilter();
            });
        }
        b.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                applyFilter();
                return true;
            }
            return false;
        });

        loadFeed();
        return b.getRoot();
    }

    private void hideKeyboard() {
        if (getContext() == null || b == null) return;
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.etSearch.getWindowToken(), 0);
    }

    private void loadFeed() {
        b.progressFeed.setVisibility(View.VISIBLE);
        final int[] done = {0};

        db.collection("announcements")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || b == null) return;
                    // Remove old announcements
                    for (int i = allItems.size() - 1; i >= 0; i--)
                        if ("Announcement".equals(allItems.get(i).type)) allItems.remove(i);
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        allItems.add(FeedItem.fromAnnouncement(doc));
                    }
                    if (++done[0] >= 2) {
                        b.progressFeed.setVisibility(View.GONE);
                        applyFilter();
                    }
                });

        db.collection("events")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || b == null) return;
                    for (int i = allItems.size() - 1; i >= 0; i--)
                        if ("Event".equals(allItems.get(i).type)) allItems.remove(i);
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        allItems.add(FeedItem.fromEvent(doc));
                    }
                    if (++done[0] >= 2) {
                        b.progressFeed.setVisibility(View.GONE);
                        applyFilter();
                    }
                });
    }

    private void applyFilter() {
        if (b == null) return;
        String q = b.etSearch.getText() != null
                ? b.etSearch.getText().toString().trim().toLowerCase() : "";

        filtered.clear();
        for (FeedItem item : allItems) {
            boolean matchType   = currentType.equals("All") || currentType.equals(item.type);
            boolean matchSearch = q.isEmpty()
                    || item.title.toLowerCase().contains(q)
                    || item.description.toLowerCase().contains(q);
            if (matchType && matchSearch) filtered.add(item);
        }

        // Sort
        filtered.sort((a, c) -> {
            if (a.createdAt == null || c.createdAt == null) return 0;
            return currentOrder.equals("desc")
                    ? c.createdAt.compareTo(a.createdAt)
                    : a.createdAt.compareTo(c.createdAt);
        });

        adapter.notifyDataSetChanged();
    }

    // ── Feed Item model ───────────────────────────────────────────────────────
    public static class FeedItem {
        public String  docId, type, title, description, imageBase64;
        public ArrayList<String> imagesBase64 = new ArrayList<>();
        public String  postedBy, postedByName, postedByDesignation, postedByPhoto;
        public Date    createdAt;
        public int     likeCount, commentCount;
        public boolean likedByMe;
        // Event-specific
        public String  location, eventDate;
        public long    maxParticipants, participantCount;
        public boolean goingByMe;

        private static String coalesce(String... parts) {
            if (parts == null) return "";
            for (String p : parts) {
                if (p != null && !p.trim().isEmpty()) return p.trim();
            }
            return "";
        }

        static FeedItem fromAnnouncement(DocumentSnapshot doc) {
            FeedItem f = new FeedItem();
            f.docId       = doc.getId();
            f.type        = "Announcement";
            f.title       = doc.getString("title")       != null ? doc.getString("title")       : "";
            f.description = doc.getString("description") != null ? doc.getString("description") : "";
            f.imagesBase64 = new ArrayList<>(PostImageList.fromDocument(doc));
            f.imageBase64 = f.imagesBase64.isEmpty() ? null : f.imagesBase64.get(0);
            f.postedBy    = coalesce(doc.getString("postedBy"), doc.getString("createdById"));
            f.postedByName = coalesce(doc.getString("postedByName"), doc.getString("createdByName"),
                    doc.getString("createdBy"));
            f.postedByDesignation = doc.getString("postedByDesignation") != null
                    ? doc.getString("postedByDesignation") : "";
            f.postedByPhoto = doc.getString("postedByPhoto");
            Timestamp ts  = doc.getTimestamp("createdAt");
            f.createdAt   = ts != null ? ts.toDate() : new Date(0);
            Long likes    = doc.getLong("likeCount");
            Long comments = doc.getLong("commentCount");
            f.likeCount   = likes    != null ? likes.intValue()    : 0;
            f.commentCount= comments != null ? comments.intValue() : 0;
            return f;
        }

        static FeedItem fromEvent(DocumentSnapshot doc) {
            FeedItem f = new FeedItem();
            f.docId        = doc.getId();
            f.type         = "Event";
            f.title        = doc.getString("title")       != null ? doc.getString("title")       : "";
            f.description  = doc.getString("description") != null ? doc.getString("description") : "";
            f.imagesBase64 = new ArrayList<>(PostImageList.fromDocument(doc));
            f.imageBase64  = f.imagesBase64.isEmpty() ? null : f.imagesBase64.get(0);
            f.postedBy     = coalesce(doc.getString("postedBy"), doc.getString("createdById"));
            f.postedByName = coalesce(doc.getString("postedByName"), doc.getString("createdByName"),
                    doc.getString("createdBy"));
            f.postedByDesignation = doc.getString("postedByDesignation") != null
                    ? doc.getString("postedByDesignation") : "";
            f.postedByPhoto = doc.getString("postedByPhoto");
            f.location = EventDisplayUtils.formatLocation(doc);
            f.eventDate = EventDisplayUtils.formatEventDate(doc);
            Long max       = doc.getLong("maxParticipants");
            f.maxParticipants  = max    != null ? max    : 0;
            f.participantCount = EventDisplayUtils.countGoing(doc);
            Timestamp ts   = doc.getTimestamp("createdAt");
            f.createdAt    = ts != null ? ts.toDate() : new Date(0);
            Long likes     = doc.getLong("likeCount");
            Long comments  = doc.getLong("commentCount");
            f.likeCount    = likes    != null ? likes.intValue()    : 0;
            f.commentCount = comments != null ? comments.intValue() : 0;
            return f;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvTypeBadge, tvPosterName, tvPosterDesig, tvTime;
            TextView tvTitle, tvDescription, tvLocation, tvEventDate;
            TextView tvLikeCount, tvCommentCount, tvGoingCount, tvFeedGoingCount;
            ImageView ivPosterPhoto, ivLike, ivComment;
            View btnLike, btnComment, btnGoing;
            View layoutEventInfo, layoutGoingRow, rowFeedGoing;
            View layoutPostMedia;
            ViewPager2 vpPostImages;
            LinearLayout dotsPostImages;
            View cardRoot;
            String boundCarouselDocId;
            String boundCarouselSig;

            VH(View v) {
                super(v);
                cardRoot          = v.findViewById(R.id.card_root);
                tvTypeBadge       = v.findViewById(R.id.tv_type_badge);
                tvPosterName      = v.findViewById(R.id.tv_poster_name);
                tvPosterDesig     = v.findViewById(R.id.tv_poster_designation);
                tvTime            = v.findViewById(R.id.tv_time);
                tvTitle           = v.findViewById(R.id.tv_title);
                tvDescription     = v.findViewById(R.id.tv_description);
                tvLocation        = v.findViewById(R.id.tv_location);
                tvEventDate       = v.findViewById(R.id.tv_event_date);
                tvLikeCount       = v.findViewById(R.id.tv_like_count);
                tvCommentCount    = v.findViewById(R.id.tv_comment_count);
                ivLike            = v.findViewById(R.id.iv_like);
                ivComment         = v.findViewById(R.id.iv_comment);
                tvGoingCount      = v.findViewById(R.id.tv_going_count);
                tvFeedGoingCount  = v.findViewById(R.id.tv_feed_going_count);
                ivPosterPhoto     = v.findViewById(R.id.iv_poster_photo);
                layoutPostMedia   = v.findViewById(R.id.layout_post_media);
                vpPostImages      = v.findViewById(R.id.vp_post_images);
                dotsPostImages    = v.findViewById(R.id.dots_post_images);
                btnLike           = v.findViewById(R.id.btn_like);
                btnComment        = v.findViewById(R.id.btn_comment);
                btnGoing          = v.findViewById(R.id.btn_going);
                layoutEventInfo   = v.findViewById(R.id.layout_event_info);
                layoutGoingRow    = v.findViewById(R.id.layout_going_row);
                rowFeedGoing      = v.findViewById(R.id.row_feed_going);
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

            h.tvPosterDesig.setText(item.postedByDesignation);
            h.tvTime.setText(item.createdAt != null ? sdf.format(item.createdAt) : "");
            bindPosterRow(item, h);
            h.tvTitle.setText(item.title);
            h.tvDescription.setText(item.description);
            if (h.tvLikeCount != null) h.tvLikeCount.setText(String.valueOf(item.likeCount));
            if (h.tvCommentCount != null) h.tvCommentCount.setText(String.valueOf(item.commentCount));

            // Event-specific
            if ("Event".equals(item.type)) {
                h.layoutEventInfo.setVisibility(View.VISIBLE);
                h.layoutGoingRow.setVisibility(View.VISIBLE);
                h.tvLocation.setText(item.location.isEmpty() ? "—" : item.location);
                h.tvEventDate.setText(item.eventDate);
                long goingCount = item.participantCount;
                String goingText = item.maxParticipants > 0
                        ? goingCount + " / " + item.maxParticipants + " going"
                        : goingCount + " going";
                h.tvGoingCount.setText(goingText);
                if (h.tvFeedGoingCount != null) h.tvFeedGoingCount.setText(goingText);
                if (h.rowFeedGoing != null) {
                    h.rowFeedGoing.setOnClickListener(v -> {
                        Intent gi = new Intent(getActivity(), EventParticipantsActivity.class);
                        gi.putExtra("eventId", item.docId);
                        gi.putExtra("eventTitle", item.title);
                        startActivity(gi);
                    });
                }

                // Check if full
                boolean isFull = item.maxParticipants > 0
                        && item.participantCount >= item.maxParticipants;
                h.btnGoing.setEnabled(!isFull || item.goingByMe);
                h.btnGoing.setAlpha(isFull && !item.goingByMe ? 0.4f : 1f);
            } else {
                h.layoutEventInfo.setVisibility(View.GONE);
                h.layoutGoingRow.setVisibility(View.GONE);
            }

            bindPostImages(item, h);
            if (h.ivComment != null) LikeIconHelper.setCommentTint(h.ivComment);

            // Like action
            checkLiked(item, h);
            h.btnLike.setOnClickListener(v -> toggleLike(item, h));

            // Comment → open detail
            h.btnComment.setOnClickListener(v -> openDetail(item));

            // Card click → open detail
            h.cardRoot.setOnClickListener(v -> openDetail(item));

            // Poster click → open user profile
            h.ivPosterPhoto.setOnClickListener(v -> openUserProfile(item.postedBy));
            h.tvPosterName.setOnClickListener(v -> openUserProfile(item.postedBy));

            // Going action
            if ("Event".equals(item.type)) {
                checkGoing(item, h);
                h.btnGoing.setOnClickListener(v -> toggleGoing(item, h));
            }
        }

        private void checkLiked(FeedItem item, VH h) {
            String uid = auth.getUid();
            if (uid == null) return;
            String col = "Announcement".equals(item.type) ? "announcements" : "events";
            db.collection(col).document(item.docId)
                    .collection("likes").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        item.likedByMe = doc.exists();
                        updateLikeUI(h, item);
                    });
        }

        private void toggleLike(FeedItem item, VH h) {
            String uid = auth.getUid();
            if (uid == null) return;
            String col = "Announcement".equals(item.type) ? "announcements" : "events";
            DocumentReference likeRef = db.collection(col).document(item.docId)
                    .collection("likes").document(uid);
            DocumentReference postRef = db.collection(col).document(item.docId);

            if (item.likedByMe) {
                likeRef.delete();
                postRef.update("likeCount", FieldValue.increment(-1));
                item.likedByMe = false;
                item.likeCount = Math.max(0, item.likeCount - 1);
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("uid", uid);
                data.put("likedAt", Timestamp.now());
                likeRef.set(data);
                postRef.update("likeCount", FieldValue.increment(1));
                item.likedByMe = true;
                item.likeCount++;

                // Notify poster
                sendNotification(item.postedBy, getUserEmail() + " liked your post",
                        item.title, item.docId, col);
            }
            updateLikeUI(h, item);
        }

        private void updateLikeUI(VH h, FeedItem item) {
            if (h.tvLikeCount != null) h.tvLikeCount.setText(String.valueOf(item.likeCount));
            LikeIconHelper.setHeartTint(h.ivLike, item.likedByMe);
        }

        private void bindPostImages(FeedItem item, VH h) {
            if (h.layoutPostMedia == null || h.vpPostImages == null) return;
            List<String> imgs = item.imagesBase64;
            if (imgs == null || imgs.isEmpty()) {
                h.layoutPostMedia.setVisibility(View.GONE);
                h.boundCarouselDocId = null;
                h.boundCarouselSig = null;
                return;
            }
            h.layoutPostMedia.setVisibility(View.VISIBLE);
            String sig = item.docId + ":" + PostImageList.signature(imgs);
            if (java.util.Objects.equals(item.docId, h.boundCarouselDocId)
                    && java.util.Objects.equals(sig, h.boundCarouselSig)) {
                return;
            }
            h.boundCarouselDocId = item.docId;
            h.boundCarouselSig = sig;
            android.content.Context ctx = requireContext();
            PostImageCarouselBinder.bind(h.vpPostImages, h.dotsPostImages, imgs, ctx,
                    pageIdx -> FullscreenImageGallery.show(ctx, imgs, pageIdx));
        }

        private void checkGoing(FeedItem item, VH h) {
            String uid = auth.getUid();
            if (uid == null) return;
            db.collection("events").document(item.docId)
                    .collection("participants").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        item.goingByMe = doc.exists();
                        updateGoingUI(h, item);
                    });
        }

        private void toggleGoing(FeedItem item, VH h) {
            String uid = auth.getUid();
            if (uid == null) return;

            boolean isFull = item.maxParticipants > 0
                    && item.participantCount >= item.maxParticipants && !item.goingByMe;
            if (isFull) {
                Toast.makeText(getContext(), "Event is full!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show confirmation dialog
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(item.goingByMe ? "Cancel RSVP?" : "Join Event?")
                    .setMessage(item.goingByMe
                            ? "Remove yourself from " + item.title + "?"
                            : "Confirm you are going to " + item.title + "?")
                    .setPositiveButton("Confirm", (d, w) -> {
                        DocumentReference partRef = db.collection("events").document(item.docId)
                                .collection("participants").document(uid);
                        DocumentReference eventRef = db.collection("events").document(item.docId);

                        if (item.goingByMe) {
                            partRef.delete();
                            eventRef.update("participantCount", FieldValue.increment(-1));
                            item.goingByMe = false;
                            item.participantCount = Math.max(0, item.participantCount - 1);

                            // Notify poster that the RSVP was cancelled
                            sendNotification(item.postedBy,
                                    getUserEmail() + " cancelled your event",
                                    item.title, item.docId, "events");
                        } else {
                            // Fetch current user info first
                            db.collection("users").document(uid).get()
                                    .addOnSuccessListener(userDoc -> {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("uid", uid);
                                        data.put("fName",   userDoc.getString("fName"));
                                        data.put("lName",   userDoc.getString("lName"));
                                        data.put("email",   userDoc.getString("email"));
                                        data.put("campus",  userDoc.getString("campus"));
                                        data.put("college", userDoc.getString("college"));
                                        data.put("course",  userDoc.getString("course"));
                                        data.put("joinedAt", Timestamp.now());
                                        partRef.set(data);
                                        eventRef.update("participantCount", FieldValue.increment(1));
                                        item.goingByMe = true;
                                        item.participantCount++;
                                        updateGoingUI(h, item);

                                        // Notify poster
                                        sendNotification(item.postedBy,
                                                getUserEmail() + " joined your event",
                                                item.title, item.docId, "events");
                                    });
                            return;
                        }
                        updateGoingUI(h, item);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void updateGoingUI(VH h, FeedItem item) {
            long goingCount = item.participantCount;
            String goingText = item.maxParticipants > 0
                    ? goingCount + " / " + item.maxParticipants + " going"
                    : goingCount + " going";
            h.tvGoingCount.setText(goingText);
            if (h.tvFeedGoingCount != null) h.tvFeedGoingCount.setText(goingText);
            h.btnGoing.setAlpha(item.goingByMe ? 1f : 0.6f);
        }

        private void bindPosterRow(FeedItem item, VH h) {
            String label = item.postedByName != null && !item.postedByName.isEmpty()
                    ? item.postedByName : "…";
            h.tvPosterName.setText(label);

            if (item.postedByPhoto != null && !item.postedByPhoto.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(item.postedByPhoto, Base64.DEFAULT);
                    h.ivPosterPhoto.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    h.ivPosterPhoto.setVisibility(View.VISIBLE);
                } catch (Exception ignored) {
                    h.ivPosterPhoto.setVisibility(View.GONE);
                }
            } else {
                h.ivPosterPhoto.setImageDrawable(null);
            }

            if (item.postedBy != null && !item.postedBy.isEmpty()) {
                db.collection("users").document(item.postedBy).get()
                        .addOnSuccessListener(doc -> {
                            if (doc == null || !doc.exists() || h.getAdapterPosition() == RecyclerView.NO_POSITION)
                                return;
                            FeedItem cur = filtered.get(h.getAdapterPosition());
                            if (cur == null || !item.docId.equals(cur.docId)) return;

                            if (item.postedByName == null || item.postedByName.isEmpty()) {
                                String f = doc.getString("fName") != null ? doc.getString("fName") : "";
                                String l = doc.getString("lName") != null ? doc.getString("lName") : "";
                                String full = (f + " " + l).trim();
                                if (!full.isEmpty()) h.tvPosterName.setText(full);
                            }
                            String b64 = doc.getString("photoBase64");
                            if (b64 != null && !b64.isEmpty()) {
                                try {
                                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                                    h.ivPosterPhoto.setImageBitmap(
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                                    h.ivPosterPhoto.setVisibility(View.VISIBLE);
                                } catch (Exception ignored) {}
                            }
                        });
            }
        }

        private void openDetail(FeedItem item) {
            Intent i = new Intent(getActivity(), PostDetailActivity.class);
            i.putExtra("docId", item.docId);
            i.putExtra("type", item.type);
            startActivity(i);
        }

        private void openUserProfile(String uid) {
            if (uid == null || uid.isEmpty()) return;
            Intent i = new Intent(getActivity(), UserProfileActivity.class);
            i.putExtra("uid", uid);
            startActivity(i);
        }

        private void sendNotification(String targetUid, String message, String subject,
                                      String refId, String refCollection) {
            if (targetUid == null || targetUid.isEmpty()) return;
            Map<String, Object> notif = new HashMap<>();
            notif.put("targetUid",     targetUid);
            notif.put("message",       message);
            notif.put("subject",       subject);
            notif.put("refId",         refId);
            notif.put("refCollection", refCollection);
            notif.put("timestamp",     Timestamp.now());
            notif.put("read",          false);
            db.collection("notifications").add(notif);
        }

        private String getUserEmail() {
            return auth.getCurrentUser() != null
                    ? auth.getCurrentUser().getEmail() : "Someone";
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
