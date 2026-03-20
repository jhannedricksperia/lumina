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
import com.example.luminae.activities.EventParticipantsActivity;
import com.example.luminae.databinding.FragmentAdminEventBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class AdminEventFragment extends Fragment {

    private FragmentAdminEventBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> all = new ArrayList<>(), filtered = new ArrayList<>();
    private EventAdapter adapter;
    private String filterPeriod = "all";
    private boolean sortDescending = true;
    private String pendingBase64Image = null;
    private ActivityResultLauncher<String> imagePicker;
    private String pickedDate = "", pickedTime = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b = FragmentAdminEventBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();

        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) pendingBase64Image = compressToBase64(uri); });

        adapter = new EventAdapter();
        b.recyclerEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        b.recyclerEvents.setAdapter(adapter);

        b.chipGroupFilter.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipToday.isChecked()) filterPeriod = "today";
            else if (b.chipWeek.isChecked())  filterPeriod = "week";
            else if (b.chipMonth.isChecked()) filterPeriod = "month";
            else                              filterPeriod = "all";
            applyFilter();
        });

        b.btnSort.setOnClickListener(v -> {
            sortDescending = !sortDescending;
            b.btnSort.setText(sortDescending ? "↓ Newest" : "↑ Oldest");
            applyFilter();
        });

        b.btnAddEvent.setOnClickListener(v -> showEventDialog(null));

        loadEvents();
        return b.getRoot();
    }

    private String compressToBase64(Uri uri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
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

    private void loadEvents() {
        db.collection("events").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            all = snap.getDocuments();
            applyFilter();
        });
    }

    private void applyFilter() {
        Calendar from = Calendar.getInstance();
        switch (filterPeriod) {
            case "today": from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0); from.set(Calendar.SECOND, 0); break;
            case "week":  from.add(Calendar.DAY_OF_YEAR, -7); break;
            case "month": from.add(Calendar.MONTH, -1); break;
            default:      from.set(2000, 0, 1);
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
            return sortDescending ? tz.toDate().compareTo(ta.toDate()) : ta.toDate().compareTo(tz.toDate());
        });
        b.tvCount.setText(filtered.size() + " event(s)");
        adapter.notifyDataSetChanged();
    }

    private void showEventDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(getContext()).inflate(R.layout.dialog_event_form, null);

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

        pendingBase64Image = null;

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
            if (b64 != null && !b64.isEmpty()) { loadBase64Image(b64, ivPreview); tvImgHint.setText("Tap to change image"); }
        } else {
            pickedDate = ""; pickedTime = "";
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

        btnPickImage.setOnClickListener(v -> {
            imagePicker.launch("image/*");
            btnPickImage.postDelayed(() -> {
                if (pendingBase64Image != null) { loadBase64Image(pendingBase64Image, ivPreview); tvImgHint.setText("Image selected ✓"); }
            }, 1000);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "New Event" : "Edit Event")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String title  = etTitle.getText().toString().trim();
                    String desc   = etDesc.getText().toString().trim();
                    String where  = etWhere.getText().toString().trim();
                    String maxStr = etMaxPax.getText().toString().trim();
                    int    maxPax = maxStr.isEmpty() ? 0 : Integer.parseInt(maxStr);
                    if (title.isEmpty()) { Toast.makeText(getContext(), "Title required", Toast.LENGTH_SHORT).show(); return; }
                    String uid = FirebaseAuth.getInstance().getUid();
                    String imageToSave = pendingBase64Image != null ? pendingBase64Image
                            : (existing != null ? existing.getString("imageBase64") : null);
                    saveEvent(existing, title, desc, where, pickedDate, pickedTime, maxPax, imageToSave, uid);
                    pendingBase64Image = null;
                })
                .setNegativeButton("Cancel", (d, w) -> pendingBase64Image = null)
                .show();
    }

    private void saveEvent(DocumentSnapshot existing, String title, String desc,
                           String where, String date, String time,
                           int maxPax, String imageBase64, String uid) {
        String actorEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "unknown";

        Map<String, Object> data = new HashMap<>();
        data.put("title", title); data.put("description", desc);
        data.put("where", where); data.put("date", date);
        data.put("time", time);   data.put("maxParticipants", maxPax);
        if (imageBase64 != null) data.put("imageBase64", imageBase64);

        if (existing == null) {
            data.put("createdAt", Timestamp.now()); data.put("createdBy", uid);
            data.put("status", "Active"); data.put("hearts", 0); data.put("goingCount", 0);
            db.collection("events").add(data)
                    .addOnSuccessListener(ref ->
                            ActivityLogger.logEvent(ActivityLogger.ACTION_CREATE, title, actorEmail))
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            data.put("modifiedAt", Timestamp.now()); data.put("modifiedBy", uid);
            existing.getReference().update(data)
                    .addOnSuccessListener(v ->
                            ActivityLogger.logEvent(ActivityLogger.ACTION_MODIFIED, title, actorEmail))
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class EventAdapter extends RecyclerView.Adapter<EventAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvAuthorInitials, tvCreatedBy, tvDateCreated, tvStatus,
                    tvTitle, tvDesc, tvWhere, tvDate, tvTime,
                    tvMaxPax, tvGoingCount, tvHearts,
                    tvModifiedInfo, btnEdit, btnArchive, btnDelete, btnParticipants;
            ImageView ivImage;

            VH(View v) {
                super(v);
                tvAuthorInitials = v.findViewById(R.id.tv_author_initials);
                tvCreatedBy      = v.findViewById(R.id.tv_created_by);
                tvDateCreated    = v.findViewById(R.id.tv_date_created);
                tvStatus         = v.findViewById(R.id.tv_status);
                tvTitle          = v.findViewById(R.id.tv_title);
                tvDesc           = v.findViewById(R.id.tv_description);
                tvWhere          = v.findViewById(R.id.tv_where);
                tvDate           = v.findViewById(R.id.tv_event_date);
                tvTime           = v.findViewById(R.id.tv_event_time);
                tvMaxPax         = v.findViewById(R.id.tv_max_participants);
                tvGoingCount     = v.findViewById(R.id.tv_going_count);
                tvHearts         = v.findViewById(R.id.tv_hearts);
                tvModifiedInfo   = v.findViewById(R.id.tv_modified_info);
                ivImage          = v.findViewById(R.id.iv_image);
                btnEdit          = v.findViewById(R.id.btn_edit);
                btnArchive       = v.findViewById(R.id.btn_archive);
                btnDelete        = v.findViewById(R.id.btn_delete);
                btnParticipants  = v.findViewById(R.id.btn_participants);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_event, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String status = doc.getString("status")        != null ? doc.getString("status")        : "Active";
            String title  = doc.getString("title")         != null ? doc.getString("title")         : "—";
            String desc   = doc.getString("description")   != null ? doc.getString("description")   : "—";
            String where  = doc.getString("where")         != null ? doc.getString("where")         : "—";
            String date   = doc.getString("date")          != null ? doc.getString("date")          : "—";
            String time   = doc.getString("time")          != null ? doc.getString("time")          : "—";
            String by     = doc.getString("createdBy")     != null ? doc.getString("createdBy")     : "Admin";
            long   hearts = doc.getLong("hearts")          != null ? doc.getLong("hearts")          : 0;
            long   going  = doc.getLong("goingCount")      != null ? doc.getLong("goingCount")      : 0;
            long   maxPax = doc.getLong("maxParticipants") != null ? doc.getLong("maxParticipants") : 0;
            Timestamp ts  = doc.getTimestamp("createdAt");

            h.tvTitle.setText(title);        h.tvDesc.setText(desc);
            h.tvWhere.setText("📍 " + where); h.tvDate.setText("📅 " + date);
            h.tvTime.setText("⏰ " + time);   h.tvMaxPax.setText("Max: " + maxPax);
            h.tvGoingCount.setText("Going: " + going); h.tvHearts.setText(String.valueOf(hearts));
            h.tvCreatedBy.setText(by);
            h.tvDateCreated.setText(ts != null ? sdf.format(ts.toDate()) : "—");
            h.tvStatus.setText(status);
            h.tvStatus.setBackgroundResource("Active".equals(status) ? R.drawable.badge_active : R.drawable.badge_blocked);
            h.tvAuthorInitials.setText(by.length() > 0 ? String.valueOf(by.charAt(0)).toUpperCase() : "A");

            Timestamp mod = doc.getTimestamp("modifiedAt");
            String modBy  = doc.getString("modifiedBy");
            h.tvModifiedInfo.setText((mod != null && modBy != null)
                    ? "Edited " + sdf.format(mod.toDate()) + " by " + modBy : "");

            String base64 = doc.getString("imageBase64");
            if (base64 != null && !base64.isEmpty()) loadBase64Image(base64, h.ivImage);
            else h.ivImage.setVisibility(View.GONE);

            h.btnEdit.setOnClickListener(v -> showEventDialog(doc));

            h.btnArchive.setOnClickListener(v -> {
                String ns = "Archived".equals(status) ? "Active" : "Archived";
                h.btnArchive.setText("Archived".equals(status) ? "Archive" : "Unarchive");
                String actorEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "unknown";
                doc.getReference().update("status", ns, "modifiedAt", Timestamp.now(),
                                "modifiedBy", FirebaseAuth.getInstance().getUid())
                        .addOnSuccessListener(unused ->
                                ActivityLogger.logEvent(ActivityLogger.ACTION_MODIFIED,
                                        title + " → " + ns, actorEmail));
            });

            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Event")
                            .setMessage("Delete \"" + title + "\"? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) -> {
                                String actorEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "unknown";
                                doc.getReference().delete()
                                        .addOnSuccessListener(unused ->
                                                ActivityLogger.logEvent(
                                                        ActivityLogger.ACTION_DELETE, title, actorEmail));
                            })
                            .setNegativeButton("Cancel", null).show());

            h.btnParticipants.setOnClickListener(v -> {
                Intent i = new Intent(getActivity(), EventParticipantsActivity.class);
                i.putExtra("eventId", doc.getId());
                i.putExtra("eventTitle", title);
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}