package com.example.riperightai

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class ActivityScanResult : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        // --- Toolbar setup ---
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        // --- Retrieve data from Intent ---
        val variety = intent.getStringExtra("variety") ?: "Unknown"
        val ripeness = intent.getStringExtra("ripeness") ?: "Unknown"
        val confidenceRaw = intent.getStringExtra("confidence") ?: "0"
        val imageUri = intent.getStringExtra("image_uri")

        // --- Bind UI elements ---
        val varietyName = findViewById<TextView>(R.id.varietyName)
        val ripenessState = findViewById<TextView>(R.id.ripenessState)
        val confidenceText = findViewById<TextView>(R.id.confidenceText)
        val ripenessProgressBar = findViewById<ProgressBar>(R.id.ripenessProgressBar)
        val ripenessPercentage = findViewById<TextView>(R.id.ripenessPercentage)
        val scannedImage = findViewById<ImageView>(R.id.scannedImage)
        val estimationText = findViewById<TextView>(R.id.estimationText)
        val btnAssessRipeness = findViewById<Button>(R.id.btnAssessRipeness)

        // --- Normalize confidence ---
        val confidenceVal = confidenceRaw.toFloatOrNull()?.coerceIn(0f, 100f)?.roundToInt() ?: 0
        confidenceText.text = "$confidenceVal% confidence"

        // --- Image preview (safe) ---
        imageUri?.let { runCatching { scannedImage.setImageURI(Uri.parse(it)) } }

        // --- Assign variety ---
        varietyName.text = variety
        val varietyLower = variety.lowercase()
        val ripenessLower = ripeness.lowercase()

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

            // 🔹 Manual Assessment Varieties
            varietyLower.contains("kabayo") || varietyLower.contains("indian") || varietyLower.contains("apple") -> {
                ripenessState.text = "Tap to assess manually"
                ripenessState.setTextColor(ContextCompat.getColor(this, R.color.black))
                ripenessProgressBar.visibility = View.GONE
                ripenessPercentage.visibility = View.GONE

                // First button appearance: "Assess Ripeness"
                btnAssessRipeness.text = "Assess Ripeness"
                btnAssessRipeness.visibility = View.VISIBLE

                // Pass state of first assessment into dialog
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
    }

    // --- Manual Assessment Dialog ---
    // --- Manual Assessment Dialog (Realistic Firmness + Aroma) ---
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

        // Observable characteristics
        val firmnessOptions = arrayOf(
            "Very hard",
            "Firm (slightly yielding)",
            "Firm but gives easily",
            "Soft"
        )
        val aromaOptions = arrayOf(
            "None",
            "Very faint",
            "Mild",
            "Strong"
        )

        spinnerFirmness.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, firmnessOptions)
        spinnerAroma.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, aromaOptions)

        AlertDialog.Builder(this)
            .setTitle("Assess Ripeness for $variety")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val firmness = spinnerFirmness.selectedItemPosition
                val aroma = spinnerAroma.selectedItemPosition

                // Determine ripeness stage
                val result = when {
                    // Unripe (very hard + no aroma)
                    firmness == 0 && aroma == 0 ->
                        Triple("Unripe", R.color.design_default_color_error, 25)

                    // Slightly Ripe (one factor starts changing)
                    (firmness == 1 && aroma == 0) || (firmness == 0 && aroma == 1) ->
                        Triple("Slightly Ripe", R.color.yellow, 45)

                    // Ripe (soft + noticeable aroma)
                    firmness == 2 && aroma == 2 ->
                        Triple("Ripe", R.color.teal_700, 100)

                    // Everything in between
                    else ->
                        Triple("Almost Ripe", R.color.orange, 70)
                }


                setRipeness(stateView, bar, percentView, result.first, result.second, result.third)
                bar.visibility = View.VISIBLE
                percentView.visibility = View.VISIBLE
                setEstimation(estimationText, result.first)

                // After first submit → allow re‑assessment
                assessButton.text = "Assess again?"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Estimation Logic ---
    // --- Accurate Estimation Logic ---
    private fun setEstimation(estimationText: TextView, ripenessStage: String) {
        when (ripenessStage.lowercase()) {
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

    // --- Helper: set ripeness visuals ---
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

    // --- Helper: clear UI when ripeness unknown ---
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
