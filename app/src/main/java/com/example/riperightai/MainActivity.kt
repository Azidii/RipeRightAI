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
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
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

        // --- UI component setup ---
        imageView = findViewById(R.id.imagePlaceholder)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnUpload = findViewById(R.id.btnUpload)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load default icon initially
        resetUI()

        // --- Permission handling ---
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) openCamera()
        }

        // --- Camera launcher ---
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && photoUri != null) {
                val uri = photoUri!!
                goToLoadingScreen(uri)
            }
        }

        // --- Gallery launcher ---
        galleryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedUri: Uri? = result.data?.data
                selectedUri?.let { uri ->
                    goToLoadingScreen(uri)
                }
            }
        }

        // --- Button listeners ---
        btnTakePhoto.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> openCamera()
                else -> permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnUpload.setOnClickListener {
            openGallery()
        }

        // --- Bottom navigation listener ---
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scan -> {
                    // Stay in MainActivity (scanner)
                    true
                }
                R.id.navigation_history -> {
                    // Go to HistoryActivity
                    val intent = Intent(this, HistoryActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                    true
                }
                else -> false
            }
        }

        // Select scan tab as default
        bottomNav.selectedItemId = R.id.navigation_scan
    }

    /** Refresh the UI whenever the user comes back from another activity **/
    override fun onResume() {
        super.onResume()
        resetUI() // Reinitialize icons and layout
    }

    /** Reset UI – show only camera icon placeholder **/
    private fun resetUI() {
        imageView.setImageResource(R.drawable.ic_camera_green)
    }

    /** Open device camera safely **/
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
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            cameraLauncher.launch(intent)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** Open gallery picker **/
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    /** Navigate to ActivityScanLoading, passing image Uri safely **/
    private fun goToLoadingScreen(imageUri: Uri) {
        val intent = Intent(this, ActivityScanLoading::class.java).apply {
            putExtra("image_uri", imageUri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        grantUriPermission(
            packageName,
            imageUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        startActivity(intent)
    }
}
