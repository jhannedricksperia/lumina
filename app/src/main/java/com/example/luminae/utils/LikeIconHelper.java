package com.example.luminae.utils;

import android.content.res.ColorStateList;
import android.widget.ImageView;

import androidx.core.widget.ImageViewCompat;

import com.example.luminae.R;

/**
 * Liked = solid filled heart; not liked = outline. Uses tint (no color filter on vectors).
 */
public final class LikeIconHelper {

    private LikeIconHelper() {}

    public static void setHeartTint(ImageView iv, boolean liked) {
        if (iv == null) return;
        iv.clearColorFilter();
        int c = liked ? 0xFFEF5350 : 0x88EF9A9A;
        if (liked) {
            iv.setImageResource(R.drawable.heart_filled);
        } else {
            iv.setImageResource(R.drawable.heart_outline);
        }
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(c));
    }

    public static void setCommentTint(ImageView iv) {
        if (iv == null) return;
        iv.clearColorFilter();
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(0xFF90CAF9));
    }
}
