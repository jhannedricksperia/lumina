package com.example.luminae.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.luminae.R;
import com.example.luminae.databinding.ActivityActivityLogBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ActivityLogActivity extends AppCompatActivity {

    private static final int REQUEST_WRITE_STORAGE = 101;

    private ActivityActivityLogBinding b;
    private FirebaseFirestore db;

    private List<DocumentSnapshot> all      = new ArrayList<>();
    private List<DocumentSnapshot> filtered = new ArrayList<>();
    private LogAdapter adapter;

    private String filterModule = "All";
    private String filterAction = "All";

    private String filterByEmail = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityActivityLogBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();
        filterByEmail = getIntent().getStringExtra("filterByEmail");
        if (filterByEmail != null) {
            getSupportActionBar().setTitle("My Activity Log");
        }

        setSupportActionBar(b.toolbar);
        getSupportActionBar().setTitle("Activity Log");
        b.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new LogAdapter();
        b.recyclerLogs.setLayoutManager(new LinearLayoutManager(this));
        b.recyclerLogs.setAdapter(adapter);

        // Search
        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bc, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int bc, int c) { applyFilter(); }
        });

        // Module chips
        b.chipGroupModule.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipModuleStudent.isChecked())      filterModule = "Student";
            else if (b.chipModuleStaff.isChecked())        filterModule = "Staff";
            else if (b.chipModuleCampus.isChecked())       filterModule = "Campus";
            else if (b.chipModuleCollege.isChecked())      filterModule = "College";
            else if (b.chipModuleCourse.isChecked())       filterModule = "Course";
            else if (b.chipModuleAnnouncement.isChecked()) filterModule = "Announcement";
            else if (b.chipModuleEvent.isChecked())        filterModule = "Event";
            else                                           filterModule = "All";
            applyFilter();
        });

        // Action chips
        b.chipGroupAction.setOnCheckedStateChangeListener((g, ids) -> {
            if      (b.chipActionCreate.isChecked())   filterAction = "Create";
            else if (b.chipActionModified.isChecked()) filterAction = "Modified";
            else if (b.chipActionDelete.isChecked())   filterAction = "Delete";
            else if (b.chipActionViewed.isChecked())   filterAction = "Viewed";
            else                                       filterAction = "All";
            applyFilter();
        });

        // Export PDF button
        b.btnExportPdf.setOnClickListener(v -> checkPermissionAndExport());

        loadLogs();
    }

    // ── Load Firestore ────────────────────────────────────────────────────────
    private void loadLogs() {
        b.tvCount.setText("Loading...");
        Query query = db.collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        // If launched from Profile, filter to current user only
        if (filterByEmail != null) {
            query = query.whereEqualTo("email", filterByEmail);
        }

        query.addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            all = snap.getDocuments();
            applyFilter();
        });
    }

    // ── Filter ────────────────────────────────────────────────────────────────
    private void applyFilter() {
        String q = b.etSearch.getText().toString().trim().toLowerCase();
        filtered.clear();
        for (DocumentSnapshot doc : all) {
            String email   = doc.getString("email")   != null ? doc.getString("email").toLowerCase()   : "";
            String subject = doc.getString("subject") != null ? doc.getString("subject").toLowerCase() : "";
            String module  = doc.getString("module")  != null ? doc.getString("module")  : "";
            String action  = doc.getString("action")  != null ? doc.getString("action")  : "";

            boolean matchSearch = q.isEmpty()
                    || email.contains(q) || subject.contains(q)
                    || module.toLowerCase().contains(q) || action.toLowerCase().contains(q);
            boolean matchModule = filterModule.equals("All") || filterModule.equals(module);
            boolean matchAction = filterAction.equals("All") || filterAction.equals(action);

            if (matchSearch && matchModule && matchAction) filtered.add(doc);
        }
        b.tvCount.setText(filtered.size() + " log(s)");
        adapter.notifyDataSetChanged();
    }

    // ── PDF Export ────────────────────────────────────────────────────────────
    private void checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
                return;
            }
        }
        exportToPdf();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_WRITE_STORAGE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            exportToPdf();
        } else {
            Toast.makeText(this, "Storage permission required to export PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportToPdf() {
        if (filtered.isEmpty()) {
            Snackbar.make(b.getRoot(), "No logs to export", Snackbar.LENGTH_SHORT).show();
            return;
        }

        b.btnExportPdf.setEnabled(false);
        b.btnExportPdf.setText("Exporting…");

        new Thread(() -> {
            try {
                SimpleDateFormat sdf     = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
                SimpleDateFormat fileFmt = new SimpleDateFormat("yyyyMMdd_HHmmss",    Locale.getDefault());
                String fileName = "ActivityLog_" + fileFmt.format(new Date()) + ".pdf";

                File dir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "")
                        : new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS), "Luminae");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);

                // ── iText7 PDF ─────────────────────────────────────────────
                PdfWriter  writer   = new PdfWriter(new FileOutputStream(outFile));
                PdfDocument pdf     = new PdfDocument(writer);
                Document   document = new Document(pdf);

                DeviceRgb maroon  = new DeviceRgb(107, 10, 10);
                DeviceRgb yellow  = new DeviceRgb(240, 192, 64);
                DeviceRgb lightBg = new DeviceRgb(245, 245, 245);

                // Title
                Paragraph title = new Paragraph("LUMINAE — Activity Log")
                        .setFontSize(18).setBold()
                        .setFontColor(maroon)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(4);
                document.add(title);

                // Subtitle: export info + filter applied
                String subtitle = "Exported on " + sdf.format(new Date())
                        + "   |   Module: " + filterModule
                        + "   |   Action: " + filterAction
                        + "   |   Total: " + filtered.size() + " log(s)";
                document.add(new Paragraph(subtitle)
                        .setFontSize(9)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(16));

                // Table header
                Table table = new Table(UnitValue.createPercentArray(
                        new float[]{12, 14, 28, 28, 18}))
                        .useAllAvailableWidth();

                String[] headers = {"Action", "Module", "By (Email)", "Subject", "Date & Time"};
                for (String h : headers) {
                    table.addHeaderCell(
                            new Cell().add(new Paragraph(h).setBold().setFontSize(9)
                                    .setFontColor(ColorConstants.WHITE))
                                    .setBackgroundColor(maroon)
                                    .setPadding(6));
                }

                // Table rows
                boolean shade = false;
                for (DocumentSnapshot doc : filtered) {
                    String action  = doc.getString("action")  != null ? doc.getString("action")  : "—";
                    String module  = doc.getString("module")  != null ? doc.getString("module")  : "—";
                    String email   = doc.getString("email")   != null ? doc.getString("email")   : "—";
                    String subject = doc.getString("subject") != null ? doc.getString("subject") : "—";
                    Timestamp ts   = doc.getTimestamp("timestamp");
                    String dateStr = ts != null ? sdf.format(ts.toDate()) : "—";

                    DeviceRgb rowBg = shade ? lightBg : new DeviceRgb(255, 255, 255);
                    shade = !shade;

                    // Action cell — color by type
                    DeviceRgb actionColor = actionColor(action);
                    table.addCell(new Cell().add(
                            new Paragraph(action).setBold().setFontSize(8)
                                    .setFontColor(ColorConstants.WHITE))
                            .setBackgroundColor(actionColor).setPadding(5));

                    for (String val : new String[]{module, email, subject, dateStr}) {
                        table.addCell(new Cell().add(
                                new Paragraph(val).setFontSize(8))
                                .setBackgroundColor(rowBg).setPadding(5));
                    }
                }

                document.add(table);
                document.close();

                // Share / open the PDF
                runOnUiThread(() -> {
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export PDF");
                    Snackbar.make(b.getRoot(), "PDF saved: " + outFile.getName(),
                            Snackbar.LENGTH_LONG)
                            .setAction("Open", v -> openPdf(outFile))
                            .show();
                });

            } catch (Exception ex) {
                runOnUiThread(() -> {
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export PDF");
                    Snackbar.make(b.getRoot(),
                            "Export failed: " + ex.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openPdf(File file) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Open PDF"));
    }

    private DeviceRgb actionColor(String action) {
        switch (action) {
            case "Create":   return new DeviceRgb(46,  125, 50);  // green
            case "Modified": return new DeviceRgb(230, 81,  0);   // orange
            case "Delete":   return new DeviceRgb(183, 28,  28);  // red
            case "Viewed":   return new DeviceRgb(55,  71,  79);  // blue-grey
            default:         return new DeviceRgb(100, 100, 100);
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvActionBadge, tvModule, tvEmail, tvSubject, tvTimestamp;
            VH(View v) {
                super(v);
                tvActionBadge = v.findViewById(R.id.tv_action_badge);
                tvModule      = v.findViewById(R.id.tv_module);
                tvEmail       = v.findViewById(R.id.tv_email);
                tvSubject     = v.findViewById(R.id.tv_subject);
                tvTimestamp   = v.findViewById(R.id.tv_timestamp);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_activity_log, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot doc = filtered.get(pos);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault());

            String action  = doc.getString("action")  != null ? doc.getString("action")  : "—";
            String module  = doc.getString("module")  != null ? doc.getString("module")  : "—";
            String email   = doc.getString("email")   != null ? doc.getString("email")   : "—";
            String subject = doc.getString("subject") != null ? doc.getString("subject") : "—";
            Timestamp ts   = doc.getTimestamp("timestamp");

            h.tvActionBadge.setText(action);
            h.tvModule.setText(module);
            h.tvEmail.setText(email);
            h.tvSubject.setText(subject);
            h.tvTimestamp.setText(ts != null ? sdf.format(ts.toDate()) : "—");

            switch (action) {
                case "Create":   h.tvActionBadge.setBackgroundResource(R.drawable.badge_create);   break;
                case "Modified": h.tvActionBadge.setBackgroundResource(R.drawable.badge_modified); break;
                case "Delete":   h.tvActionBadge.setBackgroundResource(R.drawable.badge_delete);   break;
                case "Viewed":   h.tvActionBadge.setBackgroundResource(R.drawable.badge_viewed);   break;
                default:         h.tvActionBadge.setBackgroundResource(R.drawable.badge_active);
            }
        }

        @Override public int getItemCount() { return filtered.size(); }
    }
}
