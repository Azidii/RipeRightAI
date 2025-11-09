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
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private LinearLayout historyContainer;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // --- Setup navigation ---
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

        // --- Back Press ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });

        // --- Firebase + Device ID setup ---
        firestore = FirebaseFirestore.getInstance();
        historyContainer = findViewById(R.id.historyContainer);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        loadHistory();
    }

    private void loadHistory() {
        firestore.collection("scan_history")
                .whereEqualTo("deviceId", deviceId) // 👈 only show this device’s history
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    historyContainer.removeAllViews();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String variety = doc.getString("variety");
                        String ripeness = doc.getString("ripeness");
                        String confidence = doc.getString("confidence");
                        String imageUri = doc.getString("imageUri");
                        Long timestamp = doc.getLong("timestamp");

                        String formattedDate = "Unknown date";
                        if (timestamp != null) {
                            formattedDate = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                    .format(new Date(timestamp));
                        }

                        // Inflate each history item layout
                        View itemView = LayoutInflater.from(this)
                                .inflate(R.layout.item_history, historyContainer, false);

                        ImageView mangoImage = itemView.findViewById(R.id.historyImage);
                        TextView txtVariety = itemView.findViewById(R.id.txtVariety);
                        TextView txtRipeness = itemView.findViewById(R.id.txtRipeness);
                        TextView txtConfidence = itemView.findViewById(R.id.txtConfidence);
                        TextView txtDate = itemView.findViewById(R.id.txtDate);

                        txtVariety.setText(variety);
                        txtRipeness.setText("Ripeness: " + ripeness);
                        txtConfidence.setText("Confidence: " + confidence);
                        txtDate.setText("Scanned on: " + formattedDate);

                        if (imageUri != null) {
                            Glide.with(this)
                                    .load(Uri.parse(imageUri))
                                    .into(mangoImage);
                        }

                        historyContainer.addView(itemView);
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }
}
