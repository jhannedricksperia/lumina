package com.example.luminae.staff.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.viewpager2.widget.ViewPager2;
import com.example.luminae.R;
import com.example.luminae.activities.AnnouncementFormActivity;
import com.example.luminae.activities.EventFormActivity;
import com.example.luminae.activities.EventParticipantsActivity;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.utils.EventDisplayUtils;
import com.example.luminae.activities.UserProfileActivity;
import com.example.luminae.admin.fragments.ActivityLogger;
import com.example.luminae.utils.FullscreenImageGallery;
import com.example.luminae.utils.LikeIconHelper;
import com.example.luminae.utils.NotificationHelper;
import com.example.luminae.utils.PostImageCarouselBinder;
import com.example.luminae.utils.PostImageList;
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
        setupSearch();
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

    private void setupSearch() {
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });
        b.btnSearch.setOnClickListener(v -> {
            hideKeyboard();
            applyFilter();
        });
        b.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                applyFilter();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        if (getContext() == null || b == null) return;
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.etSearch.getWindowToken(), 0);
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
                        f.location         = EventDisplayUtils.formatLocation(doc);
                        f.eventDate        = EventDisplayUtils.formatEventDate(doc);
                        Long max = doc.getLong("maxParticipants");
                        f.maxParticipants  = max != null ? max : 0;
                        f.participantCount = EventDisplayUtils.countGoing(doc);
                        allItems.add(f);
                    }
                    if (++done[0] >= 2) { b.progressFeed.setVisibility(View.GONE); applyFilter(); }
                });
    }

    private static String coalesce(String... parts) {
        if (parts == null) return "";
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) return p.trim();
        }
        return "";
    }

    private FeedItem docToItem(DocumentSnapshot doc, String type) {
        FeedItem f     = new FeedItem();
        f.docId        = doc.getId();
        f.type         = type;
        f.title        = doc.getString("title")        != null ? doc.getString("title")        : "";
        f.description  = doc.getString("description")  != null ? doc.getString("description")  : "";
        f.postedBy     = coalesce(doc.getString("postedBy"), doc.getString("createdById"));
        f.postedByName = coalesce(doc.getString("postedByName"), doc.getString("createdByName"),
                doc.getString("createdBy"));
        f.audienceLabel= doc.getString("audienceLabel")!= null ? doc.getString("audienceLabel"): "Everyone";
        f.postedByPhoto = doc.getString("postedByPhoto");
        f.imagesBase64 = new ArrayList<>(PostImageList.fromDocument(doc));
        f.imageBase64  = f.imagesBase64.isEmpty() ? null : f.imagesBase64.get(0);
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
        String q = b.etSearch.getText() != null
                ? b.etSearch.getText().toString().trim().toLowerCase() : "";
        for (FeedItem item : allItems) {
            if (!(currentType.equals("All") || currentType.equals(item.type))) continue;
            boolean matchSearch = q.isEmpty()
                    || item.title.toLowerCase().contains(q)
                    || item.description.toLowerCase().contains(q);
            if (matchSearch) filtered.add(item);
        }
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
        ArrayList<String> imagesBase64 = new ArrayList<>();
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
            TextView  tvLocation, tvEventDate, tvFeedGoingCount;
            ImageView ivLike, ivComment;
            ImageView ivPosterPhoto, btnMore;
            View      layoutPostMedia;
            ViewPager2 vpPostImages;
            LinearLayout dotsPostImages;
            View      cardRoot, btnLike, btnComment, layoutEventInfo, rowFeedGoing;
            String    boundCarouselDocId;
            String    boundCarouselSig;

            VH(View v) {
                super(v);
                cardRoot       = v.findViewById(R.id.card_root);
                tvTypeBadge    = v.findViewById(R.id.tv_type_badge);
                ivPosterPhoto  = v.findViewById(R.id.iv_poster_photo);
                layoutPostMedia = v.findViewById(R.id.layout_post_media);
                vpPostImages   = v.findViewById(R.id.vp_post_images);
                dotsPostImages = v.findViewById(R.id.dots_post_images);
                btnMore        = v.findViewById(R.id.btn_more);
                tvPosterName   = v.findViewById(R.id.tv_poster_name);
                tvTime         = v.findViewById(R.id.tv_time);
                tvTitle        = v.findViewById(R.id.tv_title);
                tvDescription  = v.findViewById(R.id.tv_description);
                tvLikeCount    = v.findViewById(R.id.tv_like_count);
                ivLike         = v.findViewById(R.id.iv_like);
                ivComment      = v.findViewById(R.id.iv_comment);
                btnLike        = v.findViewById(R.id.btn_like);
                tvCommentCount = v.findViewById(R.id.tv_comment_count);
                btnComment     = v.findViewById(R.id.btn_comment);
                tvGoingInfo    = v.findViewById(R.id.tv_going_info);
                tvMyPostBadge  = v.findViewById(R.id.tv_my_post_badge);
                tvAudience     = v.findViewById(R.id.tv_audience);
                layoutEventInfo = v.findViewById(R.id.layout_event_info);
                tvLocation     = v.findViewById(R.id.tv_location);
                tvEventDate    = v.findViewById(R.id.tv_event_date);
                rowFeedGoing   = v.findViewById(R.id.row_feed_going);
                tvFeedGoingCount = v.findViewById(R.id.tv_feed_going_count);
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
            bindPostImages(item, h);
            if (h.ivComment != null) LikeIconHelper.setCommentTint(h.ivComment);

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
            if (h.tvCommentCount != null) h.tvCommentCount.setText(String.valueOf(item.commentCount));

            // Event details + going row (icons in layout_event_info)
            if ("Event".equals(item.type)) {
                if (h.layoutEventInfo != null) h.layoutEventInfo.setVisibility(View.VISIBLE);
                if (h.tvLocation != null)
                    h.tvLocation.setText(item.location.isEmpty() ? "—" : item.location);
                if (h.tvEventDate != null) h.tvEventDate.setText(item.eventDate);
                String going = item.maxParticipants > 0
                        ? item.participantCount + " / " + item.maxParticipants + " going"
                        : item.participantCount + " going";
                if (h.tvFeedGoingCount != null) h.tvFeedGoingCount.setText(going);
                if (h.rowFeedGoing != null) {
                    h.rowFeedGoing.setOnClickListener(v -> {
                        Intent gi = new Intent(getActivity(), EventParticipantsActivity.class);
                        gi.putExtra("eventId", item.docId);
                        gi.putExtra("eventTitle", item.title);
                        startActivity(gi);
                    });
                }
                if (h.tvGoingInfo != null) h.tvGoingInfo.setVisibility(View.GONE);
            } else {
                if (h.layoutEventInfo != null) h.layoutEventInfo.setVisibility(View.GONE);
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

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}