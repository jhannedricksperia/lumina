package com.example.luminae.admin.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.example.luminae.R;
import com.example.luminae.activities.AnnouncementFormActivity;
import com.example.luminae.activities.EventFormActivity;
import com.example.luminae.activities.EventParticipantsActivity;
import com.example.luminae.activities.PostDetailActivity;
import com.example.luminae.utils.EventDisplayUtils;
import com.example.luminae.utils.FullscreenImageGallery;
import com.example.luminae.utils.LikeIconHelper;
import com.example.luminae.utils.PostImageCarouselBinder;
import com.example.luminae.utils.PostImageList;
import com.example.luminae.databinding.FragmentAdminFeedBinding;
import com.example.luminae.utils.ActivityLogger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
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

    interface NameCallback { void onResult(String name); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle saved) {
        b    = FragmentAdminFeedBinding.inflate(inflater, container, false);
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

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
            Intent i = new Intent(getActivity(),
                    activeTab == TAB_ANNOUNCEMENTS ? AnnouncementFormActivity.class : EventFormActivity.class);
            i.putExtra("collection", activeTab == TAB_ANNOUNCEMENTS ? "announcements" : "events");
            startActivity(i);
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

    // ── adapter ───────────────────────────────────────────────────────────────

    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView  tvCreatedBy, tvDateCreated, tvTitle, tvDesc, tvLikeCount;
            ImageView ivLike;
            View      btnLikeRow;
            View      layoutPostMedia;
            ViewPager2 vpPostImages;
            LinearLayout dotsPostImages;
            ImageView btnMore;
            TextView  tvWhere, tvEventDate, tvGoingCount, tvGoingInfo, btnGoing;
            View      layoutEventInfo;
            View      rowGoing;
            String    boundCarouselDocId;
            String    boundCarouselSig;

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
                tvLikeCount     = v.findViewById(R.id.tv_like_count);
                ivLike          = v.findViewById(R.id.iv_like);
                btnLikeRow      = v.findViewById(R.id.btn_like);
                layoutPostMedia = v.findViewById(R.id.layout_post_media);
                vpPostImages    = v.findViewById(R.id.vp_post_images);
                dotsPostImages  = v.findViewById(R.id.dots_post_images);
                btnMore         = v.findViewById(R.id.btn_more);
                tvWhere         = v.findViewById(R.id.tv_location);
                tvEventDate     = v.findViewById(R.id.tv_event_date);
                tvGoingCount    = v.findViewById(R.id.tv_going_count);
                if (tvGoingCount == null) tvGoingCount = v.findViewById(R.id.tv_participants);
                tvGoingInfo     = v.findViewById(R.id.tv_going_info);
                btnGoing        = v.findViewById(R.id.btn_going);
                layoutEventInfo = v.findViewById(R.id.layout_event_info);
                rowGoing        = v.findViewById(R.id.row_going);
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

            // images
            if (h.layoutPostMedia != null && h.vpPostImages != null) {
                List<String> imgs = PostImageList.fromDocument(doc);
                if (imgs.isEmpty()) {
                    h.layoutPostMedia.setVisibility(View.GONE);
                    h.boundCarouselDocId = null;
                    h.boundCarouselSig = null;
                } else {
                    h.layoutPostMedia.setVisibility(View.VISIBLE);
                    String sig = doc.getId() + ":" + PostImageList.signature(imgs);
                    if (!java.util.Objects.equals(doc.getId(), h.boundCarouselDocId)
                            || !java.util.Objects.equals(sig, h.boundCarouselSig)) {
                        h.boundCarouselDocId = doc.getId();
                        h.boundCarouselSig = sig;
                        android.content.Context ctx = requireContext();
                        PostImageCarouselBinder.bind(h.vpPostImages, h.dotsPostImages, imgs, ctx,
                                pageIdx -> FullscreenImageGallery.show(ctx, imgs, pageIdx));
                    }
                }
            }

            // item_feed_post includes event blocks; hide them on the Announcements tab
            if (activeTab == TAB_ANNOUNCEMENTS) {
                View evInfo = h.itemView.findViewById(R.id.layout_event_info);
                if (evInfo != null) evInfo.setVisibility(View.GONE);
                View goingRow = h.itemView.findViewById(R.id.layout_going_row);
                if (goingRow != null) goingRow.setVisibility(View.GONE);
            }

            // event-only extras
            if (activeTab == TAB_EVENTS) {
                if (h.layoutEventInfo != null) h.layoutEventInfo.setVisibility(View.VISIBLE);
                long going = EventDisplayUtils.countGoing(doc);
                String loc = EventDisplayUtils.formatLocation(doc);
                if (h.tvWhere != null)
                    h.tvWhere.setText(loc.isEmpty() ? "—" : loc);
                if (h.tvEventDate != null)
                    h.tvEventDate.setText(EventDisplayUtils.formatEventDate(doc));
                if (h.tvGoingCount != null) h.tvGoingCount.setText(going + " going");
                if (h.tvGoingInfo  != null) {
                    h.tvGoingInfo.setVisibility(View.VISIBLE);
                    h.tvGoingInfo.setText(going + " going");
                }
                if (h.rowGoing != null) {
                    h.rowGoing.setOnClickListener(ev -> {
                        Intent pi = new Intent(getActivity(), EventParticipantsActivity.class);
                        pi.putExtra("eventId", doc.getId());
                        pi.putExtra("eventTitle", title);
                        startActivity(pi);
                    });
                }
            }

            // ── Like logic (announcement cards only — event layout has no like row) ──
            if (h.tvLikeCount != null && h.btnLikeRow != null) {
                String col = activeTab == TAB_ANNOUNCEMENTS ? "announcements" : "events";
                DocumentReference postRef = db.collection(col).document(doc.getId());
                String uid = auth.getUid();
                DocumentReference likeRef = uid != null
                        ? postRef.collection("likes").document(uid) : null;

                final long[]    likeCount = {hearts};
                final boolean[] likedByMe = {false};

                Runnable refreshLikeUi = () -> {
                    h.tvLikeCount.setText(String.valueOf(likeCount[0]));
                    LikeIconHelper.setHeartTint(h.ivLike, likedByMe[0]);
                };
                refreshLikeUi.run();

                if (likeRef != null) {
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        likedByMe[0] = likeDoc.exists();
                        refreshLikeUi.run();
                    });
                }

                h.btnLikeRow.setOnClickListener(v -> {
                    if (uid == null || likeRef == null) return;
                    likeRef.get().addOnSuccessListener(likeDoc -> {
                        if (likeDoc.exists()) {
                            likeRef.delete();
                            postRef.update("likeCount", FieldValue.increment(-1));
                            likedByMe[0]  = false;
                            likeCount[0]  = Math.max(0, likeCount[0] - 1);
                        } else {
                            Map<String, Object> likeData = new HashMap<>();
                            likeData.put("uid",     uid);
                            likeData.put("likedAt", Timestamp.now());
                            likeRef.set(likeData);
                            postRef.update("likeCount", FieldValue.increment(1));
                            likedByMe[0] = true;
                            likeCount[0]++;
                        }
                        refreshLikeUi.run();
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
            if (h.btnMore != null) {
                h.btnMore.setVisibility(View.VISIBLE);
                h.btnMore.setOnClickListener(v -> showPostOptions(doc, title));
            }
            h.itemView.setOnLongClickListener(v -> {
                showPostOptions(doc, title);
                return true;
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private void showPostOptions(DocumentSnapshot doc, String title) {
        String[] items = {"Edit", "Archive", "Delete"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Post Options")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(getActivity(),
                                activeTab == TAB_ANNOUNCEMENTS ? AnnouncementFormActivity.class : EventFormActivity.class);
                        i.putExtra("doc_id", doc.getId());
                        i.putExtra("collection", activeTab == TAB_ANNOUNCEMENTS ? "announcements" : "events");
                        startActivity(i);
                    } else if (which == 1) {
                        String currentStatus = doc.getString("status");
                        boolean isArchived = "Archive".equalsIgnoreCase(currentStatus)
                                || "Archived".equalsIgnoreCase(currentStatus);
                        String newStatus = isArchived ? "Active" : "Archive";
                        doc.getReference().update("status", newStatus)
                                .addOnSuccessListener(unused -> {
                                    // no-op
                                });
                    } else {
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
                                .show();
                    }
                })
                .show();
    }

    // ── lifecycle cleanup ─────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeListener != null) { activeListener.remove(); activeListener = null; }
        b = null;
    }
}