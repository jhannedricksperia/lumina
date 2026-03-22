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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.example.luminae.admin.fragments.ActivityLogger;
import com.example.luminae.utils.NotificationHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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

        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(type);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new CommentAdapter();
        b.recyclerComments.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerComments.setAdapter(adapter);

        loadPost();
        loadComments();
        b.btnSendComment.setOnClickListener(v -> sendComment());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post detail
    // ─────────────────────────────────────────────────────────────────────────
    private void loadPost() {
        db.collection(postCollection).document(docId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            b.tvPostTitle.setText(doc.getString("title")                   != null ? doc.getString("title")               : "");
            postTitle     = doc.getString("title")    != null ? doc.getString("title")    : "";
            postPosterUid = doc.getString("postedBy") != null ? doc.getString("postedBy") : "";
            b.tvPostDescription.setText(doc.getString("description")       != null ? doc.getString("description")         : "");
            b.tvPostedBy.setText(doc.getString("postedByName")             != null ? doc.getString("postedByName")        : "");
            b.tvPostedByDesig.setText(doc.getString("postedByDesignation") != null ? doc.getString("postedByDesignation") : "");

            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null)
                b.tvPostTime.setText(new SimpleDateFormat("MMM d, yyyy  h:mm a",
                        Locale.getDefault()).format(ts.toDate()));

            String img = doc.getString("imageBase64");
            if (img != null && !img.isEmpty()) {
                b.ivPostImage.setVisibility(View.VISIBLE);
                try {
                    byte[] bytes = Base64.decode(img, Base64.DEFAULT);
                    b.ivPostImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                } catch (Exception ignored) {}
            }

            if ("Event".equals(type)) {
                b.layoutEventExtras.setVisibility(View.VISIBLE);
                b.tvEventLocation.setText(doc.getString("location") != null ? doc.getString("location") : "");
                b.tvEventDate.setText(doc.getString("eventDate")    != null ? doc.getString("eventDate") : "");
                Long max = doc.getLong("maxParticipants");
                Long cnt = doc.getLong("participantCount");
                b.tvParticipantsCount.setText((max != null && max > 0)
                        ? cnt + " / " + max + " going" : cnt + " going");
            } else {
                b.layoutEventExtras.setVisibility(View.GONE);
            }

            // Load poster profile photo
            String postedByPhoto = doc.getString("postedByPhoto");
            if (postedByPhoto != null && !postedByPhoto.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(postedByPhoto, Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null && b.ivPosterPhoto != null) b.ivPosterPhoto.setImageBitmap(bmp);
                } catch (Exception ignored) {}
            } else {
                // Fallback: fetch from users collection
                String posterUid2 = doc.getString("postedBy");
                if (posterUid2 != null && !posterUid2.isEmpty() && b.ivPosterPhoto != null) {
                    db.collection("users").document(posterUid2).get().addOnSuccessListener(userDoc -> {
                        String b64 = userDoc.getString("photoBase64");
                        if (b64 != null && !b64.isEmpty()) {
                            try {
                                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (bmp != null && b.ivPosterPhoto != null) b.ivPosterPhoto.setImageBitmap(bmp);
                            } catch (Exception ignored2) {}
                        }
                    });
                }
            }

            String posterUid = doc.getString("postedBy");
            if (posterUid != null && !posterUid.isEmpty())
                b.tvPostedBy.setOnClickListener(v -> openUserProfile(posterUid));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top-level comments
    // ─────────────────────────────────────────────────────────────────────────
    private void loadComments() {
        db.collection(postCollection).document(docId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    comments = snap.getDocuments();
                    b.tvCommentCount.setText(comments.size() + " comment(s)");
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
                        // Audit log
                        ActivityLogger.log(
                                "announcements".equals(postCollection)
                                        ? ActivityLogger.MODULE_ANNOUNCEMENT
                                        : ActivityLogger.MODULE_EVENT,
                                "Commented", postTitle);
                        // Notify poster
                        String commenterName = (fName + " " + lName).trim();
                        NotificationHelper.notifyComment(
                                postPosterUid, commenterName, postTitle, docId, postCollection);
                    })
                    .addOnFailureListener(ex -> {
                        b.btnSendComment.setEnabled(true);
                        Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void openUserProfile(String uid) {
        Intent i = new Intent(this, UserProfileActivity.class);
        i.putExtra("uid", uid);
        startActivity(i);
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
            String uid           = auth.getUid();
            String name          = doc.getString("name") != null ? doc.getString("name") : "Unknown";
            String text          = doc.getString("text") != null ? doc.getString("text") : "";
            String authorUid     = doc.getString("uid")  != null ? doc.getString("uid")  : "";
            Timestamp ts         = doc.getTimestamp("createdAt");
            Long likes           = doc.getLong("likeCount");
            int likeCount        = likes != null ? likes.intValue() : 0;

            h.tvInitials.setText(initials(name));
            h.tvName.setText(name);
            h.tvText.setText(text);
            h.tvTime.setText(ts != null
                    ? new SimpleDateFormat("MMM d  h:mm a", Locale.getDefault()).format(ts.toDate()) : "");
            h.tvLikeCount.setText(String.valueOf(likeCount));
            h.tvName.setOnClickListener(v -> openUserProfile(authorUid));

            // Load commenter profile photo
            loadCommenterPhoto(h, authorUid);

            DocumentReference commentRef = db.collection(postCollection).document(docId)
                    .collection("comments").document(doc.getId());

            // ── Like ──────────────────────────────────────────────────────
            if (uid != null) {
                commentRef.collection("likes").document(uid).get()
                        .addOnSuccessListener(d -> updateCommentLikeUI(h, likeCount, d.exists()));
            }
            if (h.btnLikeComment != null)
                h.btnLikeComment.setOnClickListener(v -> toggleCommentLike(h, uid, commentRef, doc));

            // Tap the like COUNT to see who liked
            if (h.tvLikeCount != null) {
                h.tvLikeCount.setOnClickListener(v -> showLikers(commentRef.collection("likes")));
            }

            // ── Reply toggle (direct reply to comment) ────────────────────
            if (h.btnReply != null) {
                h.btnReply.setOnClickListener(v -> {
                    h.layoutReplyInput.setTag(null);           // parentReplyId = null → top-level reply
                    if (h.etReply != null) h.etReply.setHint("Reply to " + name + "…");
                    toggleReplyInput(h);
                });
            }

            // ── Send reply ────────────────────────────────────────────────
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

            // ── Load full reply thread ────────────────────────────────────
            loadReplyThread(h, commentRef);

            // ── Delete comment ────────────────────────────────────────────
            boolean isOwn = uid != null && uid.equals(authorUid);
            if (h.btnDelete != null) {
                h.btnDelete.setVisibility(isOwn ? View.VISIBLE : View.GONE);
                h.btnDelete.setOnClickListener(v ->
                        new androidx.appcompat.app.AlertDialog.Builder(PostDetailActivity.this)
                                .setTitle("Delete comment?")
                                .setMessage("This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) -> {
                                    commentRef.delete().addOnSuccessListener(unused -> {
                                        db.collection(postCollection).document(docId)
                                                .update("commentCount", FieldValue.increment(-1));
                                        ActivityLogger.log(
                                                "announcements".equals(postCollection)
                                                        ? ActivityLogger.MODULE_ANNOUNCEMENT
                                                        : ActivityLogger.MODULE_EVENT,
                                                ActivityLogger.ACTION_DELETE,
                                                "Comment on: " + postTitle);
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show());
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // Like helpers
        // ─────────────────────────────────────────────────────────────────
        private void toggleCommentLike(VH h, String uid, DocumentReference commentRef,
                                       DocumentSnapshot doc) {
            if (uid == null) return;
            commentRef.collection("likes").document(uid).get().addOnSuccessListener(likeDoc -> {
                Long cur = doc.getLong("likeCount");
                if (likeDoc.exists()) {
                    likeDoc.getReference().delete();
                    commentRef.update("likeCount", FieldValue.increment(-1));
                    updateCommentLikeUI(h, Math.max(0, (cur != null ? cur.intValue() : 1) - 1), false);
                } else {
                    Map<String, Object> d = new HashMap<>();
                    d.put("uid", uid);
                    d.put("likedAt", Timestamp.now());
                    commentRef.collection("likes").document(uid).set(d);
                    commentRef.update("likeCount", FieldValue.increment(1));
                    updateCommentLikeUI(h, (cur != null ? cur.intValue() : 0) + 1, true);
                    // Audit log
                    ActivityLogger.log(
                            "announcements".equals(postCollection)
                                    ? ActivityLogger.MODULE_ANNOUNCEMENT
                                    : ActivityLogger.MODULE_EVENT,
                            "Liked Comment", postTitle);
                    // Notify comment author
                    String commentAuthorUid = likeDoc.getReference().getParent().getParent() != null
                            ? likeDoc.getReference().getParent().getParent().getId() : "";
                    // Fetch current user name for notification
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    db.collection("users").document(uid).get().addOnSuccessListener(uDoc -> {
                        String fn = uDoc.getString("fName") != null ? uDoc.getString("fName") : "";
                        String ln = uDoc.getString("lName") != null ? uDoc.getString("lName") : "";
                        String liker = (fn + " " + ln).trim();
                        // notify post poster (comment like → tell poster someone liked a comment)
                        NotificationHelper.notifyLike(postPosterUid, liker,
                                postTitle, docId, postCollection);
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

        // ─────────────────────────────────────────────────────────────────
        // Reply input toggle
        // ─────────────────────────────────────────────────────────────────
        private void toggleReplyInput(VH h) {
            boolean showing = h.layoutReplyInput.getVisibility() == View.VISIBLE;
            h.layoutReplyInput.setVisibility(showing ? View.GONE : View.VISIBLE);
            if (!showing && h.etReply != null) h.etReply.requestFocus();
        }

        // ─────────────────────────────────────────────────────────────────
        // Send reply  (parentReplyId == null → direct reply to comment)
        // ─────────────────────────────────────────────────────────────────
        private void sendReply(VH h, String uid, DocumentReference commentRef,
                               String replyText, String parentReplyId) {
            h.btnSendReply.setEnabled(false);
            db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                String fName = userDoc.getString("fName") != null ? userDoc.getString("fName") : "";
                String lName = userDoc.getString("lName") != null ? userDoc.getString("lName") : "";
                Map<String, Object> reply = new HashMap<>();
                reply.put("uid",           uid);
                reply.put("name",          (fName + " " + lName).trim());
                reply.put("text",          replyText);
                reply.put("createdAt",     Timestamp.now());
                // "" means direct child of comment; otherwise nested under another reply
                reply.put("parentReplyId", parentReplyId != null ? parentReplyId : "");

                commentRef.collection("replies").add(reply)
                        .addOnSuccessListener(ref -> {
                            h.etReply.setText("");
                            h.layoutReplyInput.setVisibility(View.GONE);
                            h.layoutReplyInput.setTag(null);
                            h.btnSendReply.setEnabled(true);
                            loadReplyThread(h, commentRef);   // refresh thread
                            // Audit log
                            ActivityLogger.log(
                                    "announcements".equals(postCollection)
                                            ? ActivityLogger.MODULE_ANNOUNCEMENT
                                            : ActivityLogger.MODULE_EVENT,
                                    "Replied", postTitle);
                            // Notify post poster about new reply
                            String rName = (fName + " " + lName).trim();
                            NotificationHelper.notifyReply(
                                    postPosterUid, rName, postTitle, docId, postCollection);
                        })
                        .addOnFailureListener(ex -> {
                            h.btnSendReply.setEnabled(true);
                            Toast.makeText(PostDetailActivity.this,
                                    "Failed to send reply", Toast.LENGTH_SHORT).show();
                        });
            });
        }

        // ─────────────────────────────────────────────────────────────────
        // Threaded reply rendering
        // ─────────────────────────────────────────────────────────────────

        /**
         * Fetches ALL replies for a comment in one query, builds a
         * parentId → children map, then calls renderReplyChildren()
         * recursively so every reply can itself be replied to infinitely.
         *
         * Firestore structure:
         *   comments/{commentId}/replies/{replyId}
         *     uid, name, text, createdAt, parentReplyId
         *
         * parentReplyId == ""  →  direct reply to the comment
         * parentReplyId == X   →  reply to reply with id X
         */
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

                        // Build parent → children map
                        Map<String, List<DocumentSnapshot>> childMap = new LinkedHashMap<>();
                        for (DocumentSnapshot r : snap.getDocuments()) {
                            String pid = r.getString("parentReplyId");
                            if (pid == null) pid = "";
                            childMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
                        }

                        // Render starting from root (direct children of comment)
                        renderReplyChildren("", childMap, h.layoutReplies, h, commentRef, 0);
                    });
        }

        /**
         * Recursively builds reply rows, indenting each level by 16 dp.
         *
         * @param parentId   ID of the parent ("" = root)
         * @param childMap   full parent→children map
         * @param container  LinearLayout to add rows into
         * @param h          ViewHolder (owns the shared reply input)
         * @param commentRef Firestore ref for sending new replies
         * @param depth      nesting depth (0 = direct reply to comment)
         */
        private void renderReplyChildren(String parentId,
                                         Map<String, List<DocumentSnapshot>> childMap,
                                         LinearLayout container,
                                         VH h,
                                         DocumentReference commentRef,
                                         int depth) {
            List<DocumentSnapshot> children = childMap.get(parentId);
            if (children == null) return;

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d  h:mm a", Locale.getDefault());

            String currentUid = auth.getUid();

            for (DocumentSnapshot r : children) {
                String rId    = r.getId();
                String rName  = r.getString("name") != null ? r.getString("name") : "Unknown";
                String rText  = r.getString("text") != null ? r.getString("text") : "";
                String rUid   = r.getString("uid")  != null ? r.getString("uid")  : "";
                Timestamp rTs = r.getTimestamp("createdAt");
                Long rLikes   = r.getLong("likeCount");
                int rLikeCount = rLikes != null ? rLikes.intValue() : 0;

                DocumentReference replyRef = commentRef.collection("replies").document(rId);
                boolean isMyReply = currentUid != null && currentUid.equals(rUid);

                // ── Row container (indented) ──────────────────────────────
                LinearLayout row = new LinearLayout(PostDetailActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(8 + depth * 16), dp(6), 0, 0);

                // ── Meta: arrows + name + time ────────────────────────────
                StringBuilder arrowSb = new StringBuilder();
                for (int i = 0; i <= depth; i++) arrowSb.append("↳ ");
                TextView tvMeta = new TextView(PostDetailActivity.this);
                tvMeta.setText(arrowSb.toString().trim() + " " + rName
                        + "  •  " + (rTs != null ? sdf.format(rTs.toDate()) : ""));
                tvMeta.setTextColor(0x99FFFFFF);
                tvMeta.setTextSize(10f);
                tvMeta.setTypeface(null, Typeface.BOLD);
                tvMeta.setOnClickListener(v -> { if (!rUid.isEmpty()) openUserProfile(rUid); });

                // ── Reply text ────────────────────────────────────────────
                TextView tvBody = new TextView(PostDetailActivity.this);
                tvBody.setText(rText);
                tvBody.setTextColor(0xCCFFFFFF);
                tvBody.setTextSize(12f);
                tvBody.setPadding(0, dp(2), 0, 0);

                // ── Action row: ♥ count  ↩ Reply  [Delete] ───────────────
                LinearLayout actionRow = new LinearLayout(PostDetailActivity.this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                actionRow.setPadding(0, dp(4), 0, 0);

                // Heart + count
                TextView tvReplyHeart = new TextView(PostDetailActivity.this);
                tvReplyHeart.setTextSize(12f);
                tvReplyHeart.setPadding(0, 0, dp(4), 0);

                TextView tvReplyLikeCount = new TextView(PostDetailActivity.this);
                tvReplyLikeCount.setTextSize(11f);
                tvReplyLikeCount.setPadding(0, 0, dp(14), 0);
                tvReplyLikeCount.setText(String.valueOf(rLikeCount));
                tvReplyLikeCount.setClickable(true);
                tvReplyLikeCount.setFocusable(true);

                // Check if current user already liked this reply
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

                // Toggle like on tap
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

                // ↩ Reply
                TextView tvReplyAction = new TextView(PostDetailActivity.this);
                tvReplyAction.setText("↩ Reply");
                tvReplyAction.setTextColor(0x88FFFFFF);
                tvReplyAction.setTextSize(11f);
                tvReplyAction.setPadding(0, 0, dp(14), 0);
                tvReplyAction.setClickable(true);
                tvReplyAction.setFocusable(true);
                tvReplyAction.setOnClickListener(v -> {
                    h.layoutReplyInput.setTag(rId);
                    if (h.etReply != null) h.etReply.setHint("Replying to " + rName + "…");
                    h.layoutReplyInput.setVisibility(View.VISIBLE);
                    if (h.etReply != null) h.etReply.requestFocus();
                });

                // Delete (own replies only)
                TextView tvReplyDelete = new TextView(PostDetailActivity.this);
                tvReplyDelete.setText("Delete");
                tvReplyDelete.setTextColor(0x66FF6B6B);
                tvReplyDelete.setTextSize(11f);
                tvReplyDelete.setVisibility(isMyReply ? View.VISIBLE : View.GONE);
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

                // ── Recurse ───────────────────────────────────────────────
                renderReplyChildren(rId, childMap, container, h, commentRef, depth + 1);
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // Utilities
        // ─────────────────────────────────────────────────────────────────
        private void loadCommenterPhoto(VH h, String uid) {
            if (uid == null || uid.isEmpty() || h.ivCommenterPhoto == null) return;
            db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
                String b64 = userDoc.getString("photoBase64");
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            h.ivCommenterPhoto.setImageBitmap(bmp);
                            h.ivCommenterPhoto.setVisibility(android.view.View.VISIBLE);
                            // Hide the initials text once photo is loaded
                            if (h.tvInitials != null)
                                h.tvInitials.setVisibility(android.view.View.INVISIBLE);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }

        /**
         * Shows a BottomSheetDialog listing everyone who liked a comment or reply.
         * @param likesRef  the "likes" subcollection reference
         */
        private void showLikers(CollectionReference likesRef) {
            likesRef.get().addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    Toast.makeText(PostDetailActivity.this, "No likes yet", Toast.LENGTH_SHORT).show();
                    return;
                }

                BottomSheetDialog sheet = new BottomSheetDialog(PostDetailActivity.this);
                LinearLayout layout = new LinearLayout(PostDetailActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dp(16), dp(16), dp(16), dp(32));

                TextView title = new TextView(PostDetailActivity.this);
                title.setText("❤️ Liked by");
                title.setTextColor(0xFFFFFFFF);
                title.setTextSize(14f);
                title.setTypeface(null, Typeface.BOLD);
                title.setPadding(0, 0, 0, dp(12));
                layout.addView(title);

                // For each like doc, fetch user name
                List<DocumentSnapshot> likeDocs = snap.getDocuments();
                final int[] loaded = {0};
                for (DocumentSnapshot likeDoc : likeDocs) {
                    String likerUid = likeDoc.getString("uid");
                    if (likerUid == null) { loaded[0]++; continue; }
                    db.collection("users").document(likerUid).get()
                            .addOnSuccessListener(userDoc -> {
                                String fn = userDoc.getString("fName") != null ? userDoc.getString("fName") : "";
                                String ln = userDoc.getString("lName") != null ? userDoc.getString("lName") : "";
                                String displayName = (fn + " " + ln).trim();
                                if (displayName.isEmpty()) displayName = likerUid;

                                // Avatar + name row
                                LinearLayout row = new LinearLayout(PostDetailActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                row.setPadding(0, dp(6), 0, dp(6));

                                TextView avatar = new TextView(PostDetailActivity.this);
                                avatar.setWidth(dp(30));
                                avatar.setHeight(dp(30));
                                String photoB64 = userDoc.getString("photoBase64");
                                if (photoB64 == null || photoB64.isEmpty()) {
                                    // Initials circle
                                    avatar.setBackground(getDrawable(R.drawable.circle_maroon));
                                    avatar.setGravity(android.view.Gravity.CENTER);
                                    avatar.setTextColor(0xFFFFFFFF);
                                    avatar.setTextSize(11f);
                                    avatar.setText(displayName.length() > 0
                                            ? String.valueOf(displayName.charAt(0)).toUpperCase() : "?");
                                    row.addView(avatar);
                                } else {
                                    ImageView iv = new ImageView(PostDetailActivity.this);
                                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
                                    iv.setLayoutParams(lp);
                                    iv.setBackground(getDrawable(R.drawable.circle_maroon));
                                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    iv.setClipToOutline(true);
                                    try {
                                        byte[] bytes = android.util.Base64.decode(photoB64, android.util.Base64.DEFAULT);
                                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                                                .decodeByteArray(bytes, 0, bytes.length);
                                        if (bmp != null) iv.setImageBitmap(bmp);
                                    } catch (Exception ignored) {}
                                    row.addView(iv);
                                }

                                TextView tvName = new TextView(PostDetailActivity.this);
                                tvName.setText(displayName);
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
                                    // All names loaded — now show the sheet
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
            return name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        }

        private int dp(int value) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                    getResources().getDisplayMetrics()));
        }

        @Override public int getItemCount() { return comments.size(); }
    }
}