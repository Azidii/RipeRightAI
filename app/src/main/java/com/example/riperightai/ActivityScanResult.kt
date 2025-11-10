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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale

class ActivityScanResult : AppCompatActivity() {

    private var variety: String = "Unknown"
    private var ripeness: String = "Unknown"
    private var confidenceRaw: String = "0"
    private var imageUri: String? = null
    private var alreadySaved = false
    private val tag = "FirestoreSave"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        // --- Retrieve data from Intent ---
        variety = intent.getStringExtra("variety") ?: "Unknown"
        ripeness = intent.getStringExtra("ripeness") ?: "Unknown"
        confidenceRaw = intent.getStringExtra("confidence") ?: "0"
        imageUri = intent.getStringExtra("image_uri")

        // --- Bind UI elements ---
        val varietyName = findViewById<TextView>(R.id.varietyName)
        val ripenessState = findViewById<TextView>(R.id.ripenessState)
        val confidenceText = findViewById<TextView>(R.id.confidenceText)
        val ripenessProgressBar = findViewById<ProgressBar>(R.id.ripenessProgressBar)
        val ripenessPercentage = findViewById<TextView>(R.id.ripenessPercentage)
        val scannedImage = findViewById<ImageView>(R.id.scannedImage)
        val estimationText = findViewById<TextView>(R.id.estimationText)
        val btnAssessRipeness = findViewById<Button>(R.id.btnAssessRipeness)
        val selfAssessmentNote = findViewById<TextView>(R.id.selfAssessmentNote)

        // --- Normalize confidence ---
        val confidenceVal = confidenceRaw.toFloatOrNull()?.coerceIn(0f, 100f)?.roundToInt() ?: 0
        confidenceText.text = "$confidenceVal% confidence"

        // --- Load image if available ---
        imageUri?.let { runCatching { scannedImage.setImageURI(Uri.parse(it)) } }

        // --- Assign variety details ---
        varietyName.text = variety
        val varietyLower = variety.lowercase(Locale.ROOT)
        val ripenessLower = ripeness.lowercase(Locale.ROOT)

        // --- Conditional note visibility for manual-assessment varieties (broader match) ---
        if (
            varietyLower.contains("kabayo") ||
            varietyLower.contains("indian") ||
            varietyLower.contains("apple")
        ) {
            selfAssessmentNote.visibility = View.VISIBLE
        } else {
            selfAssessmentNote.visibility = View.GONE
        }

        when {
            varietyLower.contains("cebu") -> {
                when (ripenessLower) {
                    "unripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Unripe", R.color.design_default_color_error, 20)
                        setEstimation(estimationText, "Unripe")
                    }
                    "slightly ripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Slightly Ripe", R.color.yellow, 45)
                        setEstimation(estimationText, "Slightly Ripe")
                    }
                    "almost ripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Almost Ripe", R.color.orange, 70)
                        setEstimation(estimationText, "Almost Ripe")
                    }
                    "ripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Ripe", R.color.teal_700, 100)
                        setEstimation(estimationText, "Ripe")
                    }
                    else -> clearRipeness(ripenessState, ripenessProgressBar, ripenessPercentage)
                }
            }

            varietyLower.contains("carabao") -> {
                when (ripenessLower) {
                    "unripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Unripe", R.color.design_default_color_error, 33)
                        setEstimation(estimationText, "Unripe")
                    }
                    "almost ripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Almost Ripe", R.color.orange, 66)
                        setEstimation(estimationText, "Almost Ripe")
                    }
                    "ripe" -> {
                        setRipeness(ripenessState, ripenessProgressBar, ripenessPercentage, "Ripe", R.color.teal_700, 100)
                        setEstimation(estimationText, "Ripe")
                    }
                    else -> clearRipeness(ripenessState, ripenessProgressBar, ripenessPercentage)
                }
            }

            // Manual-assessment varieties
            varietyLower.contains("kabayo") || varietyLower.contains("indian") || varietyLower.contains("apple") -> {
                ripenessState.text = "Tap to assess manually"
                ripenessState.setTextColor(ContextCompat.getColor(this, R.color.black))
                ripenessProgressBar.visibility = View.GONE
                ripenessPercentage.visibility = View.GONE
                btnAssessRipeness.text = "Assess Ripeness"
                btnAssessRipeness.visibility = View.VISIBLE
                btnAssessRipeness.setOnClickListener {
                    showRipenessDialog(variety, ripenessState, ripenessProgressBar, ripenessPercentage, estimationText, btnAssessRipeness)
                }
            }

            else -> {
                ripenessState.text = "Ripeness not tracked for this variety"
                ripenessState.setTextColor(ContextCompat.getColor(this, R.color.black))
                ripenessProgressBar.visibility = View.GONE
                ripenessPercentage.visibility = View.GONE
            }
        }

        // --- Save the scan to Firestore ---
        saveToHistory()
    }

    /** Save scan details to Firestore **/
    private fun saveToHistory() {
        if (alreadySaved) return
        alreadySaved = true

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

            Log.d(tag, "Attempting to save to Firestore: $data")

            firestore.collection("scan_history").add(data)
                .addOnSuccessListener {
                    Log.d(tag, "✅ Successfully saved scan to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "❌ Firestore save failed: ${e.message}", e)
                    Toast.makeText(this, "Firestore save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(tag, "Exception during Firestore save", e)
            Toast.makeText(this, "Firestore exception: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --- Manual Assessment Dialog ---
    private fun showRipenessDialog(
        variety: String,
        stateView: TextView,
        bar: ProgressBar,
        percentView: TextView,
        estimationText: TextView,
        assessButton: Button
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ripeness_assessment, null)
        val spinnerFirmness = dialogView.findViewById<Spinner>(R.id.spinnerFirmness)
        val spinnerAroma = dialogView.findViewById<Spinner>(R.id.spinnerAroma)

        val firmnessOptions = arrayOf("Very hard", "Firm (slightly yielding)", "Firm but gives easily", "Soft")
        val aromaOptions = arrayOf("None", "Very faint", "Mild", "Strong")

        spinnerFirmness.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, firmnessOptions)
        spinnerAroma.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, aromaOptions)

        AlertDialog.Builder(this)
            .setTitle("Assess Ripeness for $variety")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val firmness = spinnerFirmness.selectedItemPosition
                val aroma = spinnerAroma.selectedItemPosition

                val result = when {
                    firmness == 0 && aroma == 0 -> Triple("Unripe", R.color.design_default_color_error, 25)
                    (firmness == 1 && aroma == 0) || (firmness == 0 && aroma == 1) ->
                        Triple("Slightly Ripe", R.color.yellow, 45)
                    firmness == 3 && aroma == 3 -> Triple("Ripe", R.color.teal_700, 100)
                    else -> Triple("Almost Ripe", R.color.orange, 70)
                }

                setRipeness(stateView, bar, percentView, result.first, result.second, result.third)
                bar.visibility = View.VISIBLE
                percentView.visibility = View.VISIBLE
                setEstimation(estimationText, result.first)
                assessButton.text = "Assess again?"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setEstimation(estimationText: TextView, ripenessStage: String) {
        when (ripenessStage.lowercase(Locale.ROOT)) {
            "unripe" -> {
                estimationText.text = "Estimated 7–10 days to full ripeness\nStore at room temperature for best results"
                estimationText.setTextColor(ContextCompat.getColor(this, R.color.brown))
            }
            "slightly ripe" -> {
                estimationText.text = "Estimated 4–6 days to full ripeness\nKeep at room temperature"
                estimationText.setTextColor(ContextCompat.getColor(this, R.color.brown))
            }
            "almost ripe" -> {
                estimationText.text = "Estimated 1–3 days to fully ripen\nKeep at room temperature"
                estimationText.setTextColor(ContextCompat.getColor(this, R.color.brown))
            }
            "ripe" -> {
                estimationText.text = "No estimation since it's Ripe already"
                estimationText.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            }
            else -> estimationText.text = ""
        }
    }

    private fun setRipeness(
        stateView: TextView,
        bar: ProgressBar,
        percentView: TextView,
        text: String,
        colorRes: Int,
        progress: Int
    ) {
        val clamped = min(max(progress, 0), 100)
        stateView.text = text
        stateView.setTextColor(ContextCompat.getColor(this, colorRes))
        bar.progress = clamped
        percentView.text = "$clamped%"
    }

    private fun clearRipeness(stateView: TextView, bar: ProgressBar, percentView: TextView) {
        stateView.text = "Unknown ripeness"
        stateView.setTextColor(ContextCompat.getColor(this, R.color.black))
        bar.visibility = View.GONE
        percentView.visibility = View.GONE
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK)
        super.onBackPressed()
    }
}
