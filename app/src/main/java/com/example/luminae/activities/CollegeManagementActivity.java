package com.example.luminae.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.luminae.R;
import com.example.luminae.databinding.ActivityGenericManagementBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CollegeManagementActivity extends AppCompatActivity {

    private ActivityGenericManagementBinding b;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> all = new ArrayList<>(), filtered = new ArrayList<>();
    private CrudAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityGenericManagementBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        getSupportActionBar().setTitle("College Management");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new CrudAdapter();
        b.recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerItems.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });

        b.btnAdd.setOnClickListener(v -> showFormDialog(null));

        db.collection("colleges").orderBy("name")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    all = snap.getDocuments();
                    applyFilter();
                });
    }

    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            String name    = doc.getString("name")    != null ? doc.getString("name").toLowerCase()    : "";
            String acronym = doc.getString("acronym") != null ? doc.getString("acronym").toLowerCase() : "";
            if (q.isEmpty() || name.contains(q) || acronym.contains(q)) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " college(s)");
        adapter.notifyDataSetChanged();
    }

    private void showFormDialog(DocumentSnapshot existing) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        EditText etName    = new EditText(this); etName.setHint("College Name");
        EditText etAcronym = new EditText(this); etAcronym.setHint("Acronym (e.g. CITE)");
        EditText etDesc    = new EditText(this); etDesc.setHint("Description"); etDesc.setMinLines(2);

        if (existing != null) {
            etName.setText(existing.getString("name"));
            etAcronym.setText(existing.getString("acronym"));
            etDesc.setText(existing.getString("description"));
        }
        layout.addView(etName);
        layout.addView(etAcronym);
        layout.addView(etDesc);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add College" : "Edit College")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String name    = etName.getText().toString().trim();
                    String acronym = etAcronym.getText().toString().trim();
                    String desc    = etDesc.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Subject for log = acronym (fallback to name if blank)
                    String logSubject = !acronym.isEmpty() ? acronym : name;
                    String uid = FirebaseAuth.getInstance().getUid();

                    if (existing == null) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("name", name); data.put("acronym", acronym);
                        data.put("description", desc); data.put("status", "Active");
                        data.put("createdAt", Timestamp.now()); data.put("createdBy", uid);
                        db.collection("colleges").add(data)
                                .addOnSuccessListener(ref ->
                                        ActivityLogger.logCollege(ActivityLogger.ACTION_CREATE, logSubject));
                    } else {
                        existing.getReference().update(
                                        "name", name, "acronym", acronym, "description", desc,
                                        "modifiedAt", Timestamp.now(), "modifiedBy", uid)
                                .addOnSuccessListener(v ->
                                        ActivityLogger.logCollege(ActivityLogger.ACTION_MODIFIED, logSubject));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class CrudAdapter extends RecyclerView.Adapter<CrudAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus, tvAcronym, tvDesc,
                    tvDateCreated, tvCreatedBy, tvDateModified, tvModifiedBy,
                    btnEdit, btnToggle, btnDelete;

            VH(View v) {
                super(v);
                tvName         = v.findViewById(R.id.tv_name);
                tvStatus       = v.findViewById(R.id.tv_status);
                tvAcronym      = v.findViewById(R.id.tv_acronym);
                tvDesc         = v.findViewById(R.id.tv_description);
                tvDateCreated  = v.findViewById(R.id.tv_date_created);
                tvCreatedBy    = v.findViewById(R.id.tv_created_by);
                tvDateModified = v.findViewById(R.id.tv_date_modified);
                tvModifiedBy   = v.findViewById(R.id.tv_modified_by);
                btnEdit        = v.findViewById(R.id.btn_edit);
                btnToggle      = v.findViewById(R.id.btn_toggle_status);
                btnDelete      = v.findViewById(R.id.btn_delete);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_crud_entity, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            String status  = doc.getString("status")  != null ? doc.getString("status")  : "Active";
            String acronym = doc.getString("acronym") != null ? doc.getString("acronym") : "";
            String logSubject = !acronym.isEmpty() ? acronym : orDash(doc.getString("name"));

            h.tvName.setText(orDash(doc.getString("name")));
            h.tvDesc.setText(orDash(doc.getString("description")));
            h.tvStatus.setText(status);

            if (!acronym.isEmpty()) {
                h.tvAcronym.setVisibility(View.VISIBLE);
                h.tvAcronym.setText(acronym);
            } else {
                h.tvAcronym.setVisibility(View.GONE);
            }

            h.tvStatus.setBackgroundResource(
                    "Active".equals(status) ? R.drawable.badge_active : R.drawable.badge_blocked);
            h.btnToggle.setText("Active".equals(status) ? "Disable" : "Enable");

            Timestamp c = doc.getTimestamp("createdAt"), m = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(c != null ? sdf.format(c.toDate()) : "—");
            h.tvDateModified.setText(m != null ? sdf.format(m.toDate()) : "—");
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            h.btnEdit.setOnClickListener(v -> showFormDialog(doc));

            h.btnToggle.setOnClickListener(v -> {
                String ns = "Active".equals(status) ? "Inactive" : "Active";
                doc.getReference().update(
                                "status", ns,
                                "modifiedAt", Timestamp.now(),
                                "modifiedBy", FirebaseAuth.getInstance().getUid())
                        .addOnSuccessListener(unused ->
                                ActivityLogger.logCollege(ActivityLogger.ACTION_MODIFIED,
                                        logSubject + " → " + ns));
            });

            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(CollegeManagementActivity.this)
                            .setTitle("Delete College")
                            .setMessage("Delete " + doc.getString("name") + "?")
                            .setPositiveButton("Delete", (d, w) ->
                                    doc.getReference().delete()
                                            .addOnSuccessListener(unused ->
                                                    ActivityLogger.logCollege(
                                                            ActivityLogger.ACTION_DELETE, logSubject)))
                            .setNegativeButton("Cancel", null)
                            .show());
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private String orDash(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }
}