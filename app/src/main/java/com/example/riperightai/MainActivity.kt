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

        imageView = findViewById(R.id.imagePlaceholder)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnUpload = findViewById(R.id.btnUpload)

        // Load default icon initially
        resetUI()

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) openCamera()
        }

        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && photoUri != null) {
                val uri = photoUri!!
                goToLoadingScreen(uri)
            }
        }

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
    }

    /** Refresh the UI whenever the user comes back from another activity **/
    override fun onResume() {
        super.onResume()
        resetUI() // <--- Reinitialize icons and layout
    }

    /** Reset UI and keep only the camera icon placeholder **/
    private fun resetUI() {
        imageView.setImageResource(R.drawable.ic_camera_green)
    }

    /** Opens device camera safely **/
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

    /** Opens gallery picker **/
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    /** Navigates to ActivityScanLoading, passing image Uri safely **/
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
