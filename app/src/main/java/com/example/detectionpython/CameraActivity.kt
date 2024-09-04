package com.example.detectionpython

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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



    private val checkTooltipRunnable = object : Runnable {
        override fun run() {
            checkTooltipText()
            handler.postDelayed(this, 1000) // Check every 3 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)



        outputDirectory = getOutputDirectory()
        handler = Handler(Looper.getMainLooper())
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkForPermission()
    }

//    override fun onResume() {
//        super.onResume()
//        handler.post(checkTooltipRunnable) // Restart checking when activity resumes
//    }

//    override fun onPause() {
//        super.onPause()
//        handler.removeCallbacks(checkTooltipRunnable) // Stop checking when activity pauses
//    }

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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable {
                cameraProvider = cameraProviderFuture.get()

                preview = Preview.Builder().build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, selectAnalyzer())
                    }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraSelectorOption)
                    .build()

                try {
                    cameraProvider?.unbindAll()
                    camera = cameraProvider?.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalyzer,
                        imageCapture // Make sure to bind imageCapture
                    )
                    preview?.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }

            }, ContextCompat.getMainExecutor(this)
        )
    }

    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        val source = intent.getStringExtra("source")?:""

        return FaceContourDetectionProcessor(binding.graphicOverlayFinder, ::updateFaceStatus , source)
    }

    private fun updateFaceStatus(status: FaceStatus) {
        currentFaceStatus = status
        // Notify the callback about the updated face status
        if (status == FaceStatus.VALID && !isImageCaptured) {
            isImageCaptured = true
            takePhoto()
        }
    }

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
                            closeCamera()
                        }, 1000) // Close camera after 3 seconds
                    }
                }
            )
        } ?: run {
            Log.e(TAG, "ImageCapture is not initialized")
        }
    }

    private fun closeCamera() {
        cameraProvider?.unbindAll()
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkTooltipText() {
        val status = currentFaceStatus
        if (status != null && status == FaceStatus.VALID && !isImageCaptured) {
            Log.d(TAG, "Face status is VALID")
            // Trigger the photo capture if the status is valid
            updateFaceStatus(status)
        }
    }

    private fun getOutputDirectory(): File {
        return filesDir
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }
}
