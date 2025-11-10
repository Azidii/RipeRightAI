package com.example.riperightai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private LinearLayout historyContainer;
    private String deviceId;
    private ListenerRegistration snapshotListener;
    private TextView headerSubtitle;
    private LinearLayout emptyStateLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        firestore = FirebaseFirestore.getInstance();
        historyContainer = findViewById(R.id.historyContainer);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) deviceId = "UNKNOWN_DEVICE";

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.navigation_history);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_scan) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
                return true;
            }
            return item.getItemId() == R.id.navigation_history;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(HistoryActivity.this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });

        loadHistory();
    }

    private void loadHistory() {
        if (snapshotListener != null) {
            snapshotListener.remove();
            snapshotListener = null;
        }

        snapshotListener = firestore.collection("scan_history")
                .whereEqualTo("deviceId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot querySnapshot,
                                        @Nullable FirebaseFirestoreException e) {
                        if (isFinishing() || isDestroyed()) return;
                        if (e != null) {
                            e.printStackTrace();
                            return;
                        }

                        historyContainer.removeAllViews();
                        int scanCount = (querySnapshot != null) ? querySnapshot.size() : 0;

                        // 🔄 update header
                        headerSubtitle.setText(scanCount + " scan" + (scanCount == 1 ? "" : "s") + " recorded");

                        // toggle empty state
                        emptyStateLayout.setVisibility(scanCount == 0 ? View.VISIBLE : View.GONE);

                        if (querySnapshot == null || querySnapshot.isEmpty()) return;

                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String docId = doc.getId();
                            String variety = doc.getString("variety");
                            String ripeness = doc.getString("ripeness");
                            String confidence = doc.getString("confidence");
                            String imageUri = doc.getString("imageUri");
                            Long timestamp = doc.getLong("timestamp");

                            String formattedDate = "Unknown date";
                            if (timestamp != null) {
                                formattedDate = new SimpleDateFormat(
                                        "MMM dd, yyyy hh:mm a",
                                        Locale.getDefault()
                                ).format(new Date(timestamp));
                            }

                            View itemView = LayoutInflater.from(HistoryActivity.this)
                                    .inflate(R.layout.item_history, historyContainer, false);

                            ImageView mangoImage = itemView.findViewById(R.id.historyImage);
                            TextView txtVariety = itemView.findViewById(R.id.txtVariety);
                            TextView txtRipeness = itemView.findViewById(R.id.txtRipeness);
                            TextView txtConfidence = itemView.findViewById(R.id.txtConfidence);
                            TextView txtDate = itemView.findViewById(R.id.txtDate);

                            txtVariety.setText(variety != null ? variety : "Unknown Variety");
                            txtRipeness.setText("Ripeness: " + (ripeness != null ? ripeness : "N/A"));
                            txtConfidence.setText("Confidence: " + (confidence != null ? confidence : "N/A"));
                            txtDate.setText("Scanned on: " + formattedDate);

                            if (imageUri != null && !imageUri.isEmpty()) {
                                Glide.with(getApplicationContext())
                                        .load(Uri.parse(imageUri))
                                        .into(mangoImage);
                            }

                            // 🗑 delete handler
                            itemView.setOnClickListener(v -> {
                                new androidx.appcompat.app.AlertDialog.Builder(HistoryActivity.this)
                                        .setTitle("Delete this scan?")
                                        .setMessage("Are you sure you want to delete this scan record?")
                                        .setPositiveButton("Delete", (dialog, which) -> {
                                            firestore.collection("scan_history")
                                                    .document(docId)
                                                    .delete()
                                                    .addOnSuccessListener(aVoid -> {
                                                        // ✅ Instant local UI update
                                                        historyContainer.removeView(itemView);
                                                        int current = historyContainer.getChildCount();
                                                        headerSubtitle.setText(current + " scan" + (current == 1 ? "" : "s") + " recorded");
                                                        if (current == 0)
                                                            emptyStateLayout.setVisibility(View.VISIBLE);

                                                        android.widget.Toast.makeText(
                                                                HistoryActivity.this,
                                                                "Deleted successfully",
                                                                android.widget.Toast.LENGTH_SHORT
                                                        ).show();
                                                    })
                                                    .addOnFailureListener(err ->
                                                            android.widget.Toast.makeText(
                                                                    HistoryActivity.this,
                                                                    "Delete failed: " + err.getMessage(),
                                                                    android.widget.Toast.LENGTH_LONG
                                                            ).show()
                                                    );
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            });

                            historyContainer.addView(itemView);
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (snapshotListener != null) {
            snapshotListener.remove();
            snapshotListener = null;
        }
        super.onDestroy();
    }
}
