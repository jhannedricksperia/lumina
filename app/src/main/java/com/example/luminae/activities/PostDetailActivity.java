package com.example.luminae.activities;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Base64;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityPostDetailBinding;
import com.example.luminae.utils.EventDisplayUtils;
import com.example.luminae.utils.FullscreenImageGallery;
import com.example.luminae.utils.LikeIconHelper;
import com.example.luminae.utils.PostImageCarouselBinder;
import com.example.luminae.utils.PostImageList;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.example.luminae.admin.fragments.ActivityLogger;
import com.example.luminae.utils.NotificationHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.*;

public class PostDetailActivity extends AppCompatActivity {

    private ActivityPostDetailBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private CommentAdapter adapter;
    private List<DocumentSnapshot> comments = new ArrayList<>();
    private String docId, type, postCollection;
    private String postTitle = "", postPosterUid = "";
    private String postAuthorUid = "";
    private boolean isAdmin = false;

    private boolean postLikedByMe = false;
    private long    postLikeCount = 0;

    private long   eventMaxParticipants   = 0;
    private long   eventParticipantCount  = 0;
    private boolean eventGoingByMe        = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        b              = ActivityPostDetailBinding.inflate(getLayoutInflater());
        db             = FirebaseFirestore.getInstance();
        auth           = FirebaseAuth.getInstance();
        setContentView(b.getRoot());

        docId          = getIntent().getStringExtra("docId");
        type           = getIntent().getStringExtra("type");
        postCollection = "Announcement".equals(type) ? "announcements" : "events";

        // Keyboard pushes comment bar up
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (view, insets) -> {
            int imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            int navHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, 0, 0, Math.max(imeHeight, navHeight));
            return insets;
        });

        // Toolbar setup — do NOT call setSupportActionBar when using MaterialToolbar
        // with app:menu in XML; the menu is already inflated by the layout.
        b.toolbar.setTitle(type);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Hide the more button until we confirm the user can manage the post
        MenuItem menuMore = b.toolbar.getMenu().findItem(R.id.action_more);
        if (menuMore != null) menuMore.setVisible(false);

        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_more) {
                showPostOptionsMenu();
                return true;
            }
            return false;
        });

        adapter = new CommentAdapter();
        b.recyclerComments.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerComments.setAdapter(adapter);

        loadPost();
        loadComments();
        b.btnSendComment.setOnClickListener(v -> sendComment());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (docId != null && postCollection != null) loadPost();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post detail
    // ─────────────────────────────────────────────────────────────────────────
    private void loadPost() {
        db.collection(postCollection).document(docId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;

            postTitle     = doc.getString("title")    != null ? doc.getString("title")    : "";
            postPosterUid = doc.getString("postedBy") != null ? doc.getString("postedBy") : "";
            if (postPosterUid.isEmpty())
                postPosterUid = doc.getString("createdById") != null ? doc.getString("createdById") : "";

            postAuthorUid = !postPosterUid.isEmpty() ? postPosterUid
                    : (doc.getString("createdById") != null ? doc.getString("createdById") : "");

            b.tvPostTitle.setText(postTitle);
            b.tvPostDescription.setText(doc.getString("description") != null
                    ? doc.getString("description") : "");

            String posterName = doc.getString("postedByName") != null
                    ? doc.getString("postedByName")
                    : (doc.getString("createdBy") != null ? doc.getString("createdBy") : "");
            b.tvPostedBy.setText(posterName);

            String desig = doc.getString("postedByDesignation");
            b.tvPostedByDesig.setText(desig != null ? desig : "");

            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null)
                b.tvPostTime.setText(new SimpleDateFormat("MMM d, yyyy  h:mm a",
                        Locale.getDefault()).format(ts.toDate()));

            Long lc = doc.getLong("likeCount");
            postLikeCount = lc != null ? lc
                    : (doc.getLong("hearts") != null ? doc.getLong("hearts") : 0);

            // Check if current user already liked this post
            String uid = auth.getUid();
            if (uid != null) {
                db.collection(postCollection).document(docId)
                        .collection("likes").document(uid).get()
                        .addOnSuccessListener(likeDoc -> {
                            postLikedByMe = likeDoc.exists();
                            updatePostLikeUI();
                        });
            }
            updatePostLikeUI();

            b.btnLikePost.setOnClickListener(v -> togglePostLike());

            // Images (rounded carousel; tap opens fullscreen gallery)
            java.util.List<String> imgs = PostImageList.fromDocument(doc);
            if (!imgs.isEmpty()) {
                b.layoutPostMedia.setVisibility(View.VISIBLE);
                PostImageCarouselBinder.bind(
                        b.vpPostImages,
                        b.dotsPostImages,
                        imgs,
                        this,
                        pos -> FullscreenImageGallery.show(PostDetailActivity.this, imgs, pos));
            } else {
                b.layoutPostMedia.setVisibility(View.GONE);
            }

            // Event extras
            if ("Event".equals(type)) {
                b.layoutEventExtras.setVisibility(View.VISIBLE);
                String loc = EventDisplayUtils.formatLocation(doc);
                b.tvEventLocation.setText(loc.isEmpty() ? "—" : loc);
                b.tvEventDate.setText(EventDisplayUtils.formatEventDate(doc));
                Long max = doc.getLong("maxParticipants");
                long cnt = EventDisplayUtils.countGoing(doc);
                eventMaxParticipants  = max != null ? max : 0;
                eventParticipantCount = cnt;
                b.tvParticipantsCount.setText((max != null && max > 0)
                        ? cnt + " / " + max + " going" : cnt + " going");
                b.rowGoingDetail.setOnClickListener(v -> {
                    Intent gi = new Intent(PostDetailActivity.this, EventParticipantsActivity.class);
                    gi.putExtra("eventId", docId);
                    gi.putExtra("eventTitle", postTitle);
                    startActivity(gi);
                });
                b.btnGoingPost.setVisibility(View.VISIBLE);
                String uidGoing = auth.getUid();
                if (uidGoing != null) {
                    db.collection("events").document(docId)
                            .collection("participants").document(uidGoing).get()
                            .addOnSuccessListener(p -> {
                                eventGoingByMe = p.exists();
                                updateGoingButtonUi();
                            });
                }
                updateGoingButtonUi();
                b.btnGoingPost.setOnClickListener(v -> toggleEventGoing());
            } else {
                b.layoutEventExtras.setVisibility(View.GONE);
                if (b.btnGoingPost != null) b.btnGoingPost.setVisibility(View.GONE);
            }

            // Poster photo
            String postedByPhoto = doc.getString("postedByPhoto");
            if (postedByPhoto != null && !postedByPhoto.isEmpty()) {
                tryShowPhoto(postedByPhoto);
            } else if (!postAuthorUid.isEmpty()) {
                db.collection("users").document(postAuthorUid).get()
                        .addOnSuccessListener(userDoc -> {
                            String b64 = userDoc.getString("photoBase64");
                            if (b64 != null && !b64.isEmpty()) tryShowPhoto(b64);
                        });
            }

            if (!postAuthorUid.isEmpty())
                b.tvPostedBy.setOnClickListener(v -> openUserProfile(postAuthorUid));

            // Show 3-dot menu only for admin or post author
            String currentUid = auth.getUid();
            if (currentUid != null) {
                db.collection("users").document(currentUid).get().addOnSuccessListener(userDoc -> {
                    String role = userDoc.getString("role");
                    isAdmin = "admin".equalsIgnoreCase(role);
                    boolean canManage = isAdmin || currentUid.equals(postAuthorUid);
                    MenuItem menuMore2 = b.toolbar.getMenu().findItem(R.id.action_more);
                    if (menuMore2 != null) menuMore2.setVisible(canManage);
                });
            }
        });
    }

    private void tryShowPhoto(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null && b.ivPosterPhoto != null) {
                b.ivPosterPhoto.setVisibility(View.VISIBLE);
                b.ivPosterPhoto.setImageBitmap(bmp);
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-level like
    // ─────────────────────────────────────────────────────────────────────────
    private void togglePostLike() {
        String uid = auth.getUid();
        if (uid == null) return;

        DocumentReference postRef = db.collection(postCollection).document(docId);
        DocumentReference likeRef = postRef.collection("likes").document(uid);

        likeRef.get().addOnSuccessListener(likeDoc -> {
            if (likeDoc.exists()) {
                likeRef.delete();
                postRef.update("likeCount", FieldValue.increment(-1));
                postLikedByMe = false;
                postLikeCount = Math.max(0, postLikeCount - 1);
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("uid",     uid);
                data.put("likedAt", Timestamp.now());
                likeRef.set(data);
                postRef.update("likeCount", FieldValue.increment(1));
                postLikedByMe = true;
                postLikeCount++;
                db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                    String fn = userDoc.getString("fName") != null ? userDoc.getString("fName") : "";
                    String ln = userDoc.getString("lName") != null ? userDoc.getString("lName") : "";
                    NotificationHelper.notifyLike(postPosterUid, (fn + " " + ln).trim(),
                            postTitle, docId, postCollection);
                });
                ActivityLogger.log(
                        "announcements".equals(postCollection)
                                ? ActivityLogger.MODULE_ANNOUNCEMENT
                                : ActivityLogger.MODULE_EVENT,
                        "Liked Post", postTitle);
            }
            updatePostLikeUI();
        });
    }

    private void updatePostLikeUI() {
        if (b.tvLikePostCount != null)
            b.tvLikePostCount.setText(String.valueOf(postLikeCount));
        if (b.ivLikePost != null) {
            LikeIconHelper.setHeartTint(b.ivLikePost, postLikedByMe);
        }
    }

    private void updateGoingButtonUi() {
        if (b.btnGoingPost == null) return;
        boolean full = eventMaxParticipants > 0
                && eventParticipantCount >= eventMaxParticipants && !eventGoingByMe;
        b.btnGoingPost.setText(eventGoingByMe ? "Going ✓" : "Going");
        b.btnGoingPost.setAlpha(full ? 0.45f : 1f);
        b.btnGoingPost.setEnabled(!full || eventGoingByMe);
    }

    private String displayNameFromUserDoc(DocumentSnapshot userDoc) {
        if (userDoc == null) return "Someone";
        String f = userDoc.getString("fName");
        String l = userDoc.getString("lName");
        String full = ((f != null ? f : "") + " " + (l != null ? l : "")).trim();
        if (!full.isEmpty()) return full;
        String username = userDoc.getString("username");
        if (username != null && !username.trim().isEmpty()) return username.trim();
        String email = userDoc.getString("email");
        if (email != null && !email.trim().isEmpty()) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        return "Someone";
    }

    private void toggleEventGoing() {
        String uid = auth.getUid();
        if (uid == null || docId == null) return;
        boolean full = eventMaxParticipants > 0
                && eventParticipantCount >= eventMaxParticipants && !eventGoingByMe;
        if (full) {
            Toast.makeText(this, "Event is full!", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(eventGoingByMe ? "Cancel RSVP?" : "Join Event?")
                .setMessage(eventGoingByMe
                        ? "Remove yourself from this event?"
                        : "Confirm you are going to this event?")
                .setPositiveButton("Confirm", (d, w) -> {
                    DocumentReference partRef = db.collection("events").document(docId)
                            .collection("participants").document(uid);
                    DocumentReference eventRef = db.collection("events").document(docId);
                    if (eventGoingByMe) {
                        partRef.delete();
                        eventRef.update("participantCount", FieldValue.increment(-1));
                        eventGoingByMe = false;
                        eventParticipantCount = Math.max(0, eventParticipantCount - 1);

                        // Notify the event poster that the RSVP was cancelled
                        db.collection("users").document(uid).get().addOnSuccessListener(userDoc ->
                                NotificationHelper.sendInApp(
                                        postPosterUid,
                                        displayNameFromUserDoc(userDoc) + " cancelled your event",
                                        postTitle,
                                        docId,
                                        postCollection
                                )
                        );
                    } else {
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
                                    eventGoingByMe = true;
                                    eventParticipantCount++;
                                    updateGoingButtonUi();

                                    // Notify the event poster that the user joined
                                    NotificationHelper.sendInApp(
                                            postPosterUid,
                                            displayNameFromUserDoc(userDoc) + " joined your event",
                                            postTitle,
                                            docId,
                                            postCollection
                                    );
                                    if (b.tvParticipantsCount != null) {
                                        b.tvParticipantsCount.setText((eventMaxParticipants > 0)
                                                ? eventParticipantCount + " / " + eventMaxParticipants + " going"
                                                : eventParticipantCount + " going");
                                    }
                                });
                        return;
                    }
                    updateGoingButtonUi();
                    if (b.tvParticipantsCount != null) {
                        b.tvParticipantsCount.setText((eventMaxParticipants > 0)
                                ? eventParticipantCount + " / " + eventMaxParticipants + " going"
                                : eventParticipantCount + " going");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post options (3-dot menu)
    // ─────────────────────────────────────────────────────────────────────────
    private void showPostOptionsMenu() {
        String[] items = { "Edit", "Archive", "Delete" };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Post Options")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: openEditPost();      break;
                        case 1: toggleArchivePost(); break;
                        case 2: confirmDeletePost(); break;
                    }
                })
                .show();
    }

    private void openEditPost() {
        Intent i = new Intent(this,
                "announcements".equals(postCollection) ? AnnouncementFormActivity.class : EventFormActivity.class);
        i.putExtra("doc_id", docId);
        i.putExtra("collection", postCollection);
        startActivity(i);
    }

    private void toggleArchivePost() {
        db.collection(postCollection).document(docId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            String currentStatus = doc.getString("status");
            boolean isArchived   = "Archive".equalsIgnoreCase(currentStatus)
                    || "Archived".equalsIgnoreCase(currentStatus);
            String newStatus     = isArchived ? "Active" : "Archive";
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(isArchived ? "Restore Post?" : "Archive Post?")
                    .setMessage(isArchived
                            ? "This will set the post status back to Active."
                            : "This will set the post status to Archive.")
                    .setPositiveButton(isArchived ? "Restore" : "Archive", (d, w) ->
                            db.collection(postCollection).document(docId)
                                    .update("status", newStatus)
                                    .addOnSuccessListener(unused ->
                                            Toast.makeText(this,
                                                    "Status set to " + newStatus,
                                                    Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this,
                                                    "Failed to update status",
                                                    Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void confirmDeletePost() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Post?")
                .setMessage("This will permanently delete the post and cannot be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection(postCollection).document(docId).delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show();
                                    ActivityLogger.log(
                                            "announcements".equals(postCollection)
                                                    ? ActivityLogger.MODULE_ANNOUNCEMENT
                                                    : ActivityLogger.MODULE_EVENT,
                                            ActivityLogger.ACTION_DELETE, postTitle);
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Delete failed",
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comments
    // ─────────────────────────────────────────────────────────────────────────
    private void loadComments() {
        db.collection(postCollection).document(docId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    comments = snap.getDocuments();
                    b.tvCommentCount.setText(comments.size() + " comment(s)");
                    b.tvCommentCountInline.setText(String.valueOf(comments.size()));
                    adapter.notifyDataSetChanged();
                });
    }

    private void sendComment() {
        String uid = auth.getUid();
        if (uid == null) return;
        String text = b.etComment.getText().toString().trim();
        if (text.isEmpty()) return;
        b.btnSendComment.setEnabled(false);
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String fName = userDoc.getString("fName") != null ? userDoc.getString("fName") : "";
            String lName = userDoc.getString("lName") != null ? userDoc.getString("lName") : "";
            Map<String, Object> comment = new HashMap<>();
            comment.put("uid",       uid);
            comment.put("name",      (fName + " " + lName).trim());
            comment.put("text",      text);
            comment.put("likeCount", 0L);
            comment.put("createdAt", Timestamp.now());
            db.collection(postCollection).document(docId)
                    .collection("comments").add(comment)
                    .addOnSuccessListener(ref -> {
                        db.collection(postCollection).document(docId)
                                .update("commentCount", FieldValue.increment(1));
                        b.etComment.setText("");
                        b.btnSendComment.setEnabled(true);
                        ActivityLogger.log(
                                "announcements".equals(postCollection)
                                        ? ActivityLogger.MODULE_ANNOUNCEMENT
                                        : ActivityLogger.MODULE_EVENT,
                                "Commented", postTitle);
                        String commenterName = (fName + " " + lName).trim();
                        NotificationHelper.notifyComment(postPosterUid, commenterName,
                                postTitle, docId, postCollection);
                    })
                    .addOnFailureListener(ex -> {
                        b.btnSendComment.setEnabled(true);
                        Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────
    private void openUserProfile(String uid) {
        Intent i = new Intent(this, UserProfileActivity.class);
        i.putExtra("uid", uid);
        startActivity(i);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fullscreen image
    // ─────────────────────────────────────────────────────────────────────────
    private void openFullscreenImage(android.graphics.Bitmap bitmap) {
        android.app.Dialog dialog = new android.app.Dialog(
                PostDetailActivity.this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(buildFullscreenImageView(dialog, bitmap));
        dialog.show();
    }

    private FrameLayout buildFullscreenImageView(android.app.Dialog dialog,
                                                 android.graphics.Bitmap bitmap) {
        FrameLayout root = new FrameLayout(PostDetailActivity.this);
        root.setBackgroundColor(0xFF000000);
        com.github.chrisbanes.photoview.PhotoView photoView =
                new com.github.chrisbanes.photoview.PhotoView(PostDetailActivity.this);
        photoView.setImageBitmap(bitmap);
        photoView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(photoView);
        TextView btnClose = new TextView(PostDetailActivity.this);
        btnClose.setText("✕");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(22f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setPadding(dp(14), dp(14), dp(14), dp(14));
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        closeLp.gravity    = Gravity.TOP | Gravity.END;
        closeLp.topMargin  = dp(36);
        closeLp.rightMargin = dp(8);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose);
        return root;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comment Adapter
    // ─────────────────────────────────────────────────────────────────────────
    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView     tvInitials, tvName, tvText, tvTime;
            ImageView    ivCommenterPhoto;
            TextView     tvLikeIcon, tvLikeCount, btnReply, btnDelete;
            LinearLayout btnLikeComment, layoutReplies, layoutReplyInput;
            EditText     etReply;
            TextView     btnSendReply;

            VH(View v) {
                super(v);
                tvInitials       = v.findViewById(R.id.tv_initials);
                ivCommenterPhoto = v.findViewById(R.id.iv_commenter_photo);
                tvName           = v.findViewById(R.id.tv_name);
                tvText           = v.findViewById(R.id.tv_comment_text);
                tvTime           = v.findViewById(R.id.tv_time);
                tvLikeIcon       = v.findViewById(R.id.tv_like_icon);
                tvLikeCount      = v.findViewById(R.id.btn_like);
                btnLikeComment   = v.findViewById(R.id.btn_like_comment);
                btnReply         = v.findViewById(R.id.btn_reply);
                btnDelete        = v.findViewById(R.id.btn_delete_comment);
                layoutReplies    = v.findViewById(R.id.layout_replies);
                layoutReplyInput = v.findViewById(R.id.layout_reply_input);
                etReply          = v.findViewById(R.id.et_reply);
                btnSendReply     = v.findViewById(R.id.btn_send_reply);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_comment, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = comments.get(pos);
            String uid       = auth.getUid();
            String name      = doc.getString("name") != null ? doc.getString("name") : "Unknown";
            String text      = doc.getString("text") != null ? doc.getString("text") : "";
            String authorUid = doc.getString("uid")  != null ? doc.getString("uid")  : "";
            Timestamp ts     = doc.getTimestamp("createdAt");
            Long likes       = doc.getLong("likeCount");
            int likeCount    = likes != null ? likes.intValue() : 0;

            h.tvInitials.setText(initials(name));
            h.tvName.setText(name);
            h.tvText.setText(text);
            h.tvTime.setText(ts != null
                    ? new SimpleDateFormat("MMM d  h:mm a",
                    Locale.getDefault()).format(ts.toDate()) : "");
            h.tvLikeCount.setText(String.valueOf(likeCount));
            h.tvName.setOnClickListener(v -> openUserProfile(authorUid));
            loadCommenterPhoto(h, authorUid);

            DocumentReference commentRef = db.collection(postCollection).document(docId)
                    .collection("comments").document(doc.getId());

            if (uid != null) {
                commentRef.collection("likes").document(uid).get()
                        .addOnSuccessListener(d ->
                                updateCommentLikeUI(h, likeCount, d.exists()));
            }
            if (h.btnLikeComment != null)
                h.btnLikeComment.setOnClickListener(v ->
                        toggleCommentLike(h, uid, commentRef, doc));
            if (h.tvLikeCount != null)
                h.tvLikeCount.setOnClickListener(v ->
                        showLikers(commentRef.collection("likes")));
            if (h.btnReply != null) {
                h.btnReply.setOnClickListener(v -> {
                    h.layoutReplyInput.setTag(null);
                    if (h.etReply != null) h.etReply.setHint("Reply to " + name + "…");
                    toggleReplyInput(h);
                });
            }
            if (h.btnSendReply != null) {
                h.btnSendReply.setOnClickListener(v -> {
                    if (uid == null || h.etReply == null) return;
                    String replyText = h.etReply.getText().toString().trim();
                    if (replyText.isEmpty()) return;
                    String parentReplyId = h.layoutReplyInput.getTag() instanceof String
                            ? (String) h.layoutReplyInput.getTag() : null;
                    sendReply(h, uid, commentRef, replyText, parentReplyId);
                });
            }
            loadReplyThread(h, commentRef);

            boolean isOwn = uid != null && uid.equals(authorUid);
            if (h.btnDelete != null) {
                h.btnDelete.setVisibility(isOwn || isAdmin ? View.VISIBLE : View.GONE);
                h.btnDelete.setOnClickListener(v ->
                        new androidx.appcompat.app.AlertDialog.Builder(PostDetailActivity.this)
                                .setTitle("Delete comment?")
                                .setMessage("This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) ->
                                        commentRef.delete().addOnSuccessListener(unused ->
                                                db.collection(postCollection).document(docId)
                                                        .update("commentCount",
                                                                FieldValue.increment(-1))))
                                .setNegativeButton("Cancel", null)
                                .show());
            }
        }

        private void toggleCommentLike(VH h, String uid, DocumentReference commentRef,
                                       DocumentSnapshot doc) {
            if (uid == null) return;
            commentRef.collection("likes").document(uid).get()
                    .addOnSuccessListener(likeDoc -> {
                        Long cur = doc.getLong("likeCount");
                        if (likeDoc.exists()) {
                            likeDoc.getReference().delete();
                            commentRef.update("likeCount", FieldValue.increment(-1));
                            updateCommentLikeUI(h,
                                    Math.max(0, (cur != null ? cur.intValue() : 1) - 1), false);
                        } else {
                            Map<String, Object> d = new HashMap<>();
                            d.put("uid", uid);
                            d.put("likedAt", Timestamp.now());
                            commentRef.collection("likes").document(uid).set(d);
                            commentRef.update("likeCount", FieldValue.increment(1));
                            updateCommentLikeUI(h,
                                    (cur != null ? cur.intValue() : 0) + 1, true);
                            db.collection("users").document(uid).get()
                                    .addOnSuccessListener(uDoc -> {
                                        String fn = uDoc.getString("fName") != null
                                                ? uDoc.getString("fName") : "";
                                        String ln = uDoc.getString("lName") != null
                                                ? uDoc.getString("lName") : "";
                                        NotificationHelper.notifyLike(postPosterUid,
                                                (fn + " " + ln).trim(), postTitle,
                                                docId, postCollection);
                                    });
                        }
                    });
        }

        private void updateCommentLikeUI(VH h, int count, boolean liked) {
            if (h.tvLikeCount != null) h.tvLikeCount.setText(String.valueOf(count));
            int color = liked ? 0xFFEF5350 : 0x88FFFFFF;
            if (h.tvLikeIcon  != null) h.tvLikeIcon.setTextColor(color);
            if (h.tvLikeCount != null) h.tvLikeCount.setTextColor(color);
        }

        private void toggleReplyInput(VH h) {
            boolean showing = h.layoutReplyInput.getVisibility() == View.VISIBLE;
            h.layoutReplyInput.setVisibility(showing ? View.GONE : View.VISIBLE);
            if (!showing && h.etReply != null) h.etReply.requestFocus();
        }

        private void sendReply(VH h, String uid, DocumentReference commentRef,
                               String replyText, String parentReplyId) {
            h.btnSendReply.setEnabled(false);
            db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                String fName = userDoc.getString("fName") != null
                        ? userDoc.getString("fName") : "";
                String lName = userDoc.getString("lName") != null
                        ? userDoc.getString("lName") : "";
                Map<String, Object> reply = new HashMap<>();
                reply.put("uid",           uid);
                reply.put("name",          (fName + " " + lName).trim());
                reply.put("text",          replyText);
                reply.put("createdAt",     Timestamp.now());
                reply.put("parentReplyId", parentReplyId != null ? parentReplyId : "");
                commentRef.collection("replies").add(reply)
                        .addOnSuccessListener(ref -> {
                            h.etReply.setText("");
                            h.layoutReplyInput.setVisibility(View.GONE);
                            h.layoutReplyInput.setTag(null);
                            h.btnSendReply.setEnabled(true);
                            loadReplyThread(h, commentRef);
                            NotificationHelper.notifyReply(postPosterUid,
                                    (fName + " " + lName).trim(), postTitle,
                                    docId, postCollection);
                        })
                        .addOnFailureListener(ex -> {
                            h.btnSendReply.setEnabled(true);
                            Toast.makeText(PostDetailActivity.this,
                                    "Failed to send reply", Toast.LENGTH_SHORT).show();
                        });
            });
        }

        private void loadReplyThread(VH h, DocumentReference commentRef) {
            if (h.layoutReplies == null) return;
            commentRef.collection("replies")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .get().addOnSuccessListener(snap -> {
                        h.layoutReplies.removeAllViews();
                        if (snap.isEmpty()) {
                            h.layoutReplies.setVisibility(View.GONE);
                            return;
                        }
                        h.layoutReplies.setVisibility(View.VISIBLE);
                        Map<String, List<DocumentSnapshot>> childMap = new LinkedHashMap<>();
                        for (DocumentSnapshot r : snap.getDocuments()) {
                            String pid = r.getString("parentReplyId");
                            if (pid == null) pid = "";
                            childMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
                        }
                        renderReplyChildren("", childMap, h.layoutReplies, h, commentRef, 0);
                    });
        }

        private void renderReplyChildren(String parentId,
                                         Map<String, List<DocumentSnapshot>> childMap,
                                         LinearLayout container, VH h,
                                         DocumentReference commentRef, int depth) {
            List<DocumentSnapshot> children = childMap.get(parentId);
            if (children == null) return;
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d  h:mm a", Locale.getDefault());
            String currentUid = auth.getUid();

            for (DocumentSnapshot r : children) {
                String rId     = r.getId();
                String rName   = r.getString("name") != null ? r.getString("name") : "Unknown";
                String rText   = r.getString("text") != null ? r.getString("text") : "";
                String rUid    = r.getString("uid")  != null ? r.getString("uid")  : "";
                Timestamp rTs  = r.getTimestamp("createdAt");
                Long rLikes    = r.getLong("likeCount");
                int rLikeCount = rLikes != null ? rLikes.intValue() : 0;

                DocumentReference replyRef =
                        commentRef.collection("replies").document(rId);
                boolean isMyReply = currentUid != null && currentUid.equals(rUid);

                LinearLayout row = new LinearLayout(PostDetailActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(8 + depth * 16), dp(6), 0, 0);

                StringBuilder arrowSb = new StringBuilder();
                for (int i = 0; i <= depth; i++) arrowSb.append("↳ ");
                TextView tvMeta = new TextView(PostDetailActivity.this);
                tvMeta.setText(arrowSb.toString().trim() + " " + rName
                        + "  •  " + (rTs != null ? sdf.format(rTs.toDate()) : ""));
                tvMeta.setTextColor(0x99FFFFFF);
                tvMeta.setTextSize(10f);
                tvMeta.setTypeface(null, Typeface.BOLD);
                tvMeta.setOnClickListener(v -> {
                    if (!rUid.isEmpty()) openUserProfile(rUid);
                });

                TextView tvBody = new TextView(PostDetailActivity.this);
                tvBody.setText(rText);
                tvBody.setTextColor(0xCCFFFFFF);
                tvBody.setTextSize(12f);
                tvBody.setPadding(0, dp(2), 0, 0);

                LinearLayout actionRow = new LinearLayout(PostDetailActivity.this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setGravity(Gravity.CENTER_VERTICAL);
                actionRow.setPadding(0, dp(4), 0, 0);

                TextView tvReplyHeart     = new TextView(PostDetailActivity.this);
                TextView tvReplyLikeCount = new TextView(PostDetailActivity.this);
                tvReplyHeart.setTextSize(12f);
                tvReplyHeart.setPadding(0, 0, dp(4), 0);
                tvReplyLikeCount.setTextSize(11f);
                tvReplyLikeCount.setPadding(0, 0, dp(14), 0);
                tvReplyLikeCount.setText(String.valueOf(rLikeCount));
                tvReplyLikeCount.setClickable(true);
                tvReplyLikeCount.setFocusable(true);

                final int[] replyLikeCount = {rLikeCount};
                if (currentUid != null) {
                    replyRef.collection("likes").document(currentUid).get()
                            .addOnSuccessListener(likeDoc -> {
                                boolean liked = likeDoc.exists();
                                int color = liked ? 0xFFEF5350 : 0x88FFFFFF;
                                tvReplyHeart.setText("♥");
                                tvReplyHeart.setTextColor(color);
                                tvReplyLikeCount.setTextColor(color);
                            });
                } else {
                    tvReplyHeart.setText("♥");
                    tvReplyHeart.setTextColor(0x88FFFFFF);
                    tvReplyLikeCount.setTextColor(0x88FFFFFF);
                }

                View.OnClickListener likeReplyClick = v -> {
                    if (currentUid == null) return;
                    replyRef.collection("likes").document(currentUid).get()
                            .addOnSuccessListener(likeDoc -> {
                                if (likeDoc.exists()) {
                                    likeDoc.getReference().delete();
                                    replyRef.update("likeCount", FieldValue.increment(-1));
                                    replyLikeCount[0] = Math.max(0, replyLikeCount[0] - 1);
                                    tvReplyHeart.setTextColor(0x88FFFFFF);
                                    tvReplyLikeCount.setTextColor(0x88FFFFFF);
                                } else {
                                    Map<String, Object> ld = new HashMap<>();
                                    ld.put("uid", currentUid);
                                    ld.put("likedAt", Timestamp.now());
                                    replyRef.collection("likes").document(currentUid).set(ld);
                                    replyRef.update("likeCount", FieldValue.increment(1));
                                    replyLikeCount[0]++;
                                    tvReplyHeart.setTextColor(0xFFEF5350);
                                    tvReplyLikeCount.setTextColor(0xFFEF5350);
                                }
                                tvReplyLikeCount.setText(String.valueOf(replyLikeCount[0]));
                            });
                };
                tvReplyHeart.setOnClickListener(likeReplyClick);
                tvReplyLikeCount.setOnClickListener(v ->
                        showLikers(replyRef.collection("likes")));

                TextView tvReplyAction = new TextView(PostDetailActivity.this);
                tvReplyAction.setText("↩ Reply");
                tvReplyAction.setTextColor(0x88FFFFFF);
                tvReplyAction.setTextSize(11f);
                tvReplyAction.setPadding(0, 0, dp(14), 0);
                tvReplyAction.setClickable(true);
                tvReplyAction.setFocusable(true);
                tvReplyAction.setOnClickListener(v -> {
                    h.layoutReplyInput.setTag(rId);
                    if (h.etReply != null)
                        h.etReply.setHint("Replying to " + rName + "…");
                    h.layoutReplyInput.setVisibility(View.VISIBLE);
                    if (h.etReply != null) h.etReply.requestFocus();
                });

                TextView tvReplyDelete = new TextView(PostDetailActivity.this);
                tvReplyDelete.setText("Delete");
                tvReplyDelete.setTextColor(0x66FF6B6B);
                tvReplyDelete.setTextSize(11f);
                tvReplyDelete.setVisibility(isMyReply || isAdmin ? View.VISIBLE : View.GONE);
                tvReplyDelete.setClickable(true);
                tvReplyDelete.setFocusable(true);
                tvReplyDelete.setOnClickListener(v ->
                        new androidx.appcompat.app.AlertDialog.Builder(PostDetailActivity.this)
                                .setTitle("Delete reply?")
                                .setMessage("This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) ->
                                        replyRef.delete().addOnSuccessListener(unused ->
                                                loadReplyThread(h, commentRef)))
                                .setNegativeButton("Cancel", null)
                                .show());

                actionRow.addView(tvReplyHeart);
                actionRow.addView(tvReplyLikeCount);
                actionRow.addView(tvReplyAction);
                actionRow.addView(tvReplyDelete);
                row.addView(tvMeta);
                row.addView(tvBody);
                row.addView(actionRow);
                container.addView(row);
                renderReplyChildren(rId, childMap, container, h, commentRef, depth + 1);
            }
        }

        private void loadCommenterPhoto(VH h, String uid) {
            if (uid == null || uid.isEmpty() || h.ivCommenterPhoto == null) return;
            db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                String b64 = userDoc.getString("photoBase64");
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory
                                        .decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            h.ivCommenterPhoto.setImageBitmap(bmp);
                            h.ivCommenterPhoto.setVisibility(View.VISIBLE);
                            if (h.tvInitials != null)
                                h.tvInitials.setVisibility(View.INVISIBLE);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }

        private void showLikers(CollectionReference likesRef) {
            likesRef.get().addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    Toast.makeText(PostDetailActivity.this,
                            "No likes yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                BottomSheetDialog sheet = new BottomSheetDialog(PostDetailActivity.this);
                LinearLayout layout = new LinearLayout(PostDetailActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dp(16), dp(16), dp(16), dp(32));
                TextView title = new TextView(PostDetailActivity.this);
                title.setText("Liked by");
                title.setTextColor(0xFFFFFFFF);
                title.setTextSize(14f);
                title.setTypeface(null, Typeface.BOLD);
                title.setPadding(0, 0, 0, dp(12));
                layout.addView(title);
                List<DocumentSnapshot> likeDocs = snap.getDocuments();
                final int[] loaded = {0};
                for (DocumentSnapshot likeDoc : likeDocs) {
                    String likerUid = likeDoc.getString("uid");
                    if (likerUid == null) { loaded[0]++; continue; }
                    db.collection("users").document(likerUid).get()
                            .addOnSuccessListener(userDoc -> {
                                String fn = userDoc.getString("fName") != null
                                        ? userDoc.getString("fName") : "";
                                String ln = userDoc.getString("lName") != null
                                        ? userDoc.getString("lName") : "";
                                String displayName = (fn + " " + ln).trim();
                                if (displayName.isEmpty()) displayName = likerUid;
                                LinearLayout row = new LinearLayout(PostDetailActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setGravity(Gravity.CENTER_VERTICAL);
                                row.setPadding(0, dp(6), 0, dp(6));
                                String photoB64 = userDoc.getString("photoBase64");
                                if (photoB64 == null || photoB64.isEmpty()) {
                                    TextView avatar = new TextView(PostDetailActivity.this);
                                    avatar.setWidth(dp(30));
                                    avatar.setHeight(dp(30));
                                    avatar.setBackground(getDrawable(R.drawable.circle_maroon));
                                    avatar.setGravity(Gravity.CENTER);
                                    avatar.setTextColor(0xFFFFFFFF);
                                    avatar.setTextSize(11f);
                                    avatar.setText(displayName.length() > 0
                                            ? String.valueOf(displayName.charAt(0)).toUpperCase()
                                            : "?");
                                    row.addView(avatar);
                                } else {
                                    ImageView iv = new ImageView(PostDetailActivity.this);
                                    iv.setLayoutParams(
                                            new LinearLayout.LayoutParams(dp(30), dp(30)));
                                    iv.setBackground(getDrawable(R.drawable.circle_maroon));
                                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    iv.setClipToOutline(true);
                                    try {
                                        byte[] bytes = android.util.Base64.decode(
                                                photoB64, android.util.Base64.DEFAULT);
                                        android.graphics.Bitmap bmp =
                                                android.graphics.BitmapFactory
                                                        .decodeByteArray(bytes, 0, bytes.length);
                                        if (bmp != null) iv.setImageBitmap(bmp);
                                    } catch (Exception ignored) {}
                                    row.addView(iv);
                                }
                                String finalDisplayName = displayName;
                                TextView tvName = new TextView(PostDetailActivity.this);
                                tvName.setText(finalDisplayName);
                                tvName.setTextColor(0xCCFFFFFF);
                                tvName.setTextSize(13f);
                                tvName.setPadding(dp(10), 0, 0, 0);
                                tvName.setOnClickListener(v -> {
                                    sheet.dismiss();
                                    openUserProfile(likerUid);
                                });
                                row.addView(tvName);
                                layout.addView(row);
                                loaded[0]++;
                                if (loaded[0] == likeDocs.size()) {
                                    ScrollView sv = new ScrollView(PostDetailActivity.this);
                                    sv.addView(layout);
                                    sheet.setContentView(sv);
                                    sheet.show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                loaded[0]++;
                                if (loaded[0] == likeDocs.size()) {
                                    ScrollView sv = new ScrollView(PostDetailActivity.this);
                                    sv.addView(layout);
                                    sheet.setContentView(sv);
                                    sheet.show();
                                }
                            });
                }
            });
        }

        private String initials(String name) {
            String[] parts = name.trim().split(" ");
            if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0)
                return String.valueOf(parts[0].charAt(0)).toUpperCase()
                        + String.valueOf(parts[1].charAt(0)).toUpperCase();
            return name.length() > 0
                    ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        }

        private int dp(int value) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    value, getResources().getDisplayMetrics()));
        }

        @Override public int getItemCount() { return comments.size(); }
    }
}