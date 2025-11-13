package com.example.riperightai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListenerRegistration registration;

    private LinearLayout historyContainer;
    private View emptyStateLayout;
    private ScrollView historyScroll;
    private TextView headerSubtitle;
    private TextView emptyTitle; // optional

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = FirebaseFirestore.getInstance();

        historyContainer  = findViewById(R.id.historyContainer);
        emptyStateLayout  = findViewById(R.id.emptyStateLayout);
        historyScroll     = findViewById(R.id.historyScroll);
        headerSubtitle    = findViewById(R.id.headerSubtitle);

        // ---- Bottom navigation ----
        BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        if (nav != null) {
            nav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == getIdSafely("menu_scan") || id == getIdSafely("navigation_scan")) {
                    // Go back to main activity
                    Intent intent = new Intent(this, MainActivity.class);
                    startActivity(intent);

                    // Reverse transition (History → Main)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);

                    finish();
                    return true;
                } else if (id == getIdSafely("menu_history") || id == getIdSafely("navigation_history")) {
                    return true; // already here
                }
                return false;
            });

            int historyId = getExistingMenuId(nav,
                    getIdSafely("menu_history"), getIdSafely("navigation_history"));
            if (historyId != 0) nav.setSelectedItemId(historyId);
        }

        // Optional: set empty-state title if present
        if (emptyStateLayout instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) emptyStateLayout;
            if (ll.getChildCount() > 1 && ll.getChildAt(1) instanceof TextView) {
                emptyTitle = (TextView) ll.getChildAt(1);
                emptyTitle.setText("No Scans as of the Moment");
            }
        }

        startListening();
    }

    private void startListening() {
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Query q = db.collection("scan_history")
                .whereEqualTo("deviceId", deviceId);

        registration = q.addSnapshotListener(this, (snap, err) -> {
            if (err != null) {
                Toast.makeText(this, "History error: " + err.getMessage(), Toast.LENGTH_LONG).show();
                showEmpty(true);
                if (headerSubtitle != null) headerSubtitle.setText("0 scans recorded");
                return;
            }
            render(snap);
        });
    }

    private void render(@Nullable QuerySnapshot snap) {
        historyContainer.removeAllViews();

        if (snap == null || snap.isEmpty()) {
            showEmpty(true);
            if (headerSubtitle != null) headerSubtitle.setText("0 scans recorded");
            return;
        }

        ArrayList<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
        Collections.sort(docs, (a, b) -> Long.compare(safeTimestamp(b.get("timestamp")), safeTimestamp(a.get("timestamp"))));

        showEmpty(false);
        if (headerSubtitle != null) headerSubtitle.setText(docs.size() + " scans recorded");

        LayoutInflater inflater = LayoutInflater.from(this);
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());

        for (DocumentSnapshot doc : docs) {
            View row = inflater.inflate(R.layout.item_history, historyContainer, false);

            ImageView img   = row.findViewById(R.id.historyImage);
            View trash      = row.findViewById(R.id.btnDelete);
            TextView tvVar  = row.findViewById(R.id.txtVariety);
            TextView tvRip  = row.findViewById(R.id.txtRipeness);
            TextView tvConf = row.findViewById(R.id.txtConfidence);
            TextView tvDate = row.findViewById(R.id.txtDate);

            String variety    = safeString(doc.get("variety"));
            String ripeness   = safeString(doc.get("ripeness"));
            String confidence = safeString(doc.get("confidence"));
            String imageUri   = safeString(doc.get("imageUri"));
            long tsMillis     = safeTimestamp(doc.get("timestamp"));

            tvVar.setText(!variety.isEmpty() ? variety : "Unknown");
            tvRip.setText(!ripeness.isEmpty() ? ripeness : "Unknown");
            tvConf.setText(!confidence.isEmpty() ? "Confidence: " + confidence + "%" : "Confidence: —");
            tvDate.setText(tsMillis > 0 ? df.format(new Date(tsMillis)) : "");

            if (!imageUri.isEmpty()) {
                try { img.setImageURI(Uri.parse(imageUri)); } catch (Exception ignored) {}
            }

            if (trash != null) {
                trash.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete scan?")
                            .setMessage("This will remove the selected history.")
                            .setPositiveButton("Delete", (d, w) ->
                                    doc.getReference().delete()
                                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Scan deleted", Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show())
                            )
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            historyContainer.addView(row);
        }
    }

    private void showEmpty(boolean empty) {
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (historyScroll != null) historyScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }

    private static String safeString(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private static long safeTimestamp(Object ts) {
        try {
            if (ts instanceof Number) return ((Number) ts).longValue();
            if (ts instanceof String) return Long.parseLong((String) ts);
        } catch (Exception ignored) {}
        return 0L;
    }

    private int getIdSafely(String idName) {
        try {
            return getResources().getIdentifier(idName, "id", getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    private int getExistingMenuId(BottomNavigationView nav, int... ids) {
        if (nav == null || nav.getMenu() == null) return 0;
        for (int id : ids) {
            if (id != 0 && nav.getMenu().findItem(id) != null) return id;
        }
        return 0;
    }

    // Smooth back press transition
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
