package com.example.luminae.utils;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads legacy {@code imageBase64} and/or {@code imagesBase64} list from Firestore.
 */
public final class PostImageList {

    private PostImageList() {}

    @SuppressWarnings("unchecked")
    public static List<String> fromDocument(DocumentSnapshot doc) {
        List<String> out = new ArrayList<>();
        if (doc == null || !doc.exists()) return out;
        Object raw = doc.get("imagesBase64");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof String) {
                    String s = ((String) o).trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        }
        if (out.isEmpty()) {
            String single = doc.getString("imageBase64");
            if (single != null && !single.trim().isEmpty()) out.add(single.trim());
        }
        return out;
    }

    public static String signature(List<String> images) {
        if (images == null || images.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(images.size(), 5);
        for (int i = 0; i < n; i++) {
            String s = images.get(i);
            sb.append(s != null ? s.length() : 0).append(':');
        }
        sb.append(images.size());
        return sb.toString();
    }
}
