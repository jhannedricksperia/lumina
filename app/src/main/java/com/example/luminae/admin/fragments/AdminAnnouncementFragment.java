package com.example.luminae.admin.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.FragmentAdminAnnouncementBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class AdminAnnouncementFragment extends Fragment {

    private FragmentAdminAnnouncementBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> all = new ArrayList<>(), filtered = new ArrayList<>();
    private AnnouncementAdapter adapter;
    private String filterPeriod = "all";
    private boolean sortDescending = true;
    private String pendingBase64Image = null; // stores compressed base64 of picked image
    private ActivityResultLauncher<String> imagePicker;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b = FragmentAdminAnnouncementBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();

        // Image picker — compresses and converts to Base64 immediately on pick
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    pendingBase64Image = compressToBase64(uri);
                });

        adapter = new AnnouncementAdapter();
        b.recyclerAnnouncements.setLayoutManager(new LinearLayoutManager(getContext()));
        b.recyclerAnnouncements.setAdapter(adapter);

        // Filter chips
        b.chipGroupFilter.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipToday.isChecked()) filterPeriod = "today";
            else if (b.chipWeek.isChecked())  filterPeriod = "week";
            else if (b.chipMonth.isChecked()) filterPeriod = "month";
            else                              filterPeriod = "all";
            applyFilter();
        });

        // Sort toggle
        b.btnSort.setOnClickListener(v -> {
            sortDescending = !sortDescending;
            b.btnSort.setText(sortDescending ? "↓ Newest" : "↑ Oldest");
            applyFilter();
        });

        b.btnAddAnnouncement.setOnClickListener(v -> showPostDialog(null));

        loadAnnouncements();
        return b.getRoot();
    }

    // ── Convert Uri → compressed Base64 string ───────────────────────────────
    private String compressToBase64(Uri uri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(), uri);

            // Scale down if too large (max 800px wide)
            int maxWidth = 800;
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                int newHeight = Math.round(original.getHeight() * ratio);
                original = Bitmap.createScaledBitmap(original, maxWidth, newHeight, true);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, 50, out); // 50% quality
            byte[] bytes = out.toByteArray();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── Decode Base64 → Bitmap for ImageView ─────────────────────────────────
    private void loadBase64Image(String base64, ImageView imageView) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            imageView.setImageBitmap(bmp);
            imageView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            imageView.setVisibility(View.GONE);
        }
    }

    // ── Load from Firestore ───────────────────────────────────────────────────
    private void loadAnnouncements() {
        db.collection("announcements")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    all = snap.getDocuments();
                    applyFilter();
                });
    }

    private void applyFilter() {
        Calendar from = Calendar.getInstance();
        switch (filterPeriod) {
            case "today":
                from.set(Calendar.HOUR_OF_DAY, 0);
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);
                break;
            case "week":  from.add(Calendar.DAY_OF_YEAR, -7); break;
            case "month": from.add(Calendar.MONTH, -1); break;
            default: from.set(2000, 0, 1);
        }
        Date fromDate = from.getTime();
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts == null || ts.toDate().after(fromDate)) filtered.add(doc);
        }
        filtered.sort((a, z) -> {
            Timestamp ta = a.getTimestamp("createdAt"), tz = z.getTimestamp("createdAt");
            if (ta == null || tz == null) return 0;
            return sortDescending
                    ? tz.toDate().compareTo(ta.toDate())
                    : ta.toDate().compareTo(tz.toDate());
        });
        b.tvCount.setText(filtered.size() + " announcement(s)");
        adapter.notifyDataSetChanged();
    }

    // ── Post / Edit Dialog ────────────────────────────────────────────────────
    private void showPostDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_post_announcement, null);

        EditText  etTitle    = form.findViewById(R.id.et_title);
        EditText  etDesc     = form.findViewById(R.id.et_description);
        ImageView ivPreview  = form.findViewById(R.id.iv_preview);
        Button    btnPickImg = form.findViewById(R.id.btn_pick_image);
        TextView  tvImgHint  = form.findViewById(R.id.tv_image_hint);

        pendingBase64Image = null; // reset for this dialog session

        if (existing != null) {
            etTitle.setText(existing.getString("title"));
            etDesc.setText(existing.getString("description"));
            // Show existing image preview if present
            String existingB64 = existing.getString("imageBase64");
            if (existingB64 != null && !existingB64.isEmpty()) {
                loadBase64Image(existingB64, ivPreview);
                tvImgHint.setText("Tap to change image");
            }
        }

        btnPickImg.setOnClickListener(v -> {
            imagePicker.launch("image/*");
            // Show preview after pick — handled via pendingBase64Image
            // We defer preview update to a short post to let the launcher complete
            btnPickImg.postDelayed(() -> {
                if (pendingBase64Image != null) {
                    loadBase64Image(pendingBase64Image, ivPreview);
                    tvImgHint.setText("Image selected ✓");
                }
            }, 1000);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "New Announcement" : "Edit Announcement")
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    String desc  = etDesc.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(getContext(), "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = FirebaseAuth.getInstance().getUid();

                    // Use newly picked image, or keep existing, or null
                    String imageToSave = pendingBase64Image != null
                            ? pendingBase64Image
                            : (existing != null ? existing.getString("imageBase64") : null);

                    saveAnnouncement(existing, title, desc, imageToSave, uid);
                    pendingBase64Image = null;
                })
                .setNegativeButton("Cancel", (d, w) -> pendingBase64Image = null)
                .show();
    }

    // ── Save to Firestore ─────────────────────────────────────────────────────
    private void saveAnnouncement(DocumentSnapshot existing, String title,
                                  String desc, String imageBase64, String uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", desc);
        if (imageBase64 != null) data.put("imageBase64", imageBase64);

        if (existing == null) {
            data.put("createdAt", Timestamp.now());
            data.put("createdBy", uid);
            data.put("status", "Active");
            data.put("hearts", 0);
            db.collection("announcements").add(data)
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Failed to post: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        } else {
            data.put("modifiedAt", Timestamp.now());
            data.put("modifiedBy", uid);
            existing.getReference().update(data)
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Failed to update: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    private class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView  tvAuthorInitials, tvCreatedBy, tvDateCreated, tvStatus,
                    tvTitle, tvDesc, tvHearts, tvModifiedInfo,
                    btnEdit, btnArchive, btnDelete;
            ImageView ivImage;

            VH(View v) {
                super(v);
                tvAuthorInitials = v.findViewById(R.id.tv_author_initials);
                tvCreatedBy      = v.findViewById(R.id.tv_created_by);
                tvDateCreated    = v.findViewById(R.id.tv_date_created);
                tvStatus         = v.findViewById(R.id.tv_status);
                tvTitle          = v.findViewById(R.id.tv_title);
                tvDesc           = v.findViewById(R.id.tv_description);
                tvHearts         = v.findViewById(R.id.tv_hearts);
                tvModifiedInfo   = v.findViewById(R.id.tv_modified_info);
                ivImage          = v.findViewById(R.id.iv_image);
                btnEdit          = v.findViewById(R.id.btn_edit);
                btnArchive       = v.findViewById(R.id.btn_archive);
                btnDelete        = v.findViewById(R.id.btn_delete);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_announcement, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String status = doc.getString("status")      != null ? doc.getString("status")      : "Active";
            String title  = doc.getString("title")       != null ? doc.getString("title")        : "—";
            String desc   = doc.getString("description") != null ? doc.getString("description")  : "—";
            String by     = doc.getString("createdBy")   != null ? doc.getString("createdBy")    : "Admin";
            long   hearts = doc.getLong("hearts")        != null ? doc.getLong("hearts")         : 0;
            Timestamp ts  = doc.getTimestamp("createdAt");

            h.tvTitle.setText(title);
            h.tvDesc.setText(desc);
            h.tvCreatedBy.setText(by);
            h.tvHearts.setText(String.valueOf(hearts));
            h.tvDateCreated.setText(ts != null ? sdf.format(ts.toDate()) : "—");
            h.tvStatus.setText(status);
            h.tvStatus.setBackgroundResource(
                    "Active".equals(status) ? R.drawable.badge_active : R.drawable.badge_blocked);
            h.tvAuthorInitials.setText(
                    by.length() > 0 ? String.valueOf(by.charAt(0)).toUpperCase() : "A");

            Timestamp mod = doc.getTimestamp("modifiedAt");
            String modBy  = doc.getString("modifiedBy");
            h.tvModifiedInfo.setText(
                    (mod != null && modBy != null)
                            ? "Edited " + sdf.format(mod.toDate()) + " by " + modBy
                            : "");

            // Load Base64 image
            String base64 = doc.getString("imageBase64");
            if (base64 != null && !base64.isEmpty()) {
                loadBase64Image(base64, h.ivImage);
            } else {
                h.ivImage.setVisibility(View.GONE);
            }

            // Edit
            h.btnEdit.setOnClickListener(v -> showPostDialog(doc));

            // Archive toggle
            h.btnArchive.setOnClickListener(v -> {
                String ns = "Archived".equals(status) ? "Active" : "Archived";
                h.btnArchive.setText("Archived".equals(status) ? "Archive" : "Unarchive");
                doc.getReference().update(
                        "status", ns,
                        "modifiedAt", Timestamp.now(),
                        "modifiedBy", FirebaseAuth.getInstance().getUid());
            });

            // Delete
            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Announcement")
                            .setMessage("Delete \"" + title + "\"? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) -> doc.getReference().delete())
                            .setNegativeButton("Cancel", null).show());
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}