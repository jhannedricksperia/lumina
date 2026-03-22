package com.example.luminae.staff.fragments;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
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
import androidx.recyclerview.widget.*;
import com.example.luminae.R;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.activities.UserProfileActivity;
import com.example.luminae.admin.fragments.ActivityLogger;
import com.example.luminae.utils.NotificationHelper;
import com.example.luminae.databinding.FragmentStaffPostBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class StaffPostFragment extends Fragment {

    private FragmentStaffPostBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FeedAdapter adapter;
    private List<FeedItem> allItems = new ArrayList<>(), filtered = new ArrayList<>();
    private String currentType  = "All";
    private String currentOrder = "desc";
    private String staffUid         = "";
    private String staffName        = "";
    private String staffDesignation = "";
    private boolean staffInfoLoaded = false;
    private String staffPhotoBase64   = "";
    private ActivityResultLauncher<String> imagePicker;
    private String pendingImageBase64 = null;
    private ImageView pendingImageView;
    private Map<String, String> pendingAudience = null;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        b        = FragmentStaffPostBinding.inflate(inflater, container, false);
        db       = FirebaseFirestore.getInstance();
        auth     = FirebaseAuth.getInstance();
        staffUid = auth.getUid() != null ? auth.getUid() : "";

        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) handleImagePicked(uri); });

        loadStaffInfo();
        setupFilters();
        loadFeed();

        b.fabPost.setOnClickListener(v -> {
            if (!staffInfoLoaded) {
                Toast.makeText(getContext(), "Loading profile, please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            showPostTypeDialog();
        });

        return b.getRoot();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Staff info
    // ─────────────────────────────────────────────────────────────────────────
    private void loadStaffInfo() {
        if (staffUid.isEmpty()) return;
        db.collection("users").document(staffUid).get().addOnSuccessListener(doc -> {
            if (doc == null || b == null) return;
            String fName = doc.getString("fName") != null ? doc.getString("fName") : "";
            String lName = doc.getString("lName") != null ? doc.getString("lName") : "";
            staffName        = (fName + " " + lName).trim();
            staffDesignation = doc.getString("designation") != null ? doc.getString("designation") : "Staff";
            if (staffName.isEmpty())
                staffName = doc.getString("email") != null ? doc.getString("email") : "Staff";
            // Cache the photo so feed cards load without extra reads
            String rawPhoto = doc.getString("photoBase64");
            staffPhotoBase64 = rawPhoto != null ? rawPhoto : "";
            staffInfoLoaded = true;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feed filters
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFilters() {
        b.chipAll.setOnClickListener(v          -> { currentType  = "All";          applyFilter(); });
        b.chipAnnouncement.setOnClickListener(v -> { currentType  = "Announcement"; applyFilter(); });
        b.chipEvent.setOnClickListener(v        -> { currentType  = "Event";        applyFilter(); });
        b.chipDesc.setOnClickListener(v         -> { currentOrder = "desc";         applyFilter(); });
        b.chipAsc.setOnClickListener(v          -> { currentOrder = "asc";          applyFilter(); });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feed loading
    // ─────────────────────────────────────────────────────────────────────────
    private void loadFeed() {
        b.progressFeed.setVisibility(View.VISIBLE);
        final int[] done = {0};

        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (int j = allItems.size() - 1; j >= 0; j--)
                        if ("Announcement".equals(allItems.get(j).type)) allItems.remove(j);
                    for (DocumentSnapshot doc : snap.getDocuments())
                        allItems.add(docToItem(doc, "Announcement"));
                    if (++done[0] >= 2) { b.progressFeed.setVisibility(View.GONE); applyFilter(); }
                });

        db.collection("events").orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (int j = allItems.size() - 1; j >= 0; j--)
                        if ("Event".equals(allItems.get(j).type)) allItems.remove(j);
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        FeedItem f = docToItem(doc, "Event");
                        f.location         = doc.getString("location")  != null ? doc.getString("location")  : "";
                        f.eventDate        = doc.getString("eventDate") != null ? doc.getString("eventDate") : "";
                        Long max = doc.getLong("maxParticipants");
                        Long cnt = doc.getLong("participantCount");
                        f.maxParticipants  = max != null ? max : 0;
                        f.participantCount = cnt != null ? cnt : 0;
                        allItems.add(f);
                    }
                    if (++done[0] >= 2) { b.progressFeed.setVisibility(View.GONE); applyFilter(); }
                });
    }

    private FeedItem docToItem(DocumentSnapshot doc, String type) {
        FeedItem f     = new FeedItem();
        f.docId        = doc.getId();
        f.type         = type;
        f.title        = doc.getString("title")        != null ? doc.getString("title")        : "";
        f.description  = doc.getString("description")  != null ? doc.getString("description")  : "";
        f.postedBy     = doc.getString("postedBy")     != null ? doc.getString("postedBy")     : "";
        f.postedByName = doc.getString("postedByName") != null ? doc.getString("postedByName") : "";
        f.audienceLabel= doc.getString("audienceLabel")!= null ? doc.getString("audienceLabel"): "Everyone";
        f.postedByPhoto = doc.getString("postedByPhoto");
        Timestamp ts   = doc.getTimestamp("createdAt");
        f.createdAt    = ts != null ? ts.toDate() : new Date(0);
        Long l = doc.getLong("likeCount");
        Long c = doc.getLong("commentCount");
        f.likeCount    = l != null ? l.intValue() : 0;
        f.commentCount = c != null ? c.intValue() : 0;
        f.isMyPost     = staffUid.equals(f.postedBy);
        return f;
    }

    private void applyFilter() {
        filtered.clear();
        for (FeedItem item : allItems)
            if (currentType.equals("All") || currentType.equals(item.type)) filtered.add(item);
        filtered.sort((a, c) -> {
            if (a.createdAt == null || c.createdAt == null) return 0;
            return "desc".equals(currentOrder)
                    ? c.createdAt.compareTo(a.createdAt)
                    : a.createdAt.compareTo(c.createdAt);
        });
        if (adapter == null) {
            adapter = new FeedAdapter();
            b.recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
            b.recyclerFeed.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post creation dialogs
    // ─────────────────────────────────────────────────────────────────────────
    private void showPostTypeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create Post")
                .setItems(new String[]{"Announcement", "Event"}, (d, which) -> {
                    if (which == 0) showAnnouncementDialog();
                    else            showEventDialog();
                }).show();
    }

    private void showAnnouncementDialog() {
        View form = LayoutInflater.from(getContext()).inflate(R.layout.dialog_post_announcement, null);
        TextInputEditText etTitle    = form.findViewById(R.id.et_title);
        TextInputEditText etDesc     = form.findViewById(R.id.et_description);
        ImageView         ivPreview  = form.findViewById(R.id.iv_preview);
        TextView          btnPickImg = form.findViewById(R.id.btn_pick_image);
        TextView          tvAudience = form.findViewById(R.id.tv_audience_label);
        View              btnPickAud = form.findViewById(R.id.btn_pick_audience);

        pendingImageBase64 = null;
        pendingImageView   = ivPreview;
        pendingAudience    = null;

        if (tvAudience  != null) tvAudience.setText("👥 Audience: Everyone");
        if (btnPickImg  != null) btnPickImg.setOnClickListener(v -> imagePicker.launch("image/*"));
        if (btnPickAud  != null) btnPickAud.setOnClickListener(v ->
                showAudiencePicker(selected -> {
                    pendingAudience = selected;
                    if (tvAudience != null)
                        tvAudience.setText("👥 Audience: " + selected.get("audienceLabel"));
                }));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Announcement")
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
                    String desc  = etDesc.getText()  != null ? etDesc.getText().toString().trim()  : "";
                    if (title.isEmpty()) {
                        Toast.makeText(getContext(), "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    postAnnouncement(title, desc, pendingImageBase64, pendingAudience);
                    pendingImageBase64 = null;
                    pendingAudience    = null;
                })
                .setNegativeButton("Cancel", (d, w) -> { pendingImageBase64 = null; pendingAudience = null; })
                .show();
    }

    private void showEventDialog() {
        View form = LayoutInflater.from(getContext()).inflate(R.layout.dialog_post_event, null);
        TextInputEditText etTitle    = form.findViewById(R.id.et_title);
        TextInputEditText etDesc     = form.findViewById(R.id.et_description);
        TextInputEditText etLocation = form.findViewById(R.id.et_where);
        TextInputEditText etMax      = form.findViewById(R.id.et_max_participants);
        ImageView         ivPreview  = form.findViewById(R.id.iv_preview);
        TextView          btnPickImg = form.findViewById(R.id.btn_pick_image);
        Button            btnPickDate= form.findViewById(R.id.btn_pick_date);
        Button            btnPickTime= form.findViewById(R.id.btn_pick_time);
        TextView          tvPickedDate = form.findViewById(R.id.tv_picked_date);
        TextView          tvPickedTime = form.findViewById(R.id.tv_picked_time);
        TextView          tvAudience   = form.findViewById(R.id.tv_audience_label);
        View              btnPickAud   = form.findViewById(R.id.btn_pick_audience);

        pendingImageBase64 = null;
        pendingImageView   = ivPreview;
        pendingAudience    = null;

        if (tvAudience != null) tvAudience.setText("👥 Audience: Everyone");

        final String[] pickedDate = {""};
        final String[] pickedTime = {""};

        if (btnPickImg  != null) btnPickImg.setOnClickListener(v -> imagePicker.launch("image/*"));
        if (btnPickAud  != null) btnPickAud.setOnClickListener(v ->
                showAudiencePicker(selected -> {
                    pendingAudience = selected;
                    if (tvAudience != null)
                        tvAudience.setText("👥 Audience: " + selected.get("audienceLabel"));
                }));
        if (btnPickDate != null) btnPickDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                pickedDate[0] = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
                if (tvPickedDate != null) tvPickedDate.setText(pickedDate[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        if (btnPickTime != null) btnPickTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new TimePickerDialog(requireContext(), (view, hour, minute) -> {
                pickedTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                if (tvPickedTime != null) tvPickedTime.setText(pickedTime[0]);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Event")
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    String title    = etTitle.getText()    != null ? etTitle.getText().toString().trim()    : "";
                    String desc     = etDesc.getText()     != null ? etDesc.getText().toString().trim()     : "";
                    String location = etLocation != null && etLocation.getText() != null
                            ? etLocation.getText().toString().trim() : "";
                    String date     = (pickedDate[0] + " " + pickedTime[0]).trim();
                    String maxStr   = etMax != null && etMax.getText() != null
                            ? etMax.getText().toString().trim() : "0";
                    if (title.isEmpty()) {
                        Toast.makeText(getContext(), "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long max = 0;
                    try { max = Long.parseLong(maxStr); } catch (NumberFormatException ignored) {}
                    postEvent(title, desc, location, date, max, pendingImageBase64, pendingAudience);
                    pendingImageBase64 = null;
                    pendingAudience    = null;
                })
                .setNegativeButton("Cancel", (d, w) -> { pendingImageBase64 = null; pendingAudience = null; })
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audience Picker  (Campus → College → Course cascade from Firestore)
    // ─────────────────────────────────────────────────────────────────────────
    interface AudienceCallback { void onSelected(Map<String, String> audience); }

    private void showAudiencePicker(AudienceCallback callback) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_audience_picker, null);

        Spinner     spinnerCampus  = view.findViewById(R.id.spinner_campus);
        Spinner     spinnerCollege = view.findViewById(R.id.spinner_college);
        Spinner     spinnerCourse  = view.findViewById(R.id.spinner_course);
        View        labelCollege   = view.findViewById(R.id.label_college);
        View        labelCourse    = view.findViewById(R.id.label_course);
        ProgressBar progress       = view.findViewById(R.id.progress_audience);

        List<String> campusIds    = new ArrayList<>();
        List<String> campusNames  = new ArrayList<>();
        List<String> collegeIds   = new ArrayList<>();
        List<String> collegeNames = new ArrayList<>();
        List<String> courseIds    = new ArrayList<>();
        List<String> courseNames  = new ArrayList<>();

        java.util.function.Function<List<String>, ArrayAdapter<String>> makeAdapter = names -> {
            ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, names);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return a;
        };

        progress.setVisibility(View.VISIBLE);
        db.collection("campuses").orderBy("name").get().addOnSuccessListener(snap -> {
            progress.setVisibility(View.GONE);
            campusIds.add("all");
            campusNames.add("Everyone (all campuses)");
            for (DocumentSnapshot doc : snap.getDocuments()) {
                campusIds.add(doc.getId());
                String n = doc.getString("name");
                campusNames.add(n != null ? n : doc.getId());
            }
            spinnerCampus.setAdapter(makeAdapter.apply(campusNames));

            spinnerCampus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> p) {}
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    spinnerCollege.setAdapter(null);
                    spinnerCourse.setAdapter(null);
                    collegeIds.clear(); collegeNames.clear();
                    courseIds.clear();  courseNames.clear();
                    labelCollege.setVisibility(View.GONE);
                    spinnerCollege.setVisibility(View.GONE);
                    labelCourse.setVisibility(View.GONE);
                    spinnerCourse.setVisibility(View.GONE);
                    if (pos == 0) return;

                    String campusId = campusIds.get(pos);
                    progress.setVisibility(View.VISIBLE);
                    db.collection("colleges").whereEqualTo("campusId", campusId).orderBy("name")
                            .get().addOnSuccessListener(colSnap -> {
                                progress.setVisibility(View.GONE);
                                if (colSnap.isEmpty()) return;
                                collegeIds.add("all_" + campusId);
                                collegeNames.add("All colleges in this campus");
                                for (DocumentSnapshot doc : colSnap.getDocuments()) {
                                    collegeIds.add(doc.getId());
                                    String n = doc.getString("name");
                                    collegeNames.add(n != null ? n : doc.getId());
                                }
                                spinnerCollege.setAdapter(makeAdapter.apply(collegeNames));
                                labelCollege.setVisibility(View.VISIBLE);
                                spinnerCollege.setVisibility(View.VISIBLE);

                                spinnerCollege.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                    @Override public void onNothingSelected(AdapterView<?> p) {}
                                    @Override public void onItemSelected(AdapterView<?> p, View v, int pos2, long id2) {
                                        spinnerCourse.setAdapter(null);
                                        courseIds.clear(); courseNames.clear();
                                        labelCourse.setVisibility(View.GONE);
                                        spinnerCourse.setVisibility(View.GONE);
                                        if (pos2 == 0) return;

                                        String collegeId = collegeIds.get(pos2);
                                        progress.setVisibility(View.VISIBLE);
                                        db.collection("courses").whereEqualTo("collegeId", collegeId).orderBy("name")
                                                .get().addOnSuccessListener(crsSnap -> {
                                                    progress.setVisibility(View.GONE);
                                                    if (crsSnap.isEmpty()) return;
                                                    courseIds.add("all_" + collegeId);
                                                    courseNames.add("All courses in this college");
                                                    for (DocumentSnapshot doc : crsSnap.getDocuments()) {
                                                        courseIds.add(doc.getId());
                                                        String n = doc.getString("name");
                                                        courseNames.add(n != null ? n : doc.getId());
                                                    }
                                                    spinnerCourse.setAdapter(makeAdapter.apply(courseNames));
                                                    labelCourse.setVisibility(View.VISIBLE);
                                                    spinnerCourse.setVisibility(View.VISIBLE);
                                                });
                                    }
                                });
                            });
                }
            });
        }).addOnFailureListener(e -> {
            progress.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Failed to load campuses", Toast.LENGTH_SHORT).show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Audience")
                .setView(view)
                .setPositiveButton("Confirm", (d, w) -> {
                    Map<String, String> result = new HashMap<>();
                    int ci = spinnerCampus.getSelectedItemPosition();
                    if (ci <= 0 || campusIds.isEmpty()) {
                        result.put("type", "All");
                        result.put("audienceLabel", "Everyone");
                        callback.onSelected(result);
                        return;
                    }
                    result.put("campusId",   campusIds.get(ci));
                    result.put("campusName", campusNames.get(ci));

                    int coli = spinnerCollege.getSelectedItemPosition();
                    if (spinnerCollege.getVisibility() != View.VISIBLE || coli <= 0 || collegeIds.isEmpty()) {
                        result.put("type", "Campus");
                        result.put("audienceLabel", campusNames.get(ci));
                        callback.onSelected(result);
                        return;
                    }
                    result.put("collegeId",   collegeIds.get(coli));
                    result.put("collegeName", collegeNames.get(coli));

                    int crsi = spinnerCourse.getSelectedItemPosition();
                    if (spinnerCourse.getVisibility() != View.VISIBLE || crsi <= 0 || courseIds.isEmpty()) {
                        result.put("type", "College");
                        result.put("audienceLabel", campusNames.get(ci) + " › " + collegeNames.get(coli));
                        callback.onSelected(result);
                        return;
                    }
                    result.put("courseId",   courseIds.get(crsi));
                    result.put("courseName", courseNames.get(crsi));
                    result.put("type", "Course");
                    result.put("audienceLabel",
                            campusNames.get(ci) + " › " + collegeNames.get(coli) + " › " + courseNames.get(crsi));
                    callback.onSelected(result);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Firestore writes
    // ─────────────────────────────────────────────────────────────────────────
    private void postAnnouncement(String title, String desc,
                                  String imgBase64, Map<String, String> audience) {
        Map<String, Object> data = new HashMap<>();
        data.put("title",               title);
        data.put("description",         desc);
        data.put("postedBy",            staffUid);
        data.put("postedByName",        staffName);
        data.put("postedByDesignation", staffDesignation);
        if (!staffPhotoBase64.isEmpty()) data.put("postedByPhoto", staffPhotoBase64);
        data.put("createdAt",           Timestamp.now());
        data.put("likeCount",           0L);
        data.put("commentCount",        0L);
        data.put("status",              "Active");
        applyAudienceToData(data, audience);
        if (imgBase64 != null) data.put("imageBase64", imgBase64);
        db.collection("announcements").add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(getContext(), "Announcement posted!", Toast.LENGTH_SHORT).show();
                    ActivityLogger.logAnnouncement(ActivityLogger.ACTION_CREATE, title, staffName);
                    // Notify audience via push + in-app
                    String campusId  = audience != null ? audience.getOrDefault("campusId",  "") : "";
                    String collegeId = audience != null ? audience.getOrDefault("collegeId", "") : "";
                    String courseId  = audience != null ? audience.getOrDefault("courseId",  "") : "";
                    String audType   = audience != null ? audience.getOrDefault("type", "All") : "All";
                    NotificationHelper.notifyNewPost(
                            ref.getId(), "announcements", title, staffName,
                            audType, campusId, collegeId, courseId, staffUid);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void postEvent(String title, String desc, String location,
                           String eventDate, long maxPart,
                           String imgBase64, Map<String, String> audience) {
        Map<String, Object> data = new HashMap<>();
        data.put("title",               title);
        data.put("description",         desc);
        data.put("location",            location);
        data.put("eventDate",           eventDate);
        data.put("maxParticipants",     maxPart);
        data.put("participantCount",    0L);
        data.put("postedBy",            staffUid);
        data.put("postedByName",        staffName);
        data.put("postedByDesignation", staffDesignation);
        if (!staffPhotoBase64.isEmpty()) data.put("postedByPhoto", staffPhotoBase64);
        data.put("createdAt",           Timestamp.now());
        data.put("likeCount",           0L);
        data.put("commentCount",        0L);
        data.put("status",              "Active");
        applyAudienceToData(data, audience);
        if (imgBase64 != null) data.put("imageBase64", imgBase64);
        db.collection("events").add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(getContext(), "Event posted!", Toast.LENGTH_SHORT).show();
                    ActivityLogger.logEvent(ActivityLogger.ACTION_CREATE, title, staffName);
                    // Notify audience via push + in-app
                    String campusId  = audience != null ? audience.getOrDefault("campusId",  "") : "";
                    String collegeId = audience != null ? audience.getOrDefault("collegeId", "") : "";
                    String courseId  = audience != null ? audience.getOrDefault("courseId",  "") : "";
                    String audType   = audience != null ? audience.getOrDefault("type", "All") : "All";
                    NotificationHelper.notifyNewPost(
                            ref.getId(), "events", title, staffName,
                            audType, campusId, collegeId, courseId, staffUid);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void applyAudienceToData(Map<String, Object> data, Map<String, String> audience) {
        if (audience == null || audience.isEmpty()) {
            data.put("audienceType",  "All");
            data.put("audienceLabel", "Everyone");
            return;
        }
        data.put("audienceType",  audience.containsKey("type")          ? audience.get("type")          : "All");
        data.put("audienceLabel", audience.containsKey("audienceLabel")  ? audience.get("audienceLabel") : "Everyone");
        if (audience.containsKey("campusId"))  data.put("audienceCampusId",  audience.get("campusId"));
        if (audience.containsKey("collegeId")) data.put("audienceCollegeId", audience.get("collegeId"));
        if (audience.containsKey("courseId"))  data.put("audienceCourseId",  audience.get("courseId"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image picker
    // ─────────────────────────────────────────────────────────────────────────
    private void handleImagePicked(Uri uri) {
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
            original.compress(Bitmap.CompressFormat.JPEG, 75, out);
            pendingImageBase64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
            if (pendingImageView != null) {
                pendingImageView.setVisibility(View.VISIBLE);
                pendingImageView.setImageBitmap(original);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data model
    // ─────────────────────────────────────────────────────────────────────────
    static class FeedItem {
        String  docId, type, title, description, postedBy, postedByName, postedByPhoto;
        String  location, eventDate, audienceLabel;
        long    maxParticipants, participantCount;
        Date    createdAt;
        int     likeCount, commentCount;
        boolean isMyPost;
        boolean likedByMe = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView adapter
    // ─────────────────────────────────────────────────────────────────────────
    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView  tvTypeBadge, tvPosterName, tvTime, tvTitle, tvDescription;
            TextView  tvLikeCount, tvCommentCount, tvGoingInfo, tvMyPostBadge, tvAudience;
            TextView  btnLike;   // R.id.btn_like is a TextView in the layout
            ImageView ivPosterPhoto;
            View      cardRoot, btnComment, btnDelete;

            VH(View v) {
                super(v);
                cardRoot       = v.findViewById(R.id.card_root);
                tvTypeBadge    = v.findViewById(R.id.tv_type_badge);
                ivPosterPhoto  = v.findViewById(R.id.iv_poster_photo);
                tvPosterName   = v.findViewById(R.id.tv_poster_name);
                tvTime         = v.findViewById(R.id.tv_time);
                tvTitle        = v.findViewById(R.id.tv_title);
                tvDescription  = v.findViewById(R.id.tv_description);
                tvLikeCount    = v.findViewById(R.id.btn_like);   // same view as btnLike
                btnLike        = v.findViewById(R.id.btn_like);
                tvCommentCount = v.findViewById(R.id.btn_comment);
                btnComment     = v.findViewById(R.id.btn_comment);
                tvGoingInfo    = v.findViewById(R.id.tv_going_info);
                tvMyPostBadge  = v.findViewById(R.id.tv_my_post_badge);
                tvAudience     = v.findViewById(R.id.tv_audience);
                btnDelete      = v.findViewById(R.id.btn_delete);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_feed_post, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            FeedItem item = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d  •  h:mm a", Locale.getDefault());

            h.tvTypeBadge.setText(item.type);
            h.tvTypeBadge.setBackgroundResource(
                    "Announcement".equals(item.type) ? R.drawable.badge_announcement : R.drawable.badge_event);
            h.tvPosterName.setText(item.postedByName);
            loadProfilePhoto(h.ivPosterPhoto, item.postedByPhoto, item.postedBy);
            h.tvTime.setText(item.createdAt != null ? sdf.format(item.createdAt) : "");
            h.tvTitle.setText(item.title);
            h.tvDescription.setText(item.description);

            // ── Like: check Firestore on bind so likedByMe is always accurate ──
            String uid  = auth.getUid();
            String col  = "Announcement".equals(item.type) ? "announcements" : "events";
            DocumentReference postRef = db.collection(col).document(item.docId);
            DocumentReference likeRef = uid != null
                    ? postRef.collection("likes").document(uid) : null;

            if (likeRef != null) {
                likeRef.get().addOnSuccessListener(likeDoc -> {
                    item.likedByMe = likeDoc.exists();
                    updateLikeUI(h, item);
                });
            }
            updateLikeUI(h, item);   // immediate render with cached value

            if (h.btnLike != null) {
                h.btnLike.setOnClickListener(v -> {
                    if (uid == null || likeRef == null) return;
                    // Always read current state from Firestore to prevent double-like
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        if (likeDoc.exists()) {
                            // Unlike
                            likeRef.delete();
                            postRef.update("likeCount", FieldValue.increment(-1));
                            item.likedByMe = false;
                            item.likeCount = Math.max(0, item.likeCount - 1);
                        } else {
                            // Like
                            Map<String, Object> likeData = new HashMap<>();
                            likeData.put("uid",     uid);
                            likeData.put("likedAt", Timestamp.now());
                            likeRef.set(likeData);
                            postRef.update("likeCount", FieldValue.increment(1));
                            item.likedByMe = true;
                            item.likeCount++;
                            // Audit log
                            ActivityLogger.log(
                                    "Announcement".equals(item.type)
                                            ? ActivityLogger.MODULE_ANNOUNCEMENT
                                            : ActivityLogger.MODULE_EVENT,
                                    "Liked", item.title);
                            // Notify poster
                            NotificationHelper.notifyLike(
                                    item.postedBy, staffName, item.title,
                                    item.docId,
                                    "Announcement".equals(item.type) ? "announcements" : "events");
                        }
                        updateLikeUI(h, item);
                    });
                });
            }

            // Comment count
            if (h.tvCommentCount != null) h.tvCommentCount.setText("💬 " + item.commentCount);

            // Going info (events only)
            if ("Event".equals(item.type)) {
                if (h.tvGoingInfo != null) {
                    h.tvGoingInfo.setVisibility(View.VISIBLE);
                    String going = item.maxParticipants > 0
                            ? item.participantCount + " / " + item.maxParticipants + " going"
                            : item.participantCount + " going";
                    h.tvGoingInfo.setText("🎫 " + going);
                }
            } else {
                if (h.tvGoingInfo != null) h.tvGoingInfo.setVisibility(View.GONE);
            }

            // Audience label
            if (h.tvAudience != null) {
                String label = item.audienceLabel != null && !item.audienceLabel.isEmpty()
                        ? item.audienceLabel : "Everyone";
                h.tvAudience.setText("👥 " + label);
            }

            // My post badge + delete button
            if (h.tvMyPostBadge != null) h.tvMyPostBadge.setVisibility(item.isMyPost ? View.VISIBLE : View.GONE);
            if (h.btnDelete     != null) h.btnDelete.setVisibility(item.isMyPost ? View.VISIBLE : View.GONE);

            // Poster photo + name → open UserProfileActivity
            View.OnClickListener openProfile = v -> openUserProfile(item.postedBy);
            if (h.ivPosterPhoto != null) h.ivPosterPhoto.setOnClickListener(openProfile);
            if (h.tvPosterName  != null) h.tvPosterName.setOnClickListener(openProfile);

            // Comment button → open PostDetailActivity (same as card click)
            if (h.btnComment != null) {
                h.btnComment.setOnClickListener(v -> openDetail(item));
            }

            // Card click → detail
            h.cardRoot.setOnClickListener(v -> openDetail(item));

            // Delete
            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v ->
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete Post?")
                                .setMessage("This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) -> {
                                    String type = "Announcement".equals(item.type) ? "announcements" : "events";
                                    db.collection(type).document(item.docId).delete();
                                    ActivityLogger.log(
                                            "Announcement".equals(item.type)
                                                    ? ActivityLogger.MODULE_ANNOUNCEMENT
                                                    : ActivityLogger.MODULE_EVENT,
                                            ActivityLogger.ACTION_DELETE, item.title);
                                })
                                .setNegativeButton("Cancel", null)
                                .show());
            }
        }

        private void openDetail(FeedItem item) {
            Intent i = new Intent(getActivity(), PostDetailActivity.class);
            i.putExtra("docId", item.docId);
            i.putExtra("type",  item.type);
            startActivity(i);
        }

        private void openUserProfile(String uid) {
            if (uid == null || uid.isEmpty()) return;
            Intent i = new Intent(getActivity(), UserProfileActivity.class);
            i.putExtra("uid", uid);
            startActivity(i);
        }

        private void loadProfilePhoto(android.widget.ImageView iv, String photoBase64, String uid) {
            // First try the cached field on the post document (fast, no extra read)
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(photoBase64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) { iv.setImageBitmap(bmp); return; }
                } catch (Exception ignored) {}
            }
            // Fallback: fetch from users collection
            if (uid == null || uid.isEmpty()) return;
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                String b64 = doc.getString("photoBase64");
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) iv.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                }
            });
        }

        private void updateLikeUI(VH h, FeedItem item) {
            if (h.tvLikeCount == null) return;
            h.tvLikeCount.setText("♥ " + item.likeCount);
            int color = item.likedByMe ? 0xFFEF5350 : 0x88FFFFFF;
            h.tvLikeCount.setTextColor(color);
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}