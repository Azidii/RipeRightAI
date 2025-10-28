package com.example.riperightai

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

class ActivityScanResult : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_result)

        val variety = intent.getStringExtra("variety") ?: "Unknown"
        val ripeness = intent.getStringExtra("ripeness") ?: "Unknown"
        val confidenceRaw = intent.getStringExtra("confidence") ?: "0"
        val imageUri = intent.getStringExtra("image_uri")

        val varietyName = findViewById<TextView>(R.id.varietyName)
        val ripenessState = findViewById<TextView>(R.id.ripenessState)
        val confidenceText = findViewById<TextView>(R.id.confidenceText)
        val ripenessProgressBar = findViewById<ProgressBar>(R.id.ripenessProgressBar)
        val ripenessPercentage = findViewById<TextView>(R.id.ripenessPercentage)
        val scannedImage = findViewById<ImageView>(R.id.scannedImage)

        // --- Normalize confidence ---
        val confidenceVal = confidenceRaw.toFloatOrNull()?.coerceIn(0f, 100f)?.roundToInt() ?: 0
        confidenceText.text = "$confidenceVal% confidence"

        // --- Image preview (safe URI) ---
        imageUri?.let { runCatching { scannedImage.setImageURI(Uri.parse(it)) } }

        // --- Variety & ripeness text ---
        varietyName.text = variety
        val varietyLower = variety.lowercase()
        val ripenessLower = ripeness.lowercase()

        when {
            varietyLower.contains("cebu") -> {
                when (ripenessLower) {
                    "unripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Unripe", R.color.design_default_color_error, 20
                    )
                    "slightly ripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Slightly Ripe", R.color.yellow, 45
                    )
                    "almost ripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Almost Ripe", R.color.orange, 70
                    )
                    "ripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Ripe", R.color.teal_700, 100
                    )
                    else -> clearRipeness(ripenessState, ripenessProgressBar, ripenessPercentage)
                }
            }
            varietyLower.contains("carabao") -> {
                when (ripenessLower) {
                    "unripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Unripe", R.color.design_default_color_error, 33
                    )
                    "almost ripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Almost Ripe", R.color.orange, 66
                    )
                    "ripe" -> setRipeness(
                        ripenessState, ripenessProgressBar, ripenessPercentage,
                        "Ripe", R.color.teal_700, 100
                    )
                    else -> clearRipeness(ripenessState, ripenessProgressBar, ripenessPercentage)
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
        bar.visibility = View.VISIBLE
        percentView.visibility = View.VISIBLE
        bar.progress = clamped
        percentView.text = "$clamped%"
    }

    private fun clearRipeness(
        stateView: TextView,
        bar: ProgressBar,
        percentView: TextView
    ) {
        stateView.text = "Unknown ripeness"
        stateView.setTextColor(ContextCompat.getColor(this, R.color.black))
        bar.visibility = View.GONE
        percentView.visibility = View.GONE
    }
}
