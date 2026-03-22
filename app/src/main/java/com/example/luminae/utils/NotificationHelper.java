package com.example.luminae.utils;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central helper for:
 *  1. In-app notifications  → Firestore "notifications" collection
 *  2. FCM push notifications → Firestore "fcm_triggers" collection
 *     (a Cloud Function or your backend watches this collection and sends FCM)
 *
 * Audience targeting:
 *   audienceType = "All"     → notify all users
 *   audienceType = "Campus"  → notify users where campus == audienceCampusId
 *   audienceType = "College" → notify users where college == audienceCollegeId
 *   audienceType = "Course"  → notify users where course == audienceCourseId
 */
public class NotificationHelper {

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // In-app notification (single target user)
    // ─────────────────────────────────────────────────────────────────────────
    public static void sendInApp(String targetUid, String message, String subject,
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
        db().collection("notifications").add(notif);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push + in-app notification for a NEW POST to all users in audience scope
    //
    // postDoc fields expected: title, audienceType, audienceCampusId,
    //                          audienceCollegeId, audienceCourseId, postedBy
    // ─────────────────────────────────────────────────────────────────────────
    public static void notifyNewPost(String postId, String postCollection,
                                     String title, String postedByName,
                                     String audienceType, String audienceCampusId,
                                     String audienceCollegeId, String audienceCourseId,
                                     String posterUid) {

        String shortTitle = title != null && title.length() > 40
                ? title.substring(0, 40) + "…" : title;
        String pushTitle   = postCollection.equals("announcements") ? "📢 New Announcement" : "🎉 New Event";
        String pushBody    = postedByName + ": " + shortTitle;

        // 1. Write FCM trigger doc — your Cloud Function picks this up and sends FCM
        Map<String, Object> trigger = new HashMap<>();
        trigger.put("title",            pushTitle);
        trigger.put("body",             pushBody);
        trigger.put("refId",            postId);
        trigger.put("refCollection",    postCollection);
        trigger.put("audienceType",     audienceType  != null ? audienceType  : "All");
        trigger.put("audienceCampusId", audienceCampusId  != null ? audienceCampusId  : "");
        trigger.put("audienceCollegeId",audienceCollegeId != null ? audienceCollegeId : "");
        trigger.put("audienceCourseId", audienceCourseId  != null ? audienceCourseId  : "");
        trigger.put("createdAt",        Timestamp.now());
        trigger.put("processed",        false);
        db().collection("fcm_triggers").add(trigger);

        // 2. Write in-app notifications for each matching user
        buildAudienceQuery(audienceType, audienceCampusId, audienceCollegeId, audienceCourseId)
                .get().addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot userDoc : snap) {
                        String uid = userDoc.getId();
                        if (uid.equals(posterUid)) continue; // don't notify yourself
                        sendInApp(uid,
                                pushTitle + ": " + shortTitle,
                                title,
                                postId,
                                postCollection);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push + in-app notification for a LIKE on a post
    // ─────────────────────────────────────────────────────────────────────────
    public static void notifyLike(String posterUid, String likerName,
                                  String postTitle, String postId, String postCollection) {
        if (posterUid == null || posterUid.isEmpty()) return;
        String msg = "❤️ " + likerName + " liked your post: " + postTitle;
        sendInApp(posterUid, msg, postTitle, postId, postCollection);
        sendPushToUser(posterUid, "❤️ New Like", likerName + " liked your post", postId, postCollection);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push + in-app notification for a COMMENT on a post
    // ─────────────────────────────────────────────────────────────────────────
    public static void notifyComment(String posterUid, String commenterName,
                                     String postTitle, String postId, String postCollection) {
        if (posterUid == null || posterUid.isEmpty()) return;
        String msg = "💬 " + commenterName + " commented on your post: " + postTitle;
        sendInApp(posterUid, msg, postTitle, postId, postCollection);
        sendPushToUser(posterUid, "💬 New Comment", commenterName + " commented on your post", postId, postCollection);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push + in-app notification for a REPLY on a comment
    // ─────────────────────────────────────────────────────────────────────────
    public static void notifyReply(String commentAuthorUid, String replierName,
                                   String postTitle, String postId, String postCollection) {
        if (commentAuthorUid == null || commentAuthorUid.isEmpty()) return;
        String msg = "↩️ " + replierName + " replied to your comment on: " + postTitle;
        sendInApp(commentAuthorUid, msg, postTitle, postId, postCollection);
        sendPushToUser(commentAuthorUid, "↩️ New Reply", replierName + " replied to your comment", postId, postCollection);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single-user FCM trigger (for like / comment / reply)
    // ─────────────────────────────────────────────────────────────────────────
    private static void sendPushToUser(String targetUid, String title, String body,
                                       String refId, String refCollection) {
        Map<String, Object> trigger = new HashMap<>();
        trigger.put("targetUid",     targetUid);
        trigger.put("title",         title);
        trigger.put("body",          body);
        trigger.put("refId",         refId);
        trigger.put("refCollection", refCollection);
        trigger.put("createdAt",     Timestamp.now());
        trigger.put("processed",     false);
        db().collection("fcm_triggers").add(trigger);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build a Firestore query scoped to the audience
    // ─────────────────────────────────────────────────────────────────────────
    private static Query buildAudienceQuery(String audienceType,
                                            String campusId,
                                            String collegeId,
                                            String courseId) {
        Query q = db().collection("users").whereEqualTo("role", "student");
        if ("Campus".equals(audienceType) && campusId != null && !campusId.isEmpty())
            q = q.whereEqualTo("campusId", campusId);
        else if ("College".equals(audienceType) && collegeId != null && !collegeId.isEmpty())
            q = q.whereEqualTo("collegeId", collegeId);
        else if ("Course".equals(audienceType) && courseId != null && !courseId.isEmpty())
            q = q.whereEqualTo("courseId", courseId);
        // "All" → no extra filter, returns every student
        return q;
    }
}