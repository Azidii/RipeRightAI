package com.example.riperightai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var photoUri: Uri? = null

    private lateinit var imageView: ImageView
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var btnUpload: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI setup
        imageView = findViewById(R.id.imagePlaceholder)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnUpload = findViewById(R.id.btnUpload)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        resetUI()

        // Permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) openCamera()
        }

        // Camera launcher
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && photoUri != null) {
                goToLoadingScreen(photoUri!!)
            }
        }

        // Gallery launcher
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { goToLoadingScreen(it) }
            }
        }

        // Take Photo button
        btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Upload from gallery button
        btnUpload.setOnClickListener { openGallery() }

        // --- Bottom navigation ---
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scan -> true // already here

                R.id.navigation_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    startActivity(intent)
                    // Forward transition (Main → History)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    // Do *not* call finish() here — to avoid animation crash
                    true
                }

                else -> false
            }
        }

        // Default tab selection
        bottomNav.selectedItemId = R.id.navigation_scan
    }

    override fun onResume() {
        super.onResume()
        resetUI()
    }

    // Reset UI icon
    private fun resetUI() {
        imageView.setImageResource(R.drawable.ic_camera_green)
    }

    // Open camera
    private fun openCamera() {
        try {
            val imageFile = File.createTempFile("mango_photo_", ".jpg", cacheDir)
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                imageFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            cameraLauncher.launch(intent)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Open gallery
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galleryLauncher.launch(intent)
    }

    // Move to loading screen
    private fun goToLoadingScreen(imageUri: Uri) {
        val intent = Intent(this, ActivityScanLoading::class.java).apply {
            putExtra("image_uri", imageUri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        grantUriPermission(packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // Animate when user presses back (e.g., History → Main)
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
