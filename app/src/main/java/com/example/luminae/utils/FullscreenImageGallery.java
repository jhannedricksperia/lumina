package com.example.luminae.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * Instagram-style fullscreen viewer with swipe between images (pinch-zoom per page).
 */
public final class FullscreenImageGallery {

    private FullscreenImageGallery() {}

    public static void show(Context context, List<String> base64JpegList, int startIndex) {
        if (context == null || base64JpegList == null || base64JpegList.isEmpty()) return;
        int start = Math.max(0, Math.min(startIndex, base64JpegList.size() - 1));

        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF000000);

        ViewPager2 pager = new ViewPager2(context);
        pager.setAdapter(new PhotoPagerAdapter(base64JpegList));
        pager.setCurrentItem(start, false);
        pager.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dotsLp.bottomMargin = dp(context, 28);
        dots.setLayoutParams(dotsLp);

        if (base64JpegList.size() > 1) {
            buildDots(dots, base64JpegList.size(), start, context);
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateDots(dots, base64JpegList.size(), position, context);
                }
            });
        } else {
            dots.setVisibility(View.GONE);
        }

        TextView btnClose = new TextView(context);
        btnClose.setText("✕");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        btnClose.setTypeface(null, Typeface.BOLD);
        int p = dp(context, 14);
        btnClose.setPadding(p, p, p, p);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.TOP | Gravity.END;
        closeLp.topMargin = dp(context, 36);
        closeLp.rightMargin = dp(context, 8);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        root.addView(pager);
        root.addView(dots);
        root.addView(btnClose);
        dialog.setContentView(root);
        dialog.show();
    }

    private static void buildDots(LinearLayout row, int count, int selected, Context ctx) {
        row.removeAllViews();
        int m = dp(ctx, 4);
        for (int i = 0; i < count; i++) {
            View d = new View(ctx);
            boolean on = i == selected;
            int size = dp(ctx, on ? 8 : 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(m, 0, m, 0);
            d.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFFFFFFFF);
            d.setBackground(gd);
            d.setAlpha(on ? 1f : 0.35f);
            row.addView(d);
        }
    }

    private static void updateDots(LinearLayout row, int count, int selected, Context ctx) {
        buildDots(row, count, selected, ctx);
    }

    private static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    private static class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.VH> {
        private final List<String> images;

        PhotoPagerAdapter(List<String> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PhotoView pv = new PhotoView(parent.getContext());
            pv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return new VH(pv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            try {
                byte[] bytes = Base64.decode(images.get(position), Base64.DEFAULT);
                h.photoView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            } catch (Exception e) {
                h.photoView.setImageBitmap(null);
            }
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final PhotoView photoView;

            VH(@NonNull PhotoView itemView) {
                super(itemView);
                photoView = itemView;
            }
        }
    }
}
