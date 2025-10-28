package com.example.riperightai

import android.content.Context
import android.util.Log
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream

/** Singleton for model initialization using PyTorch Mobile (YOLO version) **/
object MangoClassifier {

    @Volatile
    private var module: Module? = null
    private const val MODEL_NAME = "my_model.torchscript"
    private const val TAG = "RipeRightModel"

    /** Returns the loaded PyTorch module, loading once if necessary */
    fun getModule(context: Context): Module {
        return module ?: synchronized(this) {
            module ?: loadModel(context).also { module = it }
        }
    }

    /** Loads TorchScript model from assets → internal storage → memory */
    private fun loadModel(context: Context): Module {
        val modelFile = File(context.filesDir, MODEL_NAME)

        // Copy from assets to internal storage if missing
        if (!modelFile.exists()) {
            Log.d(TAG, "📦 Copying YOLO model from assets...")
            context.assets.open(MODEL_NAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw Exception("❌ Model file missing or empty after copy.")
        }

        // Try loading into memory
        return try {
            val loaded = Module.load(modelFile.absolutePath)
            Log.d(TAG, "✅ Model loaded from: ${modelFile.absolutePath}")
            loaded
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Failed to load YOLO model.", e)
            throw Exception("Unable to initialize PyTorch model. ${e.message}")
        }
    }
}
