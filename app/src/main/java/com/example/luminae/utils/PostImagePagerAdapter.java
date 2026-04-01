package com.example.luminae.utils;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.luminae.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * ViewPager2 pages for JPEG base64 strings shown in the feed.
 */
public class PostImagePagerAdapter extends RecyclerView.Adapter<PostImagePagerAdapter.VH> {

    public interface OnPageImageClickListener {
        void onPageImageClick(int position);
    }

    private final List<String> images;
    private final OnPageImageClickListener clickListener;

    public PostImagePagerAdapter(List<String> images, OnPageImageClickListener clickListener) {
        this.images = images;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post_image_slide, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String b64 = images.get(position);
        h.image.setVisibility(View.VISIBLE);
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            h.image.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } catch (Exception e) {
            h.image.setVisibility(View.GONE);
        }
        final int pos = position;
        h.image.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onPageImageClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ShapeableImageView image;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
        }
    }
}
