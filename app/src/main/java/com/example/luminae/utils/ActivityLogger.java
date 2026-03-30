package com.example.luminae.utils;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Logs CRUD actions to Firestore → "activity_logs" collection.
 *
 * Fields saved:
 *   email      — the admin/staff who performed the action
 *   action     — "Create" | "Modified" | "Delete" | "Viewed"
 *   subject    — identifier of the affected record (see rules below)
 *   timestamp  — Firestore Timestamp
 *   module     — "Student" | "Staff" | "Campus" | "College" | "Course"
 *                | "Announcement" | "Event" | "Reports"
 *
 * Subject rules:
 *   Student      → email
 *   Staff        → email
 *   Campus       → name
 *   College      → acronym
 *   Course       → acronym
 *   Announcement → firstTitleWord_ddMMyyyyHHmm_creatorEmail
 *   Event        → firstTitleWord_ddMMyyyyHHmm_creatorEmail
 */
public class ActivityLogger {

    public static final String ACTION_CREATE   = "Create";
    public static final String ACTION_MODIFIED = "Modified";
    public static final String ACTION_DELETE   = "Delete";
    public static final String ACTION_VIEWED   = "Viewed";

    public static final String MODULE_STUDENT      = "Student";
    public static final String MODULE_STAFF        = "Staff";
    public static final String MODULE_CAMPUS       = "Campus";
    public static final String MODULE_COLLEGE      = "College";
    public static final String MODULE_COURSE       = "Course";
    public static final String MODULE_ANNOUNCEMENT = "Announcement";
    public static final String MODULE_EVENT        = "Event";
    public static final String MODULE_REPORTS      = "Reports";

    /**
     * Core log method. All other helpers delegate to this.
     *
     * @param module   one of the MODULE_* constants
     * @param action   one of the ACTION_* constants
     * @param subject  record identifier (email, name, acronym, system_id)
     */
    public static void log(String module, String action, String subject) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String actorEmail = (user != null && user.getEmail() != null)
                ? user.getEmail()
                : "unknown";

        Map<String, Object> entry = new HashMap<>();
        entry.put("email",     actorEmail);
        entry.put("action",    action);
        entry.put("subject",   subject != null ? subject : "—");
        entry.put("module",    module);
        entry.put("timestamp", Timestamp.now());

        FirebaseFirestore.getInstance()
                .collection("activity_logs")
                .add(entry); // fire-and-forget; no listener needed
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /** Student or Staff — subject = email */
    public static void logUser(String action, String subjectEmail) {
        String module = MODULE_STUDENT; // caller can pass MODULE_STAFF if needed
        log(module, action, subjectEmail);
    }

    public static void logStudent(String action, String subjectEmail) {
        log(MODULE_STUDENT, action, subjectEmail);
    }

    public static void logStaff(String action, String subjectEmail) {
        log(MODULE_STAFF, action, subjectEmail);
    }

    /** Campus — subject = name */
    public static void logCampus(String action, String name) {
        log(MODULE_CAMPUS, action, name);
    }

    /** College — subject = acronym */
    public static void logCollege(String action, String acronym) {
        log(MODULE_COLLEGE, action, acronym);
    }

    /** Course — subject = acronym */
    public static void logCourse(String action, String acronym) {
        log(MODULE_COURSE, action, acronym);
    }

    /**
     * Announcement / Event
     * system_id = firstTitleWord + "_" + ddMMyyyyHHmm + "_" + creatorEmail
     */
    public static void logPost(String module, String action,
                               String title, String creatorEmail) {
        String firstWord = (title != null && !title.isEmpty())
                ? title.split("\\s+")[0]
                : "untitled";
        String datePart = new SimpleDateFormat("ddMMyyyyHHmm", Locale.getDefault())
                .format(new java.util.Date());
        String systemId = firstWord + "_" + datePart + "_" + creatorEmail;
        log(module, action, systemId);
    }

    public static void logAnnouncement(String action, String title, String creatorEmail) {
        logPost(MODULE_ANNOUNCEMENT, action, title, creatorEmail);
    }

    public static void logEvent(String action, String title, String creatorEmail) {
        logPost(MODULE_EVENT, action, title, creatorEmail);
    }

    /** Reports — subject = export details */
    public static void logReport(String action, String details) {
        log(MODULE_REPORTS, action, details);
    }
}
