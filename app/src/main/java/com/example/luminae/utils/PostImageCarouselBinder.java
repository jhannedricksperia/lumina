package com.example.luminae.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

/**
 * Binds a {@link ViewPager2} + optional dot row for post images on feed cards.
 */
public final class PostImageCarouselBinder {

    private PostImageCarouselBinder() {}

    public static void bind(
            ViewPager2 pager,
            LinearLayout dots,
            List<String> images,
            Context context,
            PostImagePagerAdapter.OnPageImageClickListener onImageClick) {
        if (pager == null) return;
        if (images == null || images.isEmpty()) {
            pager.setVisibility(View.GONE);
            if (dots != null) dots.setVisibility(View.GONE);
            return;
        }
        pager.setVisibility(View.VISIBLE);
        pager.setNestedScrollingEnabled(false);
        PostImagePagerAdapter adapter = new PostImagePagerAdapter(images, onImageClick);
        pager.setAdapter(adapter);
        int n = images.size();
        pager.setOffscreenPageLimit(Math.min(n, 4));

        if (dots != null) {
            if (n <= 1) {
                dots.setVisibility(View.GONE);
            } else {
                dots.setVisibility(View.VISIBLE);
                populateDots(dots, n, pager.getCurrentItem(), context);
                pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        populateDots(dots, n, position, context);
                    }
                });
            }
        }
    }

    private static void populateDots(LinearLayout row, int count, int selected, Context ctx) {
        row.removeAllViews();
        int margin = dp(ctx, 4);
        for (int i = 0; i < count; i++) {
            View d = new View(ctx);
            boolean on = i == selected;
            int size = dp(ctx, on ? 8 : 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            d.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFFFFFFFF);
            d.setBackground(gd);
            d.setAlpha(on ? 1f : 0.35f);
            row.addView(d);
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
