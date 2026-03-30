package com.example.luminae.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.EditText;
import android.widget.RadioButton;
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
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
    private List<DocumentSnapshot> pendingExportLogs = null;
    private ExportFormat pendingFormat = ExportFormat.PDF;
    private String pendingDateRangeLabel = "";

    private enum ExportFormat {
        PDF,
        SPREADSHEET
    }

    private static class ExportDestination {
        OutputStream stream;
        Uri openUri;
        Uri pendingUri;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityActivityLogBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(b.toolbar);
        filterByEmail = getIntent().getStringExtra("filterByEmail");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(
                    filterByEmail != null ? "My Activity Log" : "Activity Log"
            );
        }
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
            else if (b.chipModuleReports.isChecked())      filterModule = "Reports";
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

        // Export button
        b.btnExportPdf.setOnClickListener(v -> showExportDialog());

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

    // ── Export Prompt + Export ────────────────────────────────────────────────
    private void showExportDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_export_report, null);
        EditText etStart = form.findViewById(R.id.et_start_date);
        EditText etEnd = form.findViewById(R.id.et_end_date);
        RadioButton rbPdf = form.findViewById(R.id.rb_pdf);

        final Calendar startCal = Calendar.getInstance();
        final Calendar endCal = Calendar.getInstance();
        final long[] startMs = new long[]{-1L};
        final long[] endMs = new long[]{-1L};
        final SimpleDateFormat uiDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        View.OnClickListener pickStart = v -> {
            Calendar seed = startMs[0] > 0 ? millisToCalendar(startMs[0]) : Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startCal.set(year, month, dayOfMonth, 0, 0, 0);
                        startCal.set(Calendar.MILLISECOND, 0);
                        startMs[0] = startCal.getTimeInMillis();
                        etStart.setText(uiDate.format(startCal.getTime()));
                    },
                    seed.get(Calendar.YEAR),
                    seed.get(Calendar.MONTH),
                    seed.get(Calendar.DAY_OF_MONTH)
            );
            dp.show();
        };

        View.OnClickListener pickEnd = v -> {
            Calendar seed = endMs[0] > 0 ? millisToCalendar(endMs[0]) : Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        endCal.set(year, month, dayOfMonth, 23, 59, 59);
                        endCal.set(Calendar.MILLISECOND, 999);
                        endMs[0] = endCal.getTimeInMillis();
                        etEnd.setText(uiDate.format(endCal.getTime()));
                    },
                    seed.get(Calendar.YEAR),
                    seed.get(Calendar.MONTH),
                    seed.get(Calendar.DAY_OF_MONTH)
            );
            dp.show();
        };
        etStart.setOnClickListener(pickStart);
        etEnd.setOnClickListener(pickEnd);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Export Audit Logs");
        builder.setView(form);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Done", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (startMs[0] < 0 || endMs[0] < 0) {
                Toast.makeText(this, "Please select starting and ending date", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startMs[0] > endMs[0]) {
                Toast.makeText(this, "Starting date must be before ending date", Toast.LENGTH_SHORT).show();
                return;
            }

            List<DocumentSnapshot> logsInRange = filterByDateRange(filtered, startMs[0], endMs[0]);
            if (logsInRange.isEmpty()) {
                Toast.makeText(this, "No logs found in selected date range", Toast.LENGTH_SHORT).show();
                return;
            }

            pendingExportLogs = logsInRange;
            pendingFormat = rbPdf.isChecked() ? ExportFormat.PDF : ExportFormat.SPREADSHEET;
            pendingDateRangeLabel = uiDate.format(new Date(startMs[0])) + " to " + uiDate.format(new Date(endMs[0]));

            dialog.dismiss();
            checkPermissionAndExport();
        });
    }

    private Calendar millisToCalendar(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c;
    }

    private List<DocumentSnapshot> filterByDateRange(List<DocumentSnapshot> source, long startMs, long endMs) {
        List<DocumentSnapshot> out = new ArrayList<>();
        for (DocumentSnapshot doc : source) {
            Timestamp ts = doc.getTimestamp("timestamp");
            if (ts == null) continue;
            long value = ts.toDate().getTime();
            if (value >= startMs && value <= endMs) out.add(doc);
        }
        return out;
    }

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
        startExport();
    }

    private void startExport() {
        if (pendingExportLogs == null || pendingExportLogs.isEmpty()) {
            Snackbar.make(b.getRoot(), "No logs to export", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (pendingFormat == ExportFormat.PDF) {
            exportToPdf(pendingExportLogs, pendingDateRangeLabel);
        } else {
            exportToSpreadsheet(pendingExportLogs, pendingDateRangeLabel);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_WRITE_STORAGE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startExport();
        } else {
            Toast.makeText(this, "Storage permission required to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportToPdf(List<DocumentSnapshot> logsToExport, String rangeLabel) {
        b.btnExportPdf.setEnabled(false);
        b.btnExportPdf.setText("Exporting…");

        new Thread(() -> {
            ExportDestination dest = null;
            try {
                SimpleDateFormat sdf     = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
                SimpleDateFormat fileFmt = new SimpleDateFormat("yyyyMMdd_HHmmss",    Locale.getDefault());
                String fileName = "ActivityLog_" + fileFmt.format(new Date()) + ".pdf";

                // ── iText7 PDF ─────────────────────────────────────────────
                dest = createExportDestination(fileName, "application/pdf");
                PdfWriter  writer   = new PdfWriter(dest.stream);
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
                        + "   |   Range: " + rangeLabel
                        + "   |   Total: " + logsToExport.size() + " log(s)";
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
                for (DocumentSnapshot doc : logsToExport) {
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
                finalizeExportDestination(dest);
                Uri openUri = dest.openUri;

                // Share / open the PDF
                runOnUiThread(() -> {
                    String reportSubject = "PDF | " + rangeLabel + " | " + logsToExport.size() + " log(s)";
                    ActivityLogger.logReport(ActivityLogger.ACTION_CREATE, reportSubject);
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export");
                    Snackbar.make(b.getRoot(), "PDF saved: " + fileName,
                            Snackbar.LENGTH_LONG)
                            .setAction("Open", v -> openExportedUri(openUri, "application/pdf", "Open with"))
                            .show();
                });

            } catch (Exception ex) {
                cleanupPendingDestination(dest);
                runOnUiThread(() -> {
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export");
                    Snackbar.make(b.getRoot(),
                            "Export failed: " + ex.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void exportToSpreadsheet(List<DocumentSnapshot> logsToExport, String rangeLabel) {
        b.btnExportPdf.setEnabled(false);
        b.btnExportPdf.setText("Exporting…");

        new Thread(() -> {
            ExportDestination dest = null;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
                SimpleDateFormat fileFmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String fileName = "ActivityLog_" + fileFmt.format(new Date()) + ".csv";

                dest = createExportDestination(fileName, "text/csv");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dest.stream))) {
                    writer.write("Report Range," + csvEscape(rangeLabel));
                    writer.newLine();
                    writer.write("Module Filter," + csvEscape(filterModule));
                    writer.newLine();
                    writer.write("Action Filter," + csvEscape(filterAction));
                    writer.newLine();
                    writer.write("Generated On," + csvEscape(sdf.format(new Date())));
                    writer.newLine();
                    writer.newLine();
                    writer.write("Action,Module,Email,Subject,Date & Time");
                    writer.newLine();

                    for (DocumentSnapshot doc : logsToExport) {
                        String action = valueOrDash(doc.getString("action"));
                        String module = valueOrDash(doc.getString("module"));
                        String email = valueOrDash(doc.getString("email"));
                        String subject = valueOrDash(doc.getString("subject"));
                        Timestamp ts = doc.getTimestamp("timestamp");
                        String dateStr = ts != null ? sdf.format(ts.toDate()) : "—";
                        writer.write(csvEscape(action) + ","
                                + csvEscape(module) + ","
                                + csvEscape(email) + ","
                                + csvEscape(subject) + ","
                                + csvEscape(dateStr));
                        writer.newLine();
                    }
                }
                finalizeExportDestination(dest);
                Uri openUri = dest.openUri;

                runOnUiThread(() -> {
                    String reportSubject = "Spreadsheet | " + rangeLabel + " | " + logsToExport.size() + " log(s)";
                    ActivityLogger.logReport(ActivityLogger.ACTION_CREATE, reportSubject);
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export");
                    Snackbar.make(b.getRoot(), "Spreadsheet saved: " + fileName,
                                    Snackbar.LENGTH_LONG)
                            .setAction("Open", v -> openExportedUri(openUri, "text/csv", "Open with"))
                            .show();
                });
            } catch (Exception ex) {
                cleanupPendingDestination(dest);
                runOnUiThread(() -> {
                    b.btnExportPdf.setEnabled(true);
                    b.btnExportPdf.setText("Export");
                    Snackbar.make(b.getRoot(),
                            "Export failed: " + ex.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String valueOrDash(String s) {
        return s != null && !s.isEmpty() ? s : "—";
    }

    private String csvEscape(String s) {
        String val = s == null ? "" : s;
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    private ExportDestination createExportDestination(String fileName, String mimeType) throws Exception {
        ExportDestination dest = new ExportDestination();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Luminae");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) throw new IllegalStateException("Unable to create export file");

            OutputStream os = getContentResolver().openOutputStream(uri, "w");
            if (os == null) throw new IllegalStateException("Unable to open export stream");

            dest.stream = os;
            dest.pendingUri = uri;
            dest.openUri = uri;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "Luminae");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Unable to create Documents/Luminae folder");
            }
            File outFile = new File(dir, fileName);
            dest.stream = new FileOutputStream(outFile);
            dest.openUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", outFile);
        }
        return dest;
    }

    private void finalizeExportDestination(ExportDestination dest) {
        if (dest == null) return;
        try {
            if (dest.stream != null) dest.stream.close();
        } catch (Exception ignored) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dest.pendingUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(dest.pendingUri, values, null, null);
            dest.pendingUri = null;
        }
    }

    private void cleanupPendingDestination(ExportDestination dest) {
        if (dest == null) return;
        try {
            if (dest.stream != null) dest.stream.close();
        } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dest.pendingUri != null) {
            try {
                getContentResolver().delete(dest.pendingUri, null, null);
            } catch (Exception ignored) {}
            dest.pendingUri = null;
        }
    }

    private void openExportedUri(Uri uri, String mimeType, String chooserTitle) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(Intent.createChooser(intent, chooserTitle));
            } else {
                Snackbar.make(b.getRoot(),
                        "No app available to open this file",
                        Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Snackbar.make(b.getRoot(),
                    "Unable to open exported file",
                    Snackbar.LENGTH_LONG).show();
        }
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
