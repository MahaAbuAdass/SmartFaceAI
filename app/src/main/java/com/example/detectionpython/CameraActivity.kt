package com.example.detectionpython

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
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
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.example.detectionpython.databinding.ActivityCameraBinding
import com.example.detectionpython.face.FaceContourDetectionProcessor
import com.example.detectionpython.face.FaceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    private var progressBar: ProgressBar? = null
    private var handler: Handler? = null
    private var isImageCaptured = false
    private var isFromMainActivity = false  // Flag to check where camera is opened from
    private var isPopupVisible = false
    private var savedImage: String? = null
    private var resultTextView: TextView? = null
    private var userName: String? = null
    private var userId: String? = null
    var livenessValue: Int ?=null

    private var isCameraReady = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the "source" from the Intent
        val source = intent.getStringExtra("source")
        isFromMainActivity = source == "mainactivity"  // Set flag if opened from mainactivity

        livenessValue = intent.getIntExtra("liveness",0)


        outputDirectory = getOutputDirectory()
        handler = Handler(Looper.getMainLooper())
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkForPermission()

    }

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
                        imageCapture
                    )
                    preview?.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

                    enableFlash() // Enable flashlight after camera initialization
                    isCameraReady = true


                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }

            }, ContextCompat.getMainExecutor(this)
        )
    }

    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        val source = intent.getStringExtra("source") ?: ""
        return FaceContourDetectionProcessor(
            binding.graphicOverlayFinder,
            ::updateFaceStatus,
            source
        )
    }

    private fun updateFaceStatus(status: FaceStatus) {
        // Only update the face status if the camera is ready
        if (isCameraReady) {
            val source = intent.getStringExtra("source") ?: ""

            currentFaceStatus = status

            if (status == FaceStatus.VALID && !isPopupVisible) {
                isPopupVisible = true  // Set the flag to avoid multiple popups
                if (isFromMainActivity) {
                    showFaceDetectedPopup()
                }
            }

            when (source) {
                "registration" -> {
                    takePhotoWithCloseTheCamera()
                }

                "mainactivity" -> {
                    takePhotoWithoutCloseTheCamera()
                }
            }
        }
    }

    private fun takePhotoWithoutCloseTheCamera() {
        imageCapture?.let { imageCapture ->
            val photoFile = File(outputDirectory, "temp_image.jpg")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        Log.e("cameraaaaaaaa", "cameraaaaaaaa")

                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "Photo saved successfully: ${photoFile.absolutePath}")

                        savedImage = photoFile.absolutePath
                        processSavedImage(savedImage)


//                        val intent = Intent()
//                        intent.putExtra("capturedImagePath", photoFile.absolutePath)
//                        setResult(Activity.RESULT_OK, intent)
//                        handler.postDelayed({
//                            closeCamera()
//                        }, 1000) // Close camera after 1 second
                    }
                }
            )
        } ?: run {
            Log.e(TAG, "ImageCapture is not initialized")
        }
    }

    private fun takePhotoWithCloseTheCamera() {
        imageCapture?.let { imageCapture ->
            val photoFile = File(outputDirectory, "temp_image.jpg")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        Log.e("cameraaaaaaaa 222222", "cameraaaaaaaa2222222")

                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "Photo saved successfully: ${photoFile.absolutePath}")
                        val intent = Intent()
                        intent.putExtra("capturedImagePath", photoFile.absolutePath)
                        setResult(Activity.RESULT_OK, intent)

                        handler?.postDelayed({
                            closeCamera()
                        }, 1000) // Close camera after 1 second
                    }
                }
            )
        } ?: run {
            Log.e(TAG, "ImageCapture is not initialized")
        }
    }

    private fun processSavedImage(imagePath: String?) {
        if (imagePath != null) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val tempImageFile = File(cacheDir, "captured_image.jpg")
                imageFile.copyTo(tempImageFile, overwrite = true)
                correctImageOrientationAndSave(tempImageFile)

                CoroutineScope(Dispatchers.IO).launch {
                    processImage(tempImageFile)
                }
            } else {
                Log.e("IntentDataError", "Image file does not exist at $imagePath")
            }
        } else {
            Log.e("IntentDataError", "Image path not found in result data")
        }
    }


    private fun correctImageOrientationAndSave(imageFile: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            val correctedBitmap = correctImageOrientation(bitmap, imageFile.absolutePath)

            FileOutputStream(imageFile).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }

            Log.d(
                "ImageCorrection",
                "Image successfully corrected and saved at ${imageFile.absolutePath}"
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun processImage(imageFile: File) {
        Log.v("process image", "process image")
        runOnUiThread {
            progressBar?.visibility = ProgressBar.VISIBLE
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("identification")

        if (pythonModule == null) {
            Log.e("PythonError", "Failed to load Python module")
            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                Toast.makeText(this, "Failed to load Python module", Toast.LENGTH_LONG).show()
            }
            return
        }

        val encodingFile = File(filesDir, "face_data.pkl")
        if (!encodingFile.exists()) {
            copyAssetToFile("face_data.pkl", encodingFile)
        }

        if (encodingFile.canRead() && encodingFile.canWrite()) {
            Log.d("FileCheck", "Encoding file permissions are OK.")
        } else {
            Log.e("FileCheck", "Encoding file permissions are not sufficient.")
            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                //  resultTextView.text = "Error: Insufficient permissions for encoding file."
            }
            return
        }

        if (imageFile.exists()) {
            Log.d("FileCheck", "Image file exists at ${imageFile.absolutePath}")
        } else {
            Log.e("FileCheck", "Image file does not exist at ${imageFile.absolutePath}")
            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                //   resultTextView.text = "Error: Image file does not exist."
            }
            return
        }

        val fileSize = imageFile.length()
        Log.d("FileCheck", "Image file size: $fileSize bytes")
        if (fileSize == 0L) {
            Log.e("FileCheck", "Image file is empty")
            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                //      resultTextView.text = "Error: Image file is empty."
            }
            return
        }

        try {
            //   Log.d("PythonExecution liveness value", livenessValue.toString())
            val result: PyObject = withContext(Dispatchers.IO) {
                pythonModule.callAttr(
                    "process_image",
                    imageFile.absolutePath,
                    encodingFile.absolutePath,
                    livenessValue
                )
            }

            val resultJson = result.toString() // Ensure the result is a JSON string
            Log.d("PythonResult", "Received result: $resultJson")

            runOnUiThread {
                try {
                    val jsonObject = JSONObject(resultJson)
                    val status = jsonObject.optString("status", "unknown")
                    val message = jsonObject.optString("message", "No message")
                    val timeAttendance =
                        jsonObject.optString("attendance_time", "No time attendance")
                    val lightThreshold = jsonObject.optDouble("light_threshold", 0.0)
                    val recognitionThreshold = jsonObject.optDouble("recognition_threshold", 0.0)
                    val livenessVariance = jsonObject.optDouble("liveness_variance", 0.0)
                    val id =
                        jsonObject.optInt("id", -1)  // Get the ID, default to -1 if not present

                    // Round numbers to two decimal places
                    val roundedLightThreshold = String.format("%.2f", lightThreshold)
                    val roundedRecognitionThreshold = String.format("%.2f", recognitionThreshold)
                    val roundedLivenessVariance = String.format("%.2f", livenessVariance)

                    // Adding log for time attendance
                    Log.d("PythonResult", "Time Attendance: $timeAttendance")
                    Log.d("PythonResult", "Light Threshold: $lightThreshold")
                    Log.d("PythonResult", "Recognition Threshold: $recognitionThreshold")
                    Log.d("PythonResult", "ID: $id")

                    val idText =
                        if (status == "success" && id != -1) "ID: $id" else "ID: Not Available"

//                    when(status){
//                        "error" -> {
//                            userName = "error"
//                            userId = "error"
//                        }
//                        "success" -> {
//                            userName = message
//                            userId = idText
//                        }
//                        else -> {
//                            userName = "else"
//                            userId = "else"
//                        }
//
//                    }

                    userName = when (status) {
                        "error" -> "$message\n\n" +
                                "$idText\n\n"
//                                "Light Threshold: $roundedLightThreshold\n\n" +
//                                "Recognition Threshold: $roundedRecognitionThreshold\n\n" +
//                                "Liveness Variance: $roundedLivenessVariance"

                        "success" ->
                            "$message\n\n" +
                                    "$idText\n\n" +
                                    "Time: $timeAttendance\n\n"
//                                    "Light Threshold: $roundedLightThreshold\n\n" +
//                                    "Recognition Threshold: $roundedRecognitionThreshold\n\n" +
//                                    "Liveness Variance: $roundedLivenessVariance"

                        else -> "Unknown status: $status\n\n"
//                                "Light Threshold: $roundedLightThreshold\n\n" +
//                                "Recognition Threshold: $roundedRecognitionThreshold\n\n" +
//                                "Liveness Variance: $roundedLivenessVariance\n\n" +
//                                "$idText"
                    }
                } catch (e: Exception) {
                    Log.e("JsonParsingError", "Failed to parse JSON result: ${e.message}")
                    userName = "Error: Failed to parse JSON result."
                }
                progressBar?.visibility = ProgressBar.GONE
            }

            Log.d("PythonExecution", "Python function execution completed")

        } catch (e: Exception) {
            Log.e("PythonError", "Python function execution failed: ${e.message}")




            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                // resultTextView.text = "Error: Python function execution failed."
            }
        }
    }

    private fun copyAssetToFile(assetName: String, file: File) {
        try {
            assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("FileCopy", "Failed to copy asset: ${e.message}")
        }
    }

    @SuppressLint("SuspiciousIndentation", "MissingInflatedId")
    private fun showFaceDetectedPopup() {


        isPopupVisible = true // Set the flag to true while the popup is visible

        // Pause the camera before showing the popup
        pauseCamera()


        val dialogView = LayoutInflater.from(this).inflate(R.layout.identification_options, null)

        // Create the AlertDialog builder and set the custom layout
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)

        // Create the AlertDialog
        val dialog = builder.create()

        dialog.setCanceledOnTouchOutside(false)


        // resultTextView = dialogView.findViewById(R.id.result)


        val inBtn = dialogView.findViewById<Button>(R.id.btn_in)
        val outBtn = dialogView.findViewById<Button>(R.id.btn_out)
        val personalOutBtn = dialogView.findViewById<Button>(R.id.btn_personal_out)
        val sickOutBtn = dialogView.findViewById<Button>(R.id.btn_sick_out)
        val closeBtn = dialogView.findViewById<Button>(R.id.close)


        closeBtn.setOnClickListener {
            resumeCamera()
            isPopupVisible = false // Reset the flag after popup is dismissed
            dialog.dismiss()
        }

        inBtn.setOnClickListener {
            showWelcomePopup()
            dialog.dismiss()
        }
        outBtn.setOnClickListener {
            showWelcomePopup()
            dialog.dismiss()
        }
        personalOutBtn.setOnClickListener {
            showWelcomePopup()
            dialog.dismiss()
        }
        sickOutBtn.setOnClickListener {
            showWelcomePopup()
            dialog.dismiss()
        }



        dialog.setOnDismissListener {
            isPopupVisible = false // Ensure flag resets if popup is dismissed in any way
        }
        dialog.show()
    }

    @SuppressLint("MissingInflatedId")
    private fun showWelcomePopup() {
        if (isPopupVisible) return // Prevent multiple popups

        isPopupVisible = true // Set the flag to true while the popup is visible

        val dialogView = LayoutInflater.from(this).inflate(R.layout.welcome_dialog, null)

        // Create the AlertDialog builder and set the custom layout
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)

        // Create the AlertDialog
        val dialog = builder.create()

        dialog.setCanceledOnTouchOutside(false)

        val userNameDialog = dialogView.findViewById<TextView>(R.id.tv_name_dialog)
        //val userIdDialog = dialogView.findViewById<TextView>(R.id.tv_user_id)

        userNameDialog.text = userName
//        userIdDialog.text = userId


        handler?.postDelayed({
            dialog.dismiss()
            resumeCamera()
            isPopupVisible = false
        }, 1000) // Close camera after 10 second

        dialog.setOnDismissListener {
            isPopupVisible = false // Ensure flag resets if popup is dismissed in any way
        }
        dialog.show()
    }


    private fun pauseCamera() {
        // Unbind all camera use cases to pause the camera
        cameraProvider?.unbindAll()
    }

    private fun resumeCamera() {
        // Rebind the camera use cases to resume the camera
        startCamera()
    }

    private fun closeCamera() {
        camera?.cameraControl?.enableTorch(false) // Turn off the flashlight
        cameraProvider?.unbindAll()
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun getOutputDirectory(): File {
        return filesDir
    }

    private fun enableFlash() {
        camera?.cameraControl?.enableTorch(true)
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }
}
