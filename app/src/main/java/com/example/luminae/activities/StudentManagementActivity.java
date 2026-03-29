package com.example.luminae.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityStudentManagementBinding;
import com.example.luminae.models.User;
import com.example.luminae.utils.ActivityLogger;
import com.example.luminae.utils.MailSender;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class StudentManagementActivity extends AppCompatActivity {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    // Only BulSU institutional email addresses are accepted during registration.
    private static final Pattern BULSU_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@ms\\.bulsu\\.edu\\.ph$",
            Pattern.CASE_INSENSITIVE
    );

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private ActivityStudentManagementBinding b;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Master list from Firestore, and the currently displayed subset after filtering.
    private List<DocumentSnapshot> allStudents = new ArrayList<>();
    private List<DocumentSnapshot> filtered    = new ArrayList<>();

    private StudentAdapter adapter;

    // Tracks the currently selected status chip ("All", "Active", or "Blocked").
    private String filterStatus = "All";

    // ---------------------------------------------------------------------------
    // Callback interface used to receive the actor's full name asynchronously
    // before writing to Firestore, so that modifiedBy stores a human-readable
    // name instead of a raw Firebase UID.
    // ---------------------------------------------------------------------------
    interface NameCallback {
        void onResult(String fullName);
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b    = ActivityStudentManagementBinding.inflate(getLayoutInflater());
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        setContentView(b.getRoot());

        // Set up toolbar with back navigation.
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // Set up the RecyclerView.
        adapter = new StudentAdapter();
        b.recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerStudents.setAdapter(adapter);

        // Re-filter whenever the user types in the search box.
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) {
                applyFilter();
            }
        });

        // Re-filter whenever a status chip is toggled.
        b.chipGroupStatus.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActive.isChecked())  filterStatus = "Active";
            else if (b.chipBlocked.isChecked()) filterStatus = "Blocked";
            else                                filterStatus = "All";
            applyFilter();
        });

        // Open the add-student dialog when the add button is tapped.
        b.btnAdd.setOnClickListener(v -> showAddStudentDialog());

        loadStudents();
    }

    // ---------------------------------------------------------------------------
    // Data loading
    // ---------------------------------------------------------------------------

    /**
     * Attaches a real-time Firestore listener that streams all users whose role
     * is "student", ordered alphabetically by first name.
     */
    private void loadStudents() {
        db.collection("users")
                .whereEqualTo("role", "student")
                .orderBy("fName")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    allStudents = snap.getDocuments();
                    applyFilter();
                });
    }

    // ---------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------

    /**
     * Rebuilds the filtered list by applying both the text search query (name or
     * email) and the status chip selection, then refreshes the adapter.
     */
    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : allStudents) {
            String name   = (doc.getString("fName") + " " + doc.getString("lName")).toLowerCase();
            String email  = doc.getString("email")  != null ? doc.getString("email").toLowerCase()  : "";
            String status = doc.getString("status") != null ? doc.getString("status") : "";

            boolean matchesSearch = q.isEmpty() || name.contains(q) || email.contains(q);
            boolean matchesStatus = filterStatus.equals("All") || filterStatus.equals(status);
            if (matchesSearch && matchesStatus) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " student(s) found");
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------------------
    // Add student dialog
    // ---------------------------------------------------------------------------

    /**
     * Shows the registration dialog.  On success it:
     *   1. Creates a Firebase Auth account with a randomly generated password.
     *   2. Writes the user document to Firestore.
     *   3. Logs the creation via ActivityLogger.
     *   4. Sends a welcome email containing the credentials via MailSender.
     */
    private void showAddStudentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);

        TextInputLayout   tilFirstName = dialogView.findViewById(R.id.til_first_name);
        TextInputLayout   tilLastName  = dialogView.findViewById(R.id.til_last_name);
        TextInputLayout   tilEmail     = dialogView.findViewById(R.id.til_email);
        TextInputLayout   tilCampus    = dialogView.findViewById(R.id.til_campus);
        TextInputLayout   tilCollege   = dialogView.findViewById(R.id.til_college);
        TextInputLayout   tilCourse    = dialogView.findViewById(R.id.til_course);
        TextInputEditText etFirstName  = dialogView.findViewById(R.id.et_first_name);
        TextInputEditText etLastName   = dialogView.findViewById(R.id.et_last_name);
        TextInputEditText etEmail      = dialogView.findViewById(R.id.et_email);
        TextInputEditText etCampus     = dialogView.findViewById(R.id.et_campus);
        TextInputEditText etCollege    = dialogView.findViewById(R.id.et_college);
        TextInputEditText etCourse     = dialogView.findViewById(R.id.et_course);

        // Validate the email domain inline as the user types.
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) {
                String typed = s.toString().trim();
                if (!typed.isEmpty() && !BULSU_EMAIL.matcher(typed).matches())
                    tilEmail.setError("Must be @ms.bulsu.edu.ph");
                else
                    tilEmail.setError(null);
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add Student")
                .setView(dialogView)
                .setPositiveButton("Register", null)   // listener set below to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnRegister = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            btnRegister.setOnClickListener(v -> {
                // Collect and trim all field values.
                String fName   = etFirstName.getText().toString().trim();
                String lName   = etLastName.getText().toString().trim();
                String email   = etEmail.getText().toString().trim();
                String campus  = etCampus.getText().toString().trim();
                String college = etCollege.getText().toString().trim();
                String course  = etCourse.getText().toString().trim();

                // Validate every required field before proceeding.
                boolean valid = true;
                if (fName.isEmpty())  { tilFirstName.setError("Required"); valid = false; } else tilFirstName.setError(null);
                if (lName.isEmpty())  { tilLastName.setError("Required");  valid = false; } else tilLastName.setError(null);
                if (email.isEmpty())  { tilEmail.setError("Required");     valid = false; }
                else if (!BULSU_EMAIL.matcher(email).matches()) {
                    tilEmail.setError("Must be @ms.bulsu.edu.ph");
                    valid = false;
                } else {
                    tilEmail.setError(null);
                }
                if (campus.isEmpty()) { tilCampus.setError("Required");    valid = false; } else tilCampus.setError(null);
                if (college.isEmpty()){ tilCollege.setError("Required");   valid = false; } else tilCollege.setError(null);
                if (course.isEmpty()) { tilCourse.setError("Required");    valid = false; } else tilCourse.setError(null);
                if (!valid) return;

                // Derive username from the email prefix (part before the @).
                String username = email.substring(0, email.indexOf('@'));
                String password = generatePassword(8);
                String fullName = fName + " " + lName;

                // Disable the button to prevent duplicate submissions.
                btnRegister.setEnabled(false);
                btnRegister.setText("Registering...");

                // Step 1: Create the Firebase Auth account.
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(result -> {
                            String uid = result.getUser().getUid();

                            // Step 2: Write the user document to Firestore.
                            User newUser = new User(fName, lName, username, email,
                                    campus, college, course, "student", "Active");
                            db.collection("users").document(uid).set(newUser)
                                    .addOnSuccessListener(unused -> {
                                        // Step 3: Log the registration action.
                                        ActivityLogger.logStudent(
                                                ActivityLogger.ACTION_CREATE, email);

                                        // Step 4: Send welcome email with generated credentials.
                                        MailSender.sendWelcomeEmail(email, fullName, username, password,
                                                new MailSender.Callback() {
                                                    @Override public void onSuccess() {
                                                        runOnUiThread(() -> {
                                                            dialog.dismiss();
                                                            Snackbar.make(b.getRoot(),
                                                                    fullName + " registered! Credentials sent to " + email,
                                                                    Snackbar.LENGTH_LONG).show();
                                                        });
                                                    }
                                                    @Override public void onFailure(Exception e) {
                                                        // Registration succeeded but the email failed.
                                                        // Dismiss and inform the admin so they can resend manually.
                                                        runOnUiThread(() -> {
                                                            dialog.dismiss();
                                                            Snackbar.make(b.getRoot(),
                                                                    fullName + " registered, but email failed: " + e.getMessage(),
                                                                    Snackbar.LENGTH_LONG).show();
                                                        });
                                                    }
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        // Firestore write failed; re-enable the button so the admin can retry.
                                        btnRegister.setEnabled(true);
                                        btnRegister.setText("Register");
                                        tilEmail.setError("Profile save failed: " + e.getMessage());
                                    });
                        })
                        .addOnFailureListener(e -> {
                            // Auth account creation failed (e.g., email already in use).
                            btnRegister.setEnabled(true);
                            btnRegister.setText("Register");
                            tilEmail.setError(e.getMessage());
                        });
            });
        });

        dialog.show();
    }

    // ---------------------------------------------------------------------------
    // Helper: resolve the currently logged-in user's full name from Firestore
    // ---------------------------------------------------------------------------

    /**
     * Looks up the current user's document in the "users" collection and returns
     * their concatenated fName + lName via the callback.  Falls back to "Unknown"
     * if the document cannot be retrieved.
     */
    private void getActorFullName(NameCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onResult("Unknown");
            return;
        }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String fName = doc.getString("fName") != null ? doc.getString("fName") : "";
                    String lName = doc.getString("lName") != null ? doc.getString("lName") : "";
                    String full  = (fName + " " + lName).trim();
                    callback.onResult(full.isEmpty() ? "Unknown" : full);
                })
                .addOnFailureListener(e -> callback.onResult("Unknown"));
    }

    // ---------------------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------------------

    /**
     * Generates a random alphanumeric password of the requested length.
     * This is used only during initial account creation; students are expected
     * to change it after their first login.
     */
    private String generatePassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    /** Returns the value if non-null and non-empty, otherwise an em dash. */
    private String orDash(String s) {
        return (s != null && !s.isEmpty()) ? s : "—";
    }

    /** Builds a two-letter initials string from first and last name. */
    private String initials(String f, String l) {
        String a  = f.length() > 0 ? String.valueOf(f.charAt(0)).toUpperCase() : "";
        String bb = l.length() > 0 ? String.valueOf(l.charAt(0)).toUpperCase() : "";
        return a + bb;
    }

    // ---------------------------------------------------------------------------
    // RecyclerView Adapter
    // ---------------------------------------------------------------------------

    private class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvInitials, tvFullName, tvUsername, tvStatus, tvEmail,
                    tvCampus, tvCollege, tvCourse,
                    tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                    btnToggleStatus, btnView;

            VH(View v) {
                super(v);
                tvInitials      = v.findViewById(R.id.tv_initials);
                tvFullName      = v.findViewById(R.id.tv_full_name);
                tvUsername      = v.findViewById(R.id.tv_username);
                tvStatus        = v.findViewById(R.id.tv_status);
                tvEmail         = v.findViewById(R.id.tv_email);
                tvCampus        = v.findViewById(R.id.tv_campus);
                tvCollege       = v.findViewById(R.id.tv_college);
                tvCourse        = v.findViewById(R.id.tv_course);
                tvDateCreated   = v.findViewById(R.id.tv_date_created);
                tvCreatedBy     = v.findViewById(R.id.tv_created_by);
                tvDateModified  = v.findViewById(R.id.tv_date_modified);
                tvModifiedBy    = v.findViewById(R.id.tv_modified_by);
                btnToggleStatus = v.findViewById(R.id.btn_toggle_status);
                btnView         = v.findViewById(R.id.btn_view);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

            String fName  = orDash(doc.getString("fName"));
            String lName  = orDash(doc.getString("lName"));
            String full   = fName + " " + lName;
            String email  = orDash(doc.getString("email"));
            String status = orDash(doc.getString("status"));

            // Bind basic fields.
            h.tvInitials.setText(initials(fName, lName));
            h.tvFullName.setText(full);
            h.tvUsername.setText("@" + orDash(doc.getString("username")));
            h.tvEmail.setText(email);
            h.tvCampus.setText(orDash(doc.getString("campus")));
            h.tvCollege.setText(orDash(doc.getString("college")));
            h.tvCourse.setText(orDash(doc.getString("course")));
            h.tvStatus.setText(status);

            // Style the status badge and toggle button label.
            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggleStatus.setText("Block");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggleStatus.setText("Unblock");
            }

            // Timestamps.
            Timestamp created  = doc.getTimestamp("createdAt");
            Timestamp modified = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(created  != null ? sdf.format(created.toDate())  : "—");
            h.tvDateModified.setText(modified != null ? sdf.format(modified.toDate()) : "—");

            // createdBy / modifiedBy now stores the full name.
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            // Toggle block / unblock with a confirmation dialog.
            // Resolves actor full name before writing to Firestore.
            h.btnToggleStatus.setOnClickListener(v -> {
                String newStatus = "Active".equals(status) ? "Blocked" : "Active";
                new MaterialAlertDialogBuilder(StudentManagementActivity.this)
                        .setTitle(newStatus.equals("Blocked") ? "Block Student" : "Unblock Student")
                        .setMessage("Set status to " + newStatus + " for " + full + "?")
                        .setPositiveButton("Confirm", (d, w) ->
                                getActorFullName(fullName -> {
                                    String uid = FirebaseAuth.getInstance().getUid();
                                    db.collection("users").document(doc.getId())
                                            .update(
                                                    "status",       newStatus,
                                                    "modifiedAt",   Timestamp.now(),
                                                    "modifiedBy",   fullName,   // full name, not UID
                                                    "modifiedById", uid)
                                            .addOnSuccessListener(unused ->
                                                    ActivityLogger.logStudent(
                                                            ActivityLogger.ACTION_MODIFIED,
                                                            email + " -> " + newStatus));
                                }))
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // Log a "Viewed" action when the admin expands the student card.
            h.btnView.setOnClickListener(v ->
                    ActivityLogger.logStudent(ActivityLogger.ACTION_VIEWED, email));
        }

        @Override
        public int getItemCount() { return filtered.size(); }
    }
}