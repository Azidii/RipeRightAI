package com.example.riperightai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.*

class ActivityScanLoading : AppCompatActivity() {

    private var inferenceJob: Job? = null
    private var isTransitioning = false
    private val TAG = "RipeRightAI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_loading)

        val uriString = intent.getStringExtra("image_uri")
        if (uriString.isNullOrEmpty()) {
            Toast.makeText(this, "No image provided.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val imageUri = Uri.parse(uriString)

        inferenceJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val safeUri = copyImageToCache(imageUri)
                val result = detectMango(safeUri)

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && !isTransitioning) {
                        isTransitioning = true
                        val resultIntent = Intent(
                            this@ActivityScanLoading,
                            ActivityScanResult::class.java
                        ).apply {
                            putExtra("variety", result.variety)
                            putExtra("ripeness", result.ripeness)
                            putExtra("confidence", result.confidence)
                            putExtra("image_uri", safeUri.toString())
                        }

                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(resultIntent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }, 500)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Inference failed", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ActivityScanLoading,
                        "Scan failed: ${ex.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@ActivityScanLoading, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceJob?.cancel()
    }

    /** Copy selected image safely to internal cache **/
    private fun copyImageToCache(sourceUri: Uri): Uri {
        val inputStream = contentResolver.openInputStream(sourceUri)
            ?: throw Exception("Unable to open image from URI.")
        val outFile = File(cacheDir, "scan_image_${System.currentTimeMillis()}.jpg")
        inputStream.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(outFile)
    }

    /** Perform PyTorch YOLO inference for mango detection **/
    private fun detectMango(imageUri: Uri): MangoResult {
        val module = MangoClassifier.getModule(this)
        val imageFile = File(imageUri.path ?: throw Exception("Invalid file path."))
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw Exception("Failed to decode bitmap from URI.")

        // Default YOLO input size
        val inputSize = 640
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            floatArrayOf(0.0f, 0.0f, 0.0f),
            floatArrayOf(1.0f, 1.0f, 1.0f)
        )

        val outputTensor: Tensor = try {
            module.forward(IValue.from(inputTensor)).toTensor()
        } catch (e: Exception) {
            Log.e(TAG, "Model forward() failed", e)
            throw Exception("Model inference failed.")
        }

        val out = outputTensor.dataAsFloatArray
        if (out.isEmpty()) throw Exception("Model produced no detections.")

        // YOLO’s TorchScript output often has shape [1, N, 6] → x, y, w, h, conf, class
        val numValuesPerDetection = 6
        val numDetections = out.size / numValuesPerDetection
        var bestConfidence = 0f
        var bestClass = -1

        for (i in 0 until numDetections) {
            val offset = i * numValuesPerDetection
            val conf = out[offset + 4]
            val cls = out[offset + 5].toInt()
            if (conf > bestConfidence) {
                bestConfidence = conf
                bestClass = cls
            }
        }

        if (bestClass == -1) throw Exception("No mango detected.")

        // Map YOLO classes to your mango stages/varieties (adjust as per training labels)
        val labels = listOf(
            "Carabao_Unripe", "Carabao_Ripe",
            "Cebu_Unripe", "Cebu_Ripe",
            "Apple_Mango", "Indian", "Kabayo"
        )

        val rawLabel = labels.getOrElse(bestClass) { "Unknown" }
        val parts = rawLabel.split('_')
        val variety = parts.firstOrNull() ?: "Unknown"
        val ripeness = parts.drop(1).joinToString(" ").takeIf { it.isNotEmpty() }
        val confidence = "%.1f".format(bestConfidence * 100f)

        Log.d(TAG, "YOLO Prediction: $rawLabel ($confidence%)")

        return MangoResult("$variety Mango", ripeness, confidence)
    }

    data class MangoResult(
        val variety: String,
        val ripeness: String?,
        val confidence: String
    )
}
