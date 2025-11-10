package com.example.riperightai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        firestore = FirebaseFirestore.getInstance();
        historyContainer = findViewById(R.id.historyContainer);

        // ✅ Ensure non-null deviceId
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "UNKNOWN_DEVICE";
        }

        // --- Bottom navigation setup ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.navigation_history);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_scan) {
                Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                finish();
                return true;
            } else if (id == R.id.navigation_history) {
                return true;
            }
            return false;
        });

        // --- Handle Back Press ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                startActivity(intent);
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

        // ✅ Listen for only this device’s scans
        snapshotListener = firestore.collection("scan_history")
                .whereEqualTo("deviceId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot querySnapshot, @Nullable FirebaseFirestoreException e) {
                        if (isFinishing() || isDestroyed()) return;

                        if (e != null) {
                            e.printStackTrace();
                            return;
                        }

                        historyContainer.removeAllViews();

                        if (querySnapshot == null || querySnapshot.isEmpty()) {
                            TextView emptyView = new TextView(HistoryActivity.this);
                            emptyView.setText("No history found for this device.\nTry scanning a mango first!");
                            emptyView.setTextSize(16);
                            emptyView.setPadding(20, 40, 20, 40);
                            historyContainer.addView(emptyView);
                            return;
                        }

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

                            // 🗑️ Delete handler
                            itemView.setOnClickListener(v -> {
                                new androidx.appcompat.app.AlertDialog.Builder(HistoryActivity.this)
                                        .setTitle("Delete this scan?")
                                        .setMessage("Are you sure you want to delete this scan record?")
                                        .setPositiveButton("Delete", (dialog, which) -> {
                                            firestore.collection("scan_history")
                                                    .document(docId)
                                                    .delete()
                                                    .addOnSuccessListener(aVoid -> {
                                                        android.widget.Toast.makeText(
                                                                HistoryActivity.this,
                                                                "Deleted successfully",
                                                                android.widget.Toast.LENGTH_SHORT
                                                        ).show();
                                                        refreshHistory();
                                                    })
                                                    .addOnFailureListener(err -> {
                                                        android.widget.Toast.makeText(
                                                                HistoryActivity.this,
                                                                "Delete failed: " + err.getMessage(),
                                                                android.widget.Toast.LENGTH_LONG
                                                        ).show();
                                                    });
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            });

                            historyContainer.addView(itemView);
                        }
                    }
                });
    }

    private void refreshHistory() {
        if (snapshotListener != null) {
            snapshotListener.remove();
            snapshotListener = null;
        }
        new Handler().postDelayed(this::loadHistory, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (snapshotListener != null) {
            snapshotListener.remove();
            snapshotListener = null;
        }
    }
}
