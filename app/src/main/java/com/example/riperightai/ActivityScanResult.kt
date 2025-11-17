package com.example.riperightai

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt
import java.util.Locale

class ActivityScanResult : AppCompatActivity() {

    private val tag = "FirestoreSave"

    companion object {
        // This ensures only one history save per image per session
        var lastSavedImageUri: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        // Toolbar Setup
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        // Retrieve Intent Data
        val variety = intent.getStringExtra("variety") ?: "Unknown"
        val ripeness = intent.getStringExtra("ripeness") ?: "Unknown"
        val confidenceRaw = intent.getStringExtra("confidence") ?: "0"
        val imgUri = intent.getStringExtra("image_uri")

        // Bind Views
        val varietyName = findViewById<TextView>(R.id.varietyName)
        val confidenceText = findViewById<TextView>(R.id.confidenceText)
        val ripenessState = findViewById<TextView>(R.id.ripenessState)
        val progressRipeness = findViewById<ProgressBar>(R.id.progressRipeness)
        val scannedImage = findViewById<ImageView>(R.id.scannedImage)
        val estimationText = findViewById<TextView>(R.id.estimationText)
        val btnAssessRipeness = findViewById<Button>(R.id.btnAssessRipeness)

        // Display Confidence
        val confValue = confidenceRaw.toFloatOrNull()?.coerceIn(0f, 100f)?.roundToInt() ?: 0
        confidenceText.text = "$confValue% confidence"

        // Load Image
        imgUri?.let {
            runCatching { scannedImage.setImageURI(Uri.parse(it)) }
                .onFailure { Log.e(tag, "Image load failed: ${it.message}") }
        }

        // Display Variety Details
        varietyName.text = variety
        val varietyLower = variety.lowercase(Locale.getDefault())
        val ripenessLower = ripeness.lowercase(Locale.getDefault())

        when {
            varietyLower.contains("cebu") || varietyLower.contains("carabao") ->
                handleAutoRipeness(ripenessLower, ripenessState, estimationText, progressRipeness)

            varietyLower.contains("kabayo") || varietyLower.contains("indian") || varietyLower.contains("apple") ->
                handleSelfAssessment(variety, ripenessState, estimationText, btnAssessRipeness, progressRipeness)

            else -> clearRipeness(ripenessState, estimationText, btnAssessRipeness, progressRipeness)
        }

        // Save to Firestore history only once per scan intent
        if (imgUri != null && imgUri != lastSavedImageUri) {
            saveToHistory(variety, ripeness, confidenceRaw, imgUri)
            lastSavedImageUri = imgUri
        } else {
            Log.d(tag, "Skipping duplicate Firestore save for URI: $imgUri")
        }
    }

    // Firestore Save
    private fun saveToHistory(variety: String, ripeness: String, confidenceRaw: String, imageUri: String?) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            val data = hashMapOf(
                "deviceId" to deviceId,
                "variety" to variety,
                "ripeness" to ripeness,
                "confidence" to confidenceRaw,
                "imageUri" to (imageUri ?: ""),
                "timestamp" to System.currentTimeMillis()
            )

            Log.d(tag, "Attempting Firestore save: $data")

