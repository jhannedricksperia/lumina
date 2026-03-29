package com.example.luminae.admin.fragments;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
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
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.databinding.FragmentAdminFeedBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class AdminFeedFragment extends Fragment {

    private static final int TAB_ANNOUNCEMENTS = 0;
    private static final int TAB_EVENTS        = 1;

    private FragmentAdminFeedBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final List<DocumentSnapshot> all      = new ArrayList<>();
    private final List<DocumentSnapshot> filtered = new ArrayList<>();

    private FeedAdapter adapter;
    private int     activeTab      = TAB_ANNOUNCEMENTS;
    private String  filterPeriod   = "all";
    private String  filterStatus   = "all"; // "all" | "active" | "archived"
    private boolean sortDescending = true;

    private ActivityResultLauncher<String> imagePicker;
    private ImageView pendingPreviewView = null;
    private TextView  pendingImgHintView = null;
    private final String[] pendingBase64Holder = new String[]{null};

    private String pickedDate = "";
    private String pickedTime = "";

    interface NameCallback { void onResult(String name); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle saved) {
        b    = FragmentAdminFeedBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri == null) return;
                    String encoded = compressToBase64(uri);
                    if (encoded == null) return;
                    pendingBase64Holder[0] = encoded;
                    if (pendingPreviewView != null) loadBase64Image(encoded, pendingPreviewView);
                    if (pendingImgHintView != null) pendingImgHintView.setText("Image selected ✓");
                });

        b.tabLayout.addTab(b.tabLayout.newTab().setText("Announcements"));
        b.tabLayout.addTab(b.tabLayout.newTab().setText("Events"));
        b.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                reloadCollection();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        adapter = new FeedAdapter();
        b.recyclerFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        b.recyclerFeed.setAdapter(adapter);

        b.chipGroupFilter.setOnCheckedStateChangeListener((g, ids) -> {
            // Period
            if      (b.chipToday.isChecked())    filterPeriod = "today";
            else if (b.chipWeek.isChecked())     filterPeriod = "week";
            else if (b.chipMonth.isChecked())    filterPeriod = "month";
            else                                 filterPeriod = "all";
            // Status
            if      (b.chipActive.isChecked())   filterStatus = "active";
            else if (b.chipArchived.isChecked()) filterStatus = "archived";
            else                                 filterStatus = "all";
            applyFilter();
        });

        b.btnSort.setOnClickListener(v -> {
            sortDescending = !sortDescending;
            b.btnSort.setText(sortDescending ? "↓ Newest" : "↑ Oldest");
            applyFilter();
        });

        b.btnAdd.setOnClickListener(v -> {
            if (activeTab == TAB_ANNOUNCEMENTS) showAnnouncementDialog(null);
            else                                showEventDialog(null);
        });

        reloadCollection();
        return b.getRoot();
    }

    // ── collection switching ──────────────────────────────────────────────────

    private ListenerRegistration activeListener = null;

    private void reloadCollection() {
        if (activeListener != null) { activeListener.remove(); activeListener = null; }
        all.clear();
        filtered.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        String collection = activeTab == TAB_ANNOUNCEMENTS ? "announcements" : "events";
        activeListener = db.collection(collection).addSnapshotListener((snap, e) -> {
            if (snap == null || b == null) return;
            all.clear();
            all.addAll(snap.getDocuments());
            applyFilter();
        });
    }

    // ── filter / sort ─────────────────────────────────────────────────────────

    private void applyFilter() {
        if (b == null) return;

        Calendar from = Calendar.getInstance();
        switch (filterPeriod) {
            case "today":
                from.set(Calendar.HOUR_OF_DAY, 0);
                from.set(Calendar.MINUTE, 0);
                from.set(Calendar.SECOND, 0);
                from.set(Calendar.MILLISECOND, 0);
                break;
            case "week":  from.add(Calendar.DAY_OF_YEAR, -7); break;
            case "month": from.add(Calendar.MONTH, -1);       break;
            default:      from.set(2000, 0, 1);
        }
        Date fromDate = from.getTime();

        filtered.clear();
        for (DocumentSnapshot doc : all) {

            // Status filter
            if (!"all".equals(filterStatus)) {
                String docStatus = doc.getString("status");
                if (docStatus == null) docStatus = "Active";
                boolean isActive = "Active".equalsIgnoreCase(docStatus);
                if ("active".equals(filterStatus)   && !isActive) continue;
                if ("archived".equals(filterStatus) &&  isActive) continue;
            }

            // Period filter
            if (!"all".equals(filterPeriod)) {
                Timestamp ts = doc.getTimestamp("createdAt");
                if (ts == null || ts.toDate().before(fromDate)) continue;
            }

            filtered.add(doc);
        }

        filtered.sort((a, z) -> {
            Timestamp ta = a.getTimestamp("createdAt");
            Timestamp tz = z.getTimestamp("createdAt");
            if (ta == null && tz == null) return 0;
            if (ta == null) return 1;
            if (tz == null) return -1;
            return sortDescending
                    ? tz.toDate().compareTo(ta.toDate())
                    : ta.toDate().compareTo(tz.toDate());
        });

        String label = activeTab == TAB_ANNOUNCEMENTS ? "announcement(s)" : "event(s)";
        b.tvCount.setText(filtered.size() + " " + label);
        adapter.notifyDataSetChanged();
    }

    // ── image helpers ─────────────────────────────────────────────────────────

    private String compressToBase64(Uri uri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(), uri);
            int maxWidth = 800;
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                original = Bitmap.createScaledBitmap(original, maxWidth,
                        Math.round(original.getHeight() * ratio), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, 50, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) { return null; }
    }

    private void loadBase64Image(String base64, ImageView iv) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            iv.setVisibility(View.VISIBLE);
        } catch (Exception e) { iv.setVisibility(View.GONE); }
    }

    // ── full name helper ──────────────────────────────────────────────────────

    private void getActorFullName(NameCallback cb) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { cb.onResult("Unknown"); return; }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String f = doc.getString("fName") != null ? doc.getString("fName") : "";
                    String l = doc.getString("lName") != null ? doc.getString("lName") : "";
                    String full = (f + " " + l).trim();
                    cb.onResult(full.isEmpty() ? "Unknown" : full);
                })
                .addOnFailureListener(e -> cb.onResult("Unknown"));
    }

    // ── announcement dialog ───────────────────────────────────────────────────

    private void showAnnouncementDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_post_announcement, null);

        EditText  etTitle   = form.findViewById(R.id.et_title);
        EditText  etDesc    = form.findViewById(R.id.et_description);
        ImageView ivPreview = form.findViewById(R.id.iv_preview);
        TextView  btnPick   = form.findViewById(R.id.btn_pick_image);
        TextView  tvHint    = form.findViewById(R.id.tv_image_hint);

        pendingBase64Holder[0] = null;
        pendingPreviewView     = ivPreview;
        pendingImgHintView     = tvHint;

        if (existing != null) {
            etTitle.setText(existing.getString("title"));
            etDesc.setText(existing.getString("description"));
            String b64 = existing.getString("imageBase64");
            if (b64 != null && !b64.isEmpty()) {
                loadBase64Image(b64, ivPreview);
                tvHint.setText("Tap to change image");
            }
        }

        btnPick.setOnClickListener(v -> imagePicker.launch("image/*"));

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
                    String img = pendingBase64Holder[0] != null ? pendingBase64Holder[0]
                            : (existing != null ? existing.getString("imageBase64") : null);
                    saveAnnouncement(existing, title, desc, img);
                })
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(d -> {
                    pendingBase64Holder[0] = null;
                    pendingPreviewView     = null;
                    pendingImgHintView     = null;
                })
                .show();
    }

    private void saveAnnouncement(DocumentSnapshot existing,
                                  String title, String desc, String imageBase64) {
        getActorFullName(fullName -> {
            String uid = FirebaseAuth.getInstance().getUid();
            Map<String, Object> data = new HashMap<>();
            data.put("title",       title);
            data.put("description", desc);
            if (imageBase64 != null) data.put("imageBase64", imageBase64);

            if (existing == null) {
                data.put("createdAt",   Timestamp.now());
                data.put("createdBy",   fullName);
                data.put("createdById", uid);
                data.put("status",      "Active");
                data.put("likeCount",   0L);
                db.collection("announcements").add(data)
                        .addOnSuccessListener(r ->
                                ActivityLogger.logAnnouncement(
                                        ActivityLogger.ACTION_CREATE, title, fullName))
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                data.put("modifiedAt",   Timestamp.now());
                data.put("modifiedBy",   fullName);
                data.put("modifiedById", uid);
                existing.getReference().update(data)
                        .addOnSuccessListener(v ->
                                ActivityLogger.logAnnouncement(
                                        ActivityLogger.ACTION_MODIFIED, title, fullName))
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── event dialog ──────────────────────────────────────────────────────────

    private void showEventDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_event_form, null);

        EditText  etTitle      = form.findViewById(R.id.et_title);
        EditText  etDesc       = form.findViewById(R.id.et_description);
        EditText  etWhere      = form.findViewById(R.id.et_where);
        EditText  etMaxPax     = form.findViewById(R.id.et_max_participants);
        TextView  tvPickedDate = form.findViewById(R.id.tv_picked_date);
        TextView  tvPickedTime = form.findViewById(R.id.tv_picked_time);
        TextView  tvImgHint    = form.findViewById(R.id.tv_image_hint);
        Button    btnPickDate  = form.findViewById(R.id.btn_pick_date);
        Button    btnPickTime  = form.findViewById(R.id.btn_pick_time);
        Button    btnPickImage = form.findViewById(R.id.btn_pick_image);
        ImageView ivPreview    = form.findViewById(R.id.iv_preview);

        pendingBase64Holder[0] = null;
        pendingPreviewView     = ivPreview;
        pendingImgHintView     = tvImgHint;

        if (existing != null) {
            etTitle.setText(existing.getString("title"));
            etDesc.setText(existing.getString("description"));
            etWhere.setText(existing.getString("where"));
            Long maxPax = existing.getLong("maxParticipants");
            if (maxPax != null) etMaxPax.setText(String.valueOf(maxPax));
            pickedDate = existing.getString("date") != null ? existing.getString("date") : "";
            pickedTime = existing.getString("time") != null ? existing.getString("time") : "";
            tvPickedDate.setText(pickedDate.isEmpty() ? "No date selected" : pickedDate);
            tvPickedTime.setText(pickedTime.isEmpty() ? "No time selected" : pickedTime);
            String b64 = existing.getString("imageBase64");
            if (b64 != null && !b64.isEmpty()) {
                loadBase64Image(b64, ivPreview);
                tvImgHint.setText("Tap to change image");
            }
        } else {
            pickedDate = "";
            pickedTime = "";
            tvPickedDate.setText("No date selected");
            tvPickedTime.setText("No time selected");
        }

        btnPickDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, y, m, d) -> {
                pickedDate = d + "/" + (m + 1) + "/" + y;
                tvPickedDate.setText(pickedDate);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnPickTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(requireContext(), (view, hr, min) -> {
                pickedTime = String.format(Locale.getDefault(), "%02d:%02d", hr, min);
                tvPickedTime.setText(pickedTime);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
        });

        btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "New Event" : "Edit Event")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String title  = etTitle.getText().toString().trim();
                    String desc   = etDesc.getText().toString().trim();
                    String where  = etWhere.getText().toString().trim();
                    String maxStr = etMaxPax.getText().toString().trim();
                    int maxPax = 0;
                    try { if (!maxStr.isEmpty()) maxPax = Integer.parseInt(maxStr); }
                    catch (NumberFormatException ignored) {}
                    if (title.isEmpty()) {
                        Toast.makeText(getContext(), "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String img = pendingBase64Holder[0] != null ? pendingBase64Holder[0]
                            : (existing != null ? existing.getString("imageBase64") : null);
                    saveEvent(existing, title, desc, where, pickedDate, pickedTime, maxPax, img);
                })
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(d -> {
                    pendingBase64Holder[0] = null;
                    pendingPreviewView     = null;
                    pendingImgHintView     = null;
                })
                .show();
    }

    private void saveEvent(DocumentSnapshot existing, String title, String desc,
                           String where, String date, String time,
                           int maxPax, String imageBase64) {
        getActorFullName(fullName -> {
            String uid = FirebaseAuth.getInstance().getUid();
            Map<String, Object> data = new HashMap<>();
            data.put("title",           title);
            data.put("description",     desc);
            data.put("where",           where);
            data.put("date",            date);
            data.put("time",            time);
            data.put("maxParticipants", maxPax);
            if (imageBase64 != null) data.put("imageBase64", imageBase64);

            if (existing == null) {
                data.put("createdAt",   Timestamp.now());
                data.put("createdBy",   fullName);
                data.put("createdById", uid);
                data.put("status",      "Active");
                data.put("likeCount",   0L);
                data.put("goingCount",  0);
                db.collection("events").add(data)
                        .addOnSuccessListener(r ->
                                ActivityLogger.logEvent(
                                        ActivityLogger.ACTION_CREATE, title, fullName))
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                data.put("modifiedAt",   Timestamp.now());
                data.put("modifiedBy",   fullName);
                data.put("modifiedById", uid);
                existing.getReference().update(data)
                        .addOnSuccessListener(v ->
                                ActivityLogger.logEvent(
                                        ActivityLogger.ACTION_MODIFIED, title, fullName))
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── adapter ───────────────────────────────────────────────────────────────

    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView  tvCreatedBy, tvDateCreated, tvTitle, tvDesc, tvHearts, btnDelete;
            ImageView ivImage;
            TextView  tvWhere, tvEventDate, tvGoingCount, tvGoingInfo, btnGoing;
            View      layoutEventInfo;

            VH(View v) {
                super(v);
                tvCreatedBy   = v.findViewById(R.id.tv_poster_name) != null
                        ? v.findViewById(R.id.tv_poster_name)
                        : v.findViewById(R.id.tv_posted_by);
                tvDateCreated = v.findViewById(R.id.tv_time) != null
                        ? v.findViewById(R.id.tv_time)
                        : v.findViewById(R.id.tv_created_date);
                tvTitle         = v.findViewById(R.id.tv_title);
                tvDesc          = v.findViewById(R.id.tv_description);
                tvHearts        = v.findViewById(R.id.btn_like);
                ivImage         = v.findViewById(R.id.iv_post_image);
                btnDelete       = v.findViewById(R.id.btn_delete);
                tvWhere         = v.findViewById(R.id.tv_location);
                tvEventDate     = v.findViewById(R.id.tv_event_date);
                tvGoingCount    = v.findViewById(R.id.tv_going_count);
                tvGoingInfo     = v.findViewById(R.id.tv_going_info);
                btnGoing        = v.findViewById(R.id.btn_going);
                layoutEventInfo = v.findViewById(R.id.layout_event_info);
            }
        }

        @Override
        public int getItemViewType(int position) { return activeTab; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            int layout = viewType == TAB_ANNOUNCEMENTS
                    ? R.layout.item_feed_post
                    : R.layout.item_event_post;
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String title = doc.getString("title")       != null ? doc.getString("title")       : "—";
            String desc  = doc.getString("description") != null ? doc.getString("description") : "—";
            String by    = doc.getString("createdBy")   != null ? doc.getString("createdBy")   : "Admin";
            Timestamp ts = doc.getTimestamp("createdAt");

            // Support both "likeCount" (staff posts) and "hearts" (older admin posts)
            long hearts = 0;
            if (doc.getLong("likeCount") != null)   hearts = doc.getLong("likeCount");
            else if (doc.getLong("hearts") != null)  hearts = doc.getLong("hearts");

            if (h.tvTitle       != null) h.tvTitle.setText(title);
            if (h.tvDesc        != null) h.tvDesc.setText(desc);
            if (h.tvCreatedBy   != null) h.tvCreatedBy.setText(by);
            if (h.tvDateCreated != null)
                h.tvDateCreated.setText(ts != null ? sdf.format(ts.toDate()) : "—");

            // image
            if (h.ivImage != null) {
                h.ivImage.setVisibility(View.GONE);
                String base64 = doc.getString("imageBase64");
                if (base64 != null && !base64.isEmpty()) loadBase64Image(base64, h.ivImage);
            }

            // event-only extras
            if (activeTab == TAB_EVENTS) {
                if (h.layoutEventInfo != null) h.layoutEventInfo.setVisibility(View.VISIBLE);
                long going = doc.getLong("goingCount") != null ? doc.getLong("goingCount") : 0;
                if (h.tvWhere != null)
                    h.tvWhere.setText("📍 " + (doc.getString("where") != null ? doc.getString("where") : "—"));
                if (h.tvEventDate != null) {
                    String date = doc.getString("date") != null ? doc.getString("date") : "";
                    String time = doc.getString("time") != null ? doc.getString("time") : "";
                    h.tvEventDate.setText("📅 " + date + (time.isEmpty() ? "" : "  " + time));
                }
                if (h.tvGoingCount != null) h.tvGoingCount.setText("🎫 " + going + " going");
                if (h.tvGoingInfo  != null) {
                    h.tvGoingInfo.setVisibility(View.VISIBLE);
                    h.tvGoingInfo.setText("🎫 " + going + " going");
                }
            }

            // ── Like logic (mirrors StaffPostFragment exactly) ────────────────
            if (h.tvHearts != null) {
                String col = activeTab == TAB_ANNOUNCEMENTS ? "announcements" : "events";
                DocumentReference postRef = db.collection(col).document(doc.getId());
                String uid = auth.getUid();
                DocumentReference likeRef = uid != null
                        ? postRef.collection("likes").document(uid) : null;

                // Local mutable state for this card
                final long[]    likeCount = {hearts};
                final boolean[] likedByMe = {false};

                // Immediate render with default state
                h.tvHearts.setText("♥ " + likeCount[0]);
                h.tvHearts.setTextColor(0x88EF9A9A);

                // Async check: did this admin already like it?
                if (likeRef != null) {
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        likedByMe[0] = likeDoc.exists();
                        h.tvHearts.setText("♥ " + likeCount[0]);
                        h.tvHearts.setTextColor(likedByMe[0] ? 0xFFEF5350 : 0x88EF9A9A);
                    });
                }

                // Toggle like / unlike
                h.tvHearts.setOnClickListener(v -> {
                    if (uid == null || likeRef == null) return;
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        if (likeDoc.exists()) {
                            // Unlike
                            likeRef.delete();
                            postRef.update("likeCount", FieldValue.increment(-1));
                            likedByMe[0]  = false;
                            likeCount[0]  = Math.max(0, likeCount[0] - 1);
                        } else {
                            // Like
                            Map<String, Object> likeData = new HashMap<>();
                            likeData.put("uid",     uid);
                            likeData.put("likedAt", Timestamp.now());
                            likeRef.set(likeData);
                            postRef.update("likeCount", FieldValue.increment(1));
                            likedByMe[0] = true;
                            likeCount[0]++;
                        }
                        h.tvHearts.setText("♥ " + likeCount[0]);
                        h.tvHearts.setTextColor(likedByMe[0] ? 0xFFEF5350 : 0x88EF9A9A);
                    });
                });
            }
            // ── end like logic ────────────────────────────────────────────────

            // root click → PostDetailActivity
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), PostDetailActivity.class);
                intent.putExtra("docId", doc.getId());
                intent.putExtra("type",  activeTab == TAB_ANNOUNCEMENTS ? "Announcement" : "Event");
                startActivity(intent);
            });

            // delete button — always visible for admin
            if (h.btnDelete != null) {
                h.btnDelete.setVisibility(View.VISIBLE);
                h.btnDelete.setOnClickListener(v ->
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete")
                                .setMessage("Delete \"" + title + "\"? This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) ->
                                        getActorFullName(fullName ->
                                                doc.getReference().delete()
                                                        .addOnSuccessListener(unused -> {
                                                            if (activeTab == TAB_ANNOUNCEMENTS)
                                                                ActivityLogger.logAnnouncement(
                                                                        ActivityLogger.ACTION_DELETE, title, fullName);
                                                            else
                                                                ActivityLogger.logEvent(
                                                                        ActivityLogger.ACTION_DELETE, title, fullName);
                                                        })))
                                .setNegativeButton("Cancel", null)
                                .show());
            }
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    // ── lifecycle cleanup ─────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeListener != null) { activeListener.remove(); activeListener = null; }
        pendingPreviewView = null;
        pendingImgHintView = null;
        b = null;
    }
}