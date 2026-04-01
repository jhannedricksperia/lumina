package com.example.luminae.utils;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Shared display helpers for event date/location across feeds and detail screens.
 */
public final class EventDisplayUtils {

    private EventDisplayUtils() {}

    /** Returns a non-empty date string, or "TBA" when no date/time was set. */
    public static String formatEventDate(DocumentSnapshot doc) {
        if (doc == null) return "TBA";
        String ev = doc.getString("eventDate");
        if (ev != null && !ev.trim().isEmpty()) return ev.trim();
        String d = doc.getString("date");
        String t = doc.getString("time");
        d = d != null ? d : "";
        t = t != null ? t : "";
        String merged = (d + (t.isEmpty() ? "" : " " + t)).trim();
        return merged.isEmpty() ? "TBA" : merged;
    }

    public static String formatLocation(DocumentSnapshot doc) {
        if (doc == null) return "";
        String loc = doc.getString("location");
        if (loc != null && !loc.trim().isEmpty()) return loc.trim();
        String where = doc.getString("where");
        return where != null ? where.trim() : "";
    }

    public static long countGoing(DocumentSnapshot doc) {
        if (doc == null) return 0;
        Long p = doc.getLong("participantCount");
        if (p != null) return p;
        Long g = doc.getLong("goingCount");
        return g != null ? g : 0;
    }
}
