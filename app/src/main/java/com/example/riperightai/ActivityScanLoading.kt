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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ActivityScanLoading : AppCompatActivity() {

    private var inferenceJob: Job? = null
    private var isTransitioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_loading)

        val uriString = intent.getStringExtra("image_uri")
        if (uriString.isNullOrEmpty()) {
            Log.e("RipeRightAI", "Missing image URI.")
            Toast.makeText(this, "No image provided.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val imageUri = Uri.parse(uriString)

        inferenceJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val safeUri = copyImageToInternalStorage(imageUri)
                val (variety, ripeness, confidence) = classifyMango(safeUri)

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && !isTransitioning) {
                        isTransitioning = true
                        val resultIntent = Intent(
                            this@ActivityScanLoading,
                            ActivityScanResult::class.java
                        ).apply {
                            putExtra("variety", variety)
                            putExtra("ripeness", ripeness)
                            putExtra("confidence", confidence)
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
                Log.e("RipeRightAI", "Inference failed", ex)
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceJob?.cancel()
    }

    /** Copies image safely to internal cache so URI never becomes invalid **/
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri {
        val inputStream: InputStream =
            contentResolver.openInputStream(sourceUri) ?: throw Exception("Cannot open image.")
        val outFile = File(cacheDir, "scan_image_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { output -> inputStream.copyTo(output) }
        inputStream.close()
        return Uri.fromFile(outFile)
    }

    /** Runs PyTorch model inference **/
    private fun classifyMango(imgUri: Uri): Triple<String, String?, String> {
        val module = MangoClassifier.getModule(this)

        val bitmap: Bitmap = BitmapFactory.decodeFile(File(imgUri.path!!).absolutePath)
            ?: throw Exception("Failed to decode bitmap from URI.")

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            bitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        val outputTensor: Tensor = try {
            module.forward(IValue.from(inputTensor)).toTensor()
        } catch (e: Exception) {
            Log.e("RipeRightAI", "Model forward() failed", e)
            throw Exception("Model inference failed.")
        }

        val scores = outputTensor.dataAsFloatArray
        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val confidence = "%.1f".format(scores[bestIndex] * 100f)

        val labels = listOf(
            "Apple_Mango",
            "Indian",
            "Kabayo",
            "Cebu_Unripe",
            "Cebu_Slightly_Ripe",
            "Cebu_Almost_Ripe",
            "Cebu_Ripe",
            "Carabao_Unripe",
            "Carabao_Almost_Ripe",
            "Carabao_Ripe"
        )

        val rawLabel = labels.getOrElse(bestIndex) { "Unknown" }
        val parts = rawLabel.split('_')
        val variety = parts.firstOrNull() ?: "Unknown"
        val ripeness = parts.drop(1).joinToString(" ").takeIf { it.isNotEmpty() }

        return Triple("$variety Mango", ripeness, confidence)
    }
}

/** Singleton loader for PyTorch model **/
object MangoClassifier {
    @Volatile
    private var module: Module? = null

    fun getModule(context: Context): Module {
        return module ?: synchronized(this) {
            module ?: loadModule(context).also { module = it }
        }
    }

    private fun loadModule(context: Context): Module {
        val modelFile = File(context.filesDir, "model_mobile.ptl")
        if (!modelFile.exists()) {
            context.assets.open("model_mobile.ptl").use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
        }
        return Module.load(modelFile.absolutePath)
    }
}
