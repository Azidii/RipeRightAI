package com.example.riperightai

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.*
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class ActivityScanLoading : AppCompatActivity() {

    private var inferenceJob: Job? = null
    private var isTransitioning = false
    private var tflite: Interpreter? = null
    private val tag = "RipeRightAI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_loading)

        val uriString = intent.getStringExtra("image_uri")
        if (uriString.isNullOrEmpty()) {
            Toast.makeText(this, "No image provided.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val imageUri = Uri.parse(uriString)

        inferenceJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val safeUri = copyImageToCache(imageUri)

                tflite?.close()
                tflite = Interpreter(loadModelFile("best_float32_nms.tflite"))

                val result = detectMango(safeUri)

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && !isTransitioning) {
                        isTransitioning = true
                        val intent = Intent(this@ActivityScanLoading, ActivityScanResult::class.java)
                        intent.putExtra("variety", result.variety)
                        intent.putExtra("ripeness", result.ripeness)
                        intent.putExtra("confidence", result.confidence)
                        intent.putExtra("image_uri", result.imageUri)

                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(intent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }, 400)
                    }
                }
            } catch (ex: Exception) {
                Log.e(tag, "Inference failed", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ActivityScanLoading,
                        "Scan failed: ${ex.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@ActivityScanLoading, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            } finally {
                tflite?.close(); tflite = null
            }
        }
    }

    override fun onStop() {
        super.onStop()
        tflite?.close(); tflite = null
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceJob?.cancel()
        tflite?.close(); tflite = null
    }

    /** Copy selected image into cache for safe access **/
    private fun copyImageToCache(sourceUri: Uri): Uri {
        val inputStream = contentResolver.openInputStream(sourceUri)
            ?: throw Exception("Unable to open image from URI.")
        val outFile = File(cacheDir, "scan_image_${System.currentTimeMillis()}.jpg")
        inputStream.use { inp ->
            FileOutputStream(outFile).use { out -> inp.copyTo(out) }
        }
        return Uri.fromFile(outFile)
    }

    /** Map the .tflite file into memory **/
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fd = assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { stream ->
            val channel = stream.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /** Run inference for built‑in‑NMS YOLO model **/
    private fun detectMango(imageUri: Uri): MangoResult {
        val imageFile = File(imageUri.path ?: throw Exception("Invalid path"))
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw Exception("Failed to decode bitmap")

        val inputSize = 640
        val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // image pre-processing (do not touch)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }
        inputBuffer.rewind()

        val interpreter = tflite ?: throw Exception("Interpreter not initialized")
        val output = Array(1) { Array(300) { FloatArray(6) } }
        interpreter.run(inputBuffer, output)

        val labels = listOf(
            "Apple Mango", "Carabao",
            "Carabao_Almost_Ripe", "Carabao_Ripe", "Carabao_Unripe",
            "Cebu", "Cebu_Almost_Ripe", "Cebu_Ripe",
            "Cebu_Slightly_Ripe", "Cebu_Unripe",
            "Indian", "Kabayo"
        )

        val detections = output[0].filter { it[4] >= 0.25f }
        var bestDet = detections.maxByOrNull { it[4] } ?: throw Exception("No mango detected")
        var bestConf = bestDet[4]
        var clsIdx = bestDet[5].toInt()

        // ✅ Added block — allow merging parent + subset detections if overlapping
        val sorted = detections.sortedByDescending { it[4] }
        val overlapping = sorted.drop(1).find { iou(it, bestDet) > 0.5f }
        if (overlapping != null) {
            val mainLabel = labels.getOrElse(clsIdx) { "Unknown" }
            val overlapLabel = labels.getOrElse(overlapping[5].toInt()) { "Unknown" }
            val base1 = mainLabel.substringBefore("_")
            val base2 = overlapLabel.substringBefore("_")
            if (base1.equals(base2, ignoreCase = true)) {
                val parent = if (!mainLabel.contains("_")) mainLabel else overlapLabel
                val child = if (mainLabel.contains("_")) mainLabel else overlapLabel
                bestDet = if (child == overlapLabel) overlapping else bestDet
                bestConf = max(bestDet[4], overlapping[4])
                clsIdx = labels.indexOf(child)
                Log.d(tag, "✓ Merged detection: $parent + $child")
            }
        }
        // ✅ End of added logic

        val confidencePct = "%.1f".format(bestConf * 100f)
        val rawLabel = labels.getOrElse(clsIdx) { "Unknown" }

        var variety = rawLabel
        var ripeness = "Unknown"

        if (rawLabel.contains("_")) {
            val parts = rawLabel.split('_', limit = 2)
            variety = parts[0]
            ripeness = parts[1]
        }

        if (variety !in listOf("Cebu", "Carabao")) {
            ripeness = "Normal"
        } else {
            ripeness = when {
                "unripe" in ripeness.lowercase() -> "Unripe"
                "slightly" in ripeness.lowercase() -> "Slightly Ripe"
                "almost" in ripeness.lowercase() -> "Almost Ripe"
                "ripe" in ripeness.lowercase() -> "Ripe"
                else -> "Unknown"
            }
        }

        Log.d(tag, "✓ Detected → variety=$variety  ripeness=$ripeness  conf=$confidencePct%")
        return MangoResult(variety, ripeness, confidencePct, imageUri.toString())
    }

    /** IoU helper **/
    private fun iou(a: FloatArray, b: FloatArray): Float {
        val x1 = max(a[0], b[0])
        val y1 = max(a[1], b[1])
        val x2 = min(a[2], b[2])
        val y2 = min(a[3], b[3])
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        return inter / (areaA + areaB - inter + 1e-6f)
    }

    /** Result container **/
    data class MangoResult(
        val variety: String,
        val ripeness: String?,
        val confidence: String,
        val imageUri: String
    )
}
