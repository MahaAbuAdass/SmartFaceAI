package com.example.detectionpython

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.detectionpython.databinding.ActivityCameraBinding
import com.example.detectionpython.face.FaceContourDetectionProcessor
import com.example.detectionpython.face.FaceStatus
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var outputDirectory: File
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelectorOption = CameraSelector.LENS_FACING_FRONT
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var currentFaceStatus: FaceStatus? = null
    private lateinit var handler: Handler
    private var isImageCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen orientation to portrait (or landscape if needed)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize output directory and executor
        outputDirectory = getOutputDirectory()
        handler = Handler(Looper.getMainLooper())
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request permissions
        checkForPermission()
    }

    // Method to check permissions (CAMERA permission)
    private fun checkForPermission() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    // Method to start the camera with correct configurations
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable {
                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder().build()

                // Set up the image analyzer (for face recognition or other use cases)
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, selectAnalyzer())
                    }

                // Configure ImageCapture with target rotation (to handle device orientation)
                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(window.decorView.rotation.toInt()) // Handle screen orientation changes
                    .build()

                // Set up the camera selector for front/back camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraSelectorOption)
                    .build()

                try {
                    cameraProvider?.unbindAll() // Unbind previous use cases
                    camera = cameraProvider?.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalyzer,
                        imageCapture
                    )
                    preview?.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

                    enableFlash() // Enable the flash if necessary

                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    // Method to enable the flash/torch
    private fun enableFlash() {
        camera?.cameraControl?.enableTorch(true)
    }

    // Method to choose the analyzer (face detection, etc.)
    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        val source = intent.getStringExtra("source") ?: ""
        return FaceContourDetectionProcessor(binding.graphicOverlayFinder, ::updateFaceStatus, source)
    }

    // Update face status and handle captured photo
    private fun updateFaceStatus(status: FaceStatus) {
        currentFaceStatus = status
        if (status == FaceStatus.VALID && !isImageCaptured) {
            isImageCaptured = true
            takePhoto()
        }
    }

    // Method to capture photo
    private fun takePhoto() {
        imageCapture?.let { imageCapture ->
            val photoFile = File(outputDirectory, "temp_image.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "Photo saved successfully: ${photoFile.absolutePath}")
                        val intent = Intent()
                        intent.putExtra("capturedImagePath", photoFile.absolutePath)
                        setResult(Activity.RESULT_OK, intent)
                        handler.postDelayed({
                            closeCamera() // Close the camera after capture
                        }, 1000)
                    }
                }
            )
        } ?: run {
            Log.e(TAG, "ImageCapture is not initialized")
        }
    }

    // Method to close the camera after capture
    private fun closeCamera() {
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        finish()
    }

    // Check if all required permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    // Helper function to get the output directory
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }
}
