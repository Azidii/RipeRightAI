package com.example.riperightai

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ActivityScanResult : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        // Retrieve values from ScanLoading
        val variety = intent.getStringExtra("variety") ?: "Unknown"
        val ripeness = intent.getStringExtra("ripeness") ?: "Unknown"
        val confidence = intent.getStringExtra("confidence") ?: "0"
        val imageUri = intent.getStringExtra("image_uri")

        // Connect UI
        val varietyName = findViewById<TextView>(R.id.varietyName)
        val ripenessState = findViewById<TextView>(R.id.ripenessState)
        val confidenceText = findViewById<TextView>(R.id.confidenceText)
        val ripenessProgressBar = findViewById<ProgressBar>(R.id.ripenessProgressBar)
        val ripenessPercentage = findViewById<TextView>(R.id.ripenessPercentage)
        val scannedImage = findViewById<ImageView>(R.id.scannedImage)

        // Display general info
        varietyName.text = variety
        confidenceText.text = "$confidence% confidence"

        // Show scanned image if URI is valid
        imageUri?.let {
            val uri = Uri.parse(it)
            scannedImage.setImageURI(uri)
        }

        // Handle ripeness logic for specific varieties
        val varietyLower = variety.lowercase()
        val ripenessLower = ripeness.lowercase()

        when {
            varietyLower.contains("cebu") -> {
                // Cebu has 4 ripeness stages
                when (ripenessLower) {
                    "unripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Unripe", R.color.design_default_color_error, 20)
                    "slightly ripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Slightly Ripe", R.color.yellow, 45)
                    "almost ripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Almost Ripe", R.color.orange, 70)
                    "ripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Ripe", R.color.teal_700, 100)
                    else -> clearRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage)
                }
            }

            varietyLower.contains("carabao") -> {
                // Carabao has 3 ripeness stages
                when (ripenessLower) {
                    "unripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Unripe", R.color.design_default_color_error, 33)
                    "almost ripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Almost Ripe", R.color.orange, 66)
                    "ripe" -> setRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage, "Ripe", R.color.teal_700, 100)
                    else -> clearRipenessUI(ripenessState, ripenessProgressBar, ripenessPercentage)
                }
            }

            else -> {
                // All other varieties → no ripeness tracking
                ripenessState.text = "Ripeness not tracked for this variety"
                ripenessState.setTextColor(ContextCompat.getColor(this, R.color.black))
                ripenessProgressBar.visibility = android.view.View.GONE
                ripenessPercentage.visibility = android.view.View.GONE
            }
        }
    }

    /** Helper: sets progress, text, and color for a known ripeness state. */
    private fun setRipenessUI(
        textView: TextView,
        progressBar: ProgressBar,
        percentageView: TextView,
        text: String,
        colorRes: Int,
        progress: Int
    ) {
        textView.text = text
        textView.setTextColor(ContextCompat.getColor(this, colorRes))
        progressBar.visibility = android.view.View.VISIBLE
        percentageView.visibility = android.view.View.VISIBLE
        progressBar.progress = progress
        percentageView.text = "$progress%"
    }

    /** Helper: hides ripeness UI when unavailable or unknown. */
    private fun clearRipenessUI(
        textView: TextView,
        progressBar: ProgressBar,
        percentageView: TextView
    ) {
        textView.text = "Unknown ripeness"
        textView.setTextColor(ContextCompat.getColor(this, R.color.black))
        progressBar.visibility = android.view.View.GONE
        percentageView.visibility = android.view.View.GONE
    }
}
