package com.example.luminae.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.luminae.R;
import com.example.luminae.utils.ActivityLogger;
import com.example.luminae.utils.NotificationHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventFormActivity extends AppCompatActivity {
    public static final String EXTRA_DOC_ID = "doc_id";
    public static final String EXTRA_COLLECTION = "collection";

    private static final int MAX_POST_IMAGES = 10;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private TextInputEditText etTitle, etDescription, etLocation, etMax;
    private TextInputLayout tilTitle;
    private TextView tvDate, tvTime, tvAudienceLabel, tvError;
    private AutoCompleteTextView acvCampus, acvCollege, acvCourse;
    private TextInputLayout tilCampus, tilCollege, tilCourse;
    private View layoutAudiencePicker;
    private RecyclerView rvPhotoStrip;
    private final ArrayList<String> pendingImages = new ArrayList<>();
    private PhotoStripAdapter photoAdapter;
    private MaterialButton btnSave;
    private View progressBar;

    private String collection = "events";
    private String docId;
    private DocumentSnapshot existingDoc;

    private String pickedDate = "";
    private String pickedTime = "";
    private ActivityResultLauncher<String> pickMultipleImages;

    private final List<DocumentSnapshot> campusDocs = new ArrayList<>();
    private final List<String> campusNames = new ArrayList<>();
    private final List<DocumentSnapshot> collegeDocs = new ArrayList<>();
    private final List<String> collegeNames = new ArrayList<>();
    private final List<DocumentSnapshot> courseDocs = new ArrayList<>();
    private final List<String> courseNames = new ArrayList<>();

    private String audienceType = "All";
    private String audienceCampusId = "";
    private String audienceCollegeId = "";
    private String audienceCourseId = "";
    private String audienceLabel = "Everyone";

    private String staffCampusId = "";
    private String staffCollegeId = "";
    private String staffCourseId = "";
    private String staffCampusName = "";
    private String staffRole = "";
    private boolean isUniversityWide = false;
    private String actorName = "Unknown";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_form);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        docId = getIntent().getStringExtra(EXTRA_DOC_ID);
        String col = getIntent().getStringExtra(EXTRA_COLLECTION);
        if (col != null && !col.trim().isEmpty()) collection = col;

        bindViews();
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(docId == null ? "Create Event" : "Edit Event");
        }
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> finish());

        pickMultipleImages = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null) return;
                    for (Uri uri : uris) {
                        if (pendingImages.size() >= MAX_POST_IMAGES) break;
                        String b64 = compressToBase64(uri);
                        if (b64 != null) pendingImages.add(b64);
                    }
                    refreshPhotoStrip();
                });

        findViewById(R.id.btn_pick_image).setOnClickListener(v -> pickMultipleImages.launch("image/*"));
        findViewById(R.id.btn_pick_date).setOnClickListener(v -> pickDate());
        findViewById(R.id.btn_pick_time).setOnClickListener(v -> pickTime());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveEvent());

        loadActorAndScopeThenInit();
    }

    private void bindViews() {
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etLocation = findViewById(R.id.et_location);
        etMax = findViewById(R.id.et_max);
        tilTitle = findViewById(R.id.til_title);
        tvDate = findViewById(R.id.tv_picked_date);
        tvTime = findViewById(R.id.tv_picked_time);
        tvAudienceLabel = findViewById(R.id.tv_audience_label);
        tvError = findViewById(R.id.tv_error);
        acvCampus = findViewById(R.id.acv_campus);
        acvCollege = findViewById(R.id.acv_college);
        acvCourse = findViewById(R.id.acv_course);
        tilCampus = findViewById(R.id.til_campus);
        tilCollege = findViewById(R.id.til_college);
        tilCourse = findViewById(R.id.til_course);
        layoutAudiencePicker = findViewById(R.id.layout_audience_picker);
        rvPhotoStrip = findViewById(R.id.rv_photo_strip);
        if (rvPhotoStrip != null) {
            rvPhotoStrip.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            photoAdapter = new PhotoStripAdapter();
            rvPhotoStrip.setAdapter(photoAdapter);
        }
        btnSave = findViewById(R.id.btn_save);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void refreshPhotoStrip() {
        if (photoAdapter != null) photoAdapter.notifyDataSetChanged();
        if (rvPhotoStrip != null)
            rvPhotoStrip.setVisibility(pendingImages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private class PhotoStripAdapter extends RecyclerView.Adapter<PhotoStripAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View btnRemove;

            VH(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.thumb);
                btnRemove = itemView.findViewById(R.id.btn_remove_thumb);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_form_photo_thumb, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            showBase64Image(pendingImages.get(position), h.thumb);
            h.btnRemove.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= pendingImages.size()) return;
                pendingImages.remove(pos);
                notifyDataSetChanged();
                refreshPhotoStrip();
            });
        }

        @Override
        public int getItemCount() {
            return pendingImages.size();
        }
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            // Internal storage for eventDate (YYYY-MM-DD)
            pickedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);

            // Human‑friendly label on the button, e.g. March 21, 2026
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, y);
            chosen.set(Calendar.MONTH, m);
            chosen.set(Calendar.DAY_OF_MONTH, d);
            String display = new java.text.SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    .format(chosen.getTime());

            com.google.android.material.button.MaterialButton btn =
                    findViewById(R.id.btn_pick_date);
            if (btn != null) btn.setText(display);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this, (v, h, min) -> {
            // Internal storage for eventDate (24h time)
            pickedTime = String.format(Locale.US, "%02d:%02d", h, min);

            // Human‑friendly label on the button, e.g. 3:45 PM
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.HOUR_OF_DAY, h);
            chosen.set(Calendar.MINUTE, min);
            String display = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                    .format(chosen.getTime());

            com.google.android.material.button.MaterialButton btn =
                    findViewById(R.id.btn_pick_time);
            if (btn != null) btn.setText(display);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    private void loadActorAndScopeThenInit() {
        String uid = auth.getUid();
        if (uid == null) return;
        setLoading(true);
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String f = orEmpty(userDoc.getString("fName"));
            String l = orEmpty(userDoc.getString("lName"));
            actorName = (f + " " + l).trim().isEmpty() ? "Unknown" : (f + " " + l).trim();
            staffRole = orEmpty(userDoc.getString("role")).toLowerCase();
            staffCampusId = orEmpty(userDoc.getString("campusId"));
            staffCollegeId = orEmpty(userDoc.getString("collegeId"));
            staffCourseId = orEmpty(userDoc.getString("courseId"));
            staffCampusName = orEmpty(userDoc.getString("campusLabel"));
            if (staffCampusName.isEmpty()) staffCampusName = orEmpty(userDoc.getString("campusName"));
            if (staffCampusName.isEmpty()) staffCampusName = orEmpty(userDoc.getString("campus"));
            isUniversityWide = "admin".equals(staffRole)
                    || "university wide".equalsIgnoreCase(staffCampusName)
                    || "all".equalsIgnoreCase(staffCampusName);
            initAudiencePicker();
            if (docId != null) loadExisting();
            else setLoading(false);
        }).addOnFailureListener(e -> setLoading(false));
    }

    private void initAudiencePicker() {
        if (!staffCourseId.isEmpty()) {
            audienceType = "Course";
            audienceCampusId = staffCampusId;
            audienceCollegeId = staffCollegeId;
            audienceCourseId = staffCourseId;
            audienceLabel = "Specific course";
            tvAudienceLabel.setText("Audience: " + audienceLabel);
            layoutAudiencePicker.setVisibility(View.GONE);
            return;
        }
        if (!isUniversityWide && !staffCampusId.isEmpty()) {
            setAudienceScopeUiForAllCampuses(false);
            acvCampus.setText(staffCampusName.isEmpty() ? "Assigned Campus" : staffCampusName, false);
            tilCampus.setEnabled(false);
            acvCampus.setEnabled(false);
            audienceType = "Campus";
            audienceCampusId = staffCampusId;
            loadCollegesForCampus(staffCampusId, true,
                    staffCollegeId.isEmpty() ? null : staffCollegeId);
            return;
        }
        db.collection("campuses").whereEqualTo("status", "Active").get()
                .addOnSuccessListener(this::bindCampusChoicesAllScope);
    }

    private void bindCampusChoicesAllScope(QuerySnapshot snap) {
        campusDocs.clear();
        campusNames.clear();
        campusNames.add("All Campuses");
        for (DocumentSnapshot d : snap.getDocuments()) {
            campusDocs.add(d);
            campusNames.add(orEmpty(d.getString("name")));
        }
        acvCampus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, campusNames));
        acvCampus.setOnClickListener(v -> acvCampus.showDropDown());
        acvCampus.setOnItemClickListener((p, v, pos, id) -> {
            if (pos == 0) {
                audienceType = "All";
                audienceCampusId = "";
                audienceCollegeId = "";
                audienceCourseId = "";
                audienceLabel = "Everyone (all campuses, colleges, courses)";
                tvAudienceLabel.setText("Audience: " + audienceLabel);
                clearCollegeCourse();
                setAudienceScopeUiForAllCampuses(true);
                return;
            }
            setAudienceScopeUiForAllCampuses(false);
            DocumentSnapshot campus = campusDocs.get(pos - 1);
            audienceCampusId = campus.getId();
            audienceType = "Campus";
            audienceLabel = orEmpty(campus.getString("name"));
            audienceCollegeId = "";
            audienceCourseId = "";
            tvAudienceLabel.setText("Audience: " + audienceLabel);
            loadCollegesForCampus(audienceCampusId, true, null);
        });
    }

    private void setAudienceScopeUiForAllCampuses(boolean allCampusesUniversityWide) {
        if (tilCollege != null)
            tilCollege.setVisibility(allCampusesUniversityWide ? View.GONE : View.VISIBLE);
        if (tilCourse != null)
            tilCourse.setVisibility(allCampusesUniversityWide ? View.GONE : View.VISIBLE);
    }

    private void loadCollegesForCampus(String campusId, boolean includeAllOption,
                                     @Nullable String preSelectCollegeId) {
        db.collection("colleges").whereEqualTo("campus", campusId).whereEqualTo("status", "Active")
                .get().addOnSuccessListener(snap -> {
                    collegeDocs.clear();
                    collegeNames.clear();
                    if (includeAllOption) collegeNames.add("All Colleges");
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        collegeDocs.add(d);
                        collegeNames.add(orEmpty(d.getString("name")));
                    }
                    acvCollege.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, collegeNames));
                    acvCollege.setOnClickListener(v -> acvCollege.showDropDown());
                    acvCollege.setOnItemClickListener((p, v, pos, id) -> {
                        if (includeAllOption && pos == 0) {
                            audienceType = "Campus";
                            audienceCollegeId = "";
                            audienceCourseId = "";
                            audienceLabel = acvCampus.getText().toString().trim();
                            tvAudienceLabel.setText("Audience: " + audienceLabel);
                            clearCoursesOnly();
                            return;
                        }
                        DocumentSnapshot sel = collegeDocs.get(includeAllOption ? pos - 1 : pos);
                        audienceType = "College";
                        audienceCollegeId = sel.getId();
                        audienceCourseId = "";
                        audienceLabel = orEmpty(sel.getString("name"));
                        tvAudienceLabel.setText("Audience: " + audienceLabel);
                        loadCoursesForCollege(audienceCollegeId, true);
                    });
                    if (preSelectCollegeId != null && !preSelectCollegeId.isEmpty()) {
                        for (int i = 0; i < collegeDocs.size(); i++) {
                            if (preSelectCollegeId.equals(collegeDocs.get(i).getId())) {
                                int row = includeAllOption ? i + 1 : i;
                                acvCollege.setText(collegeNames.get(row), false);
                                audienceCollegeId = preSelectCollegeId;
                                audienceType = "College";
                                audienceLabel = orEmpty(collegeDocs.get(i).getString("name"));
                                tvAudienceLabel.setText("Audience: " + audienceLabel);
                                loadCoursesForCollege(audienceCollegeId, true);
                                break;
                            }
                        }
                    }
                });
    }

    private void loadCoursesForCollege(String collegeId, boolean includeAllOption) {
        db.collection("courses").whereEqualTo("college", collegeId)
                .get().addOnSuccessListener(snap -> {
                    courseDocs.clear();
                    courseNames.clear();
                    if (includeAllOption) courseNames.add("All Courses");
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        courseDocs.add(d);
                        courseNames.add(orEmpty(d.getString("name")));
                    }
                    acvCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, courseNames));
                    acvCourse.setOnClickListener(v -> acvCourse.showDropDown());
                    acvCourse.setOnItemClickListener((p, v, pos, id) -> {
                        if (includeAllOption && pos == 0) {
                            audienceType = "College";
                            audienceCourseId = "";
                            audienceLabel = acvCollege.getText().toString().trim();
                            tvAudienceLabel.setText("Audience: " + audienceLabel);
                            return;
                        }
                        DocumentSnapshot sel = courseDocs.get(includeAllOption ? pos - 1 : pos);
                        audienceType = "Course";
                        audienceCourseId = sel.getId();
                        audienceLabel = orEmpty(sel.getString("name"));
                        tvAudienceLabel.setText("Audience: " + audienceLabel);
                    });
                });
    }

    private void clearCollegeCourse() {
        acvCollege.setText("", false);
        acvCourse.setText("", false);
        acvCollege.setAdapter(null);
        acvCourse.setAdapter(null);
    }

    private void clearCoursesOnly() {
        acvCourse.setText("", false);
        acvCourse.setAdapter(null);
    }

    private void loadExisting() {
        db.collection(collection).document(docId).get().addOnSuccessListener(doc -> {
            existingDoc = doc;
            etTitle.setText(orEmpty(doc.getString("title")));
            etDescription.setText(orEmpty(doc.getString("description")));
            etLocation.setText(orEmpty(doc.getString("location")).isEmpty() ? orEmpty(doc.getString("where")) : orEmpty(doc.getString("location")));
            String evDate = orEmpty(doc.getString("eventDate"));
            if (evDate.isEmpty()) evDate = (orEmpty(doc.getString("date")) + " " + orEmpty(doc.getString("time"))).trim();
            if (!evDate.isEmpty()) {
                String[] parts = evDate.split(" ");
                pickedDate = parts.length > 0 ? parts[0] : "";
                pickedTime = parts.length > 1 ? parts[1] : "";
                tvDate.setText(pickedDate.isEmpty() ? "No date selected" : pickedDate);
                tvTime.setText(pickedTime.isEmpty() ? "No time selected" : pickedTime);
            }
            Long max = doc.getLong("maxParticipants");
            if (max != null) etMax.setText(String.valueOf(max));
            pendingImages.clear();
            Object rawImg = doc.get("imagesBase64");
            if (rawImg instanceof List) {
                for (Object o : (List<?>) rawImg) {
                    if (o instanceof String && !((String) o).trim().isEmpty())
                        pendingImages.add(((String) o).trim());
                }
            }
            if (pendingImages.isEmpty()) {
                String img = doc.getString("imageBase64");
                if (img != null && !img.isEmpty()) pendingImages.add(img);
            }
            refreshPhotoStrip();
            audienceType = orEmpty(doc.getString("audienceType")).isEmpty() ? "All" : doc.getString("audienceType");
            audienceCampusId = orEmpty(doc.getString("audienceCampusId"));
            audienceCollegeId = orEmpty(doc.getString("audienceCollegeId"));
            audienceCourseId = orEmpty(doc.getString("audienceCourseId"));
            audienceLabel = orEmpty(doc.getString("audienceLabel"));
            if (audienceLabel.isEmpty()) audienceLabel = "Everyone";
            tvAudienceLabel.setText("Audience: " + audienceLabel);
            if ("All".equals(audienceType)) {
                setAudienceScopeUiForAllCampuses(true);
                acvCampus.setText("All Campuses", false);
            } else {
                setAudienceScopeUiForAllCampuses(false);
            }
            setLoading(false);
        }).addOnFailureListener(e -> setLoading(false));
    }

    private void saveEvent() {
        String title = text(etTitle);
        if (title.isEmpty()) {
            tilTitle.setError("Title is required");
            return;
        }
        tilTitle.setError(null);
        String desc = text(etDescription);
        String location = text(etLocation);
        String eventDate = (pickedDate + " " + pickedTime).trim();
        long max = 0;
        try { max = Long.parseLong(text(etMax)); } catch (Exception ignored) {}

        setLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", desc);
        data.put("location", location);
        data.put("eventDate", eventDate);
        data.put("maxParticipants", max);
        data.put("audienceType", audienceType);
        data.put("audienceCampusId", audienceCampusId);
        data.put("audienceCollegeId", audienceCollegeId);
        data.put("audienceCourseId", audienceCourseId);
        data.put("audienceLabel", audienceLabel);
        if (!pendingImages.isEmpty()) {
            data.put("imagesBase64", new ArrayList<>(pendingImages));
            data.put("imageBase64", pendingImages.get(0));
        } else {
            data.put("imagesBase64", new ArrayList<>());
            data.put("imageBase64", null);
        }

        String uid = auth.getUid();
        if (uid != null) {
            data.put("postedBy", uid);
            data.put("postedByName", actorName);
            data.put("createdById", uid);
            data.put("createdBy", actorName);
        }

        if (existingDoc == null) {
            data.put("createdAt", Timestamp.now());
            data.put("status", "Active");
            data.put("likeCount", 0L);
            data.put("commentCount", 0L);
            data.put("participantCount", 0L);
            data.put("goingCount", 0L);
            db.collection(collection).add(data).addOnSuccessListener(ref -> {
                ActivityLogger.logEvent(ActivityLogger.ACTION_CREATE, title, actorName);
                NotificationHelper.notifyNewPost(
                        ref.getId(), collection, title, actorName,
                        audienceType, audienceCampusId, audienceCollegeId, audienceCourseId, uid
                );
                finish();
            }).addOnFailureListener(e -> {
                setLoading(false);
                tvError.setText(e.getMessage());
                tvError.setVisibility(View.VISIBLE);
            });
        } else {
            data.put("modifiedAt", Timestamp.now());
            data.put("modifiedBy", actorName);
            if (uid != null) data.put("modifiedById", uid);
            existingDoc.getReference().update(data).addOnSuccessListener(unused -> {
                ActivityLogger.logEvent(ActivityLogger.ACTION_MODIFIED, title, actorName);
                finish();
            }).addOnFailureListener(e -> {
                setLoading(false);
                tvError.setText(e.getMessage());
                tvError.setVisibility(View.VISIBLE);
            });
        }
    }

    private String compressToBase64(Uri uri) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            int maxWidth = 1000;
            if (original.getWidth() > maxWidth) {
                float ratio = (float) maxWidth / original.getWidth();
                original = Bitmap.createScaledBitmap(original, maxWidth, Math.round(original.getHeight() * ratio), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    private void showBase64Image(String b64, ImageView view) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            view.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            view.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean on) {
        btnSave.setEnabled(!on);
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
