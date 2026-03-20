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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class CampusManagementActivity extends AppCompatActivity {

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
        b.toolbar.setTitle("Campus Management");
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

        db.collection("campuses").orderBy("name")
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
            String name = doc.getString("name") != null ? doc.getString("name").toLowerCase() : "";
            if (q.isEmpty() || name.contains(q)) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " campus(es)");
        adapter.notifyDataSetChanged();
    }

    private void showFormDialog(DocumentSnapshot existing) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_campus_form, null);
        TextInputEditText etName = form.findViewById(R.id.et_name);
        TextInputEditText etDesc = form.findViewById(R.id.et_description);

        if (existing != null) {
            etName.setText(existing.getString("name"));
            etDesc.setText(existing.getString("description"));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add Campus" : "Edit Campus")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show(); return; }
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (existing == null) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("name", name);
                        data.put("description", desc);
                        data.put("status", "Active");
                        data.put("createdAt", Timestamp.now());
                        data.put("createdBy", uid);
                        db.collection("campuses").add(data);
                    } else {
                        existing.getReference().update("name", name, "description", desc,
                                "modifiedAt", Timestamp.now(), "modifiedBy", uid);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
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
            String status = doc.getString("status") != null ? doc.getString("status") : "Active";

            h.tvName.setText(orDash(doc.getString("name")));
            h.tvDesc.setText(orDash(doc.getString("description")));
            h.tvStatus.setText(status);
            h.tvAcronym.setVisibility(View.GONE);

            if ("Active".equals(status)) {
                h.tvStatus.setBackgroundResource(R.drawable.badge_active);
                h.btnToggle.setText("Disable");
            } else {
                h.tvStatus.setBackgroundResource(R.drawable.badge_blocked);
                h.btnToggle.setText("Enable");
            }

            Timestamp c = doc.getTimestamp("createdAt"), m = doc.getTimestamp("modifiedAt");
            h.tvDateCreated.setText(c != null ? sdf.format(c.toDate()) : "—");
            h.tvDateModified.setText(m != null ? sdf.format(m.toDate()) : "—");
            h.tvCreatedBy.setText(orDash(doc.getString("createdBy")));
            h.tvModifiedBy.setText(orDash(doc.getString("modifiedBy")));

            h.btnEdit.setOnClickListener(v -> showFormDialog(doc));

            h.btnToggle.setOnClickListener(v -> {
                String ns = "Active".equals(status) ? "Inactive" : "Active";
                doc.getReference().update("status", ns, "modifiedAt", Timestamp.now(),
                        "modifiedBy", FirebaseAuth.getInstance().getUid());
            });

            h.btnDelete.setOnClickListener(v ->
                    new MaterialAlertDialogBuilder(CampusManagementActivity.this)
                            .setTitle("Delete Campus")
                            .setMessage("Delete " + doc.getString("name") + "? This cannot be undone.")
                            .setPositiveButton("Delete", (d, w) -> doc.getReference().delete())
                            .setNegativeButton("Cancel", null).show());
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private String orDash(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }
}