            firestore.collection("scan_history").add(data)
                .addOnSuccessListener {
                    Log.d(tag, "✅ Successfully saved scan to Firestore")
                    Toast.makeText(this, "✅ Scan saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "❌ Firestore save failed: ${e.message}", e)
                    Toast.makeText(this, "Firestore save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(tag, "Firestore exception: ${e.message}", e)
            Toast.makeText(this, "Exception during save: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Auto Ripeness Visualization
    private fun handleAutoRipeness(stage: String, stateView: TextView, estimationView: TextView, progressBar: ProgressBar) {
        val (colorRes, progressValue, textLabel) = when (stage) {
            "unripe" -> Triple(R.color.design_default_color_error, 25, "Unripe")
            "slightly ripe" -> Triple(R.color.yellow, 50, "Slightly Ripe")
            "almost ripe" -> Triple(R.color.orange, 75, "Almost Ripe")
            "ripe" -> Triple(R.color.teal_700, 100, "Ripe")
            else -> Triple(R.color.gray, 0, "Unknown")
        }

        setRipeness(stateView, textLabel, colorRes)
        progressBar.progress = progressValue
        progressBar.progressDrawable.setTint(ContextCompat.getColor(this, colorRes))
        setEstimation(estimationView, stage)
    }

    // Manual Ripeness Assessment
    private fun handleSelfAssessment(
        variety: String,
        stateView: TextView,
        estimationText: TextView,
        button: Button,
        progressBar: ProgressBar
    ) {
        stateView.text = "Tap to assess manually"
        stateView.setTextColor(ContextCompat.getColor(this, R.color.black))
        estimationText.text = "Please assess to get proper estimate"
        estimationText.setTextColor(ContextCompat.getColor(this, R.color.brown))
        progressBar.progress = 0
        progressBar.progressDrawable.setTint(ContextCompat.getColor(this, R.color.teal_700))

        button.visibility = View.VISIBLE
        button.text = "ASSESS RIPENESS"
        button.setOnClickListener {
            showRipenessDialog(variety, stateView, estimationText, button, progressBar)
        }
    }

    // Dialog Assessment
    private fun showRipenessDialog(
        variety: String,
        stateView: TextView,
        estimationText: TextView,
        assessButton: Button,
        progressBar: ProgressBar
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ripeness_assessment, null)
        val spinnerFirm = dialogView.findViewById<Spinner>(R.id.spinnerFirmness)
        val spinnerAroma = dialogView.findViewById<Spinner>(R.id.spinnerAroma)

        val firmnessOptions = arrayOf("Very hard", "Firm (slightly yielding)", "Firm but gives easily", "Soft")
        val aromaOptions = arrayOf("None", "Very faint", "Mild", "Strong")

        spinnerFirm.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, firmnessOptions)
        spinnerAroma.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, aromaOptions)

        AlertDialog.Builder(this)
            .setTitle("Assess Ripeness for $variety")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val firmIndex = spinnerFirm.selectedItemPosition
                val aromaIndex = spinnerAroma.selectedItemPosition

                val result = when {
                    aromaIndex == 3 && firmIndex == 3 -> Triple("Ripe", R.color.teal_700, "ripe")
                    aromaIndex == 3 || firmIndex == 3 -> Triple("Almost Ripe", R.color.orange, "almost ripe")
                    aromaIndex in 1..2 || firmIndex in 1..2 -> Triple("Slightly Ripe", R.color.yellow, "slightly ripe")
                    aromaIndex == 0 && firmIndex == 0 -> Triple("Unripe", R.color.design_default_color_error, "unripe")
                    else -> Triple("Unknown", R.color.gray, "unknown")
                }

                stateView.text = result.first
                stateView.setTextColor(ContextCompat.getColor(this, result.second))
                setEstimation(estimationText, result.third)

                progressBar.progress = when (result.third) {
                    "unripe" -> 25
                    "slightly ripe" -> 50
                    "almost ripe" -> 75
                    "ripe" -> 100
                    else -> 0
                }
                progressBar.progressDrawable.setTint(ContextCompat.getColor(this, result.second))
                assessButton.text = "ASSESS AGAIN?"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Utility Display Helpers
    private fun setRipeness(stateView: TextView, text: String, colorRes: Int) {
        stateView.text = text
        stateView.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun clearRipeness(
        stateView: TextView,
        estimationText: TextView,
        button: Button,
        progressBar: ProgressBar
    ) {
        stateView.text = "Ripeness not tracked for this variety"
        stateView.setTextColor(ContextCompat.getColor(this, R.color.black))
        estimationText.text = ""
        button.visibility = View.GONE
        progressBar.progress = 0
        progressBar.progressDrawable.setTint(ContextCompat.getColor(this, R.color.gray))
    }

    private fun setEstimation(estimationText: TextView, stage: String) {
        val estimateText = when (stage.lowercase(Locale.getDefault())) {
            "unripe" -> "Estimated 7–10 days to full ripeness.\nStore at room temperature."
            "slightly ripe" -> "Estimated 4–6 days to full ripeness.\nKeep at room temperature."
            "almost ripe" -> "Estimated 1–3 days to fully ripen.\nKeep at room temperature."
            "ripe" -> "Mango is ripe. No estimation needed."
            else -> ""
        }
        estimationText.text = estimateText
        estimationText.setTextColor(ContextCompat.getColor(this, R.color.brown))
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK)
        super.onBackPressed()
    }
}
