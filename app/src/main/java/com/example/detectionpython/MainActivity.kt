package com.example.detectionpython

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.example.detectionpython.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    //private lateinit var resultTextView: TextView
    private  var progressBar: ProgressBar ?=null
    private var cameraLauncher: ActivityResultLauncher<Intent> ?=null
    var livenessValue: Int ?=null

    private val CAMERA_PERMISSION_CODE = 100

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    //    resultTextView = binding.resultTextView
        progressBar = findViewById(R.id.progressBar)

        val switchButton: Switch = binding.switchButton


        // Initialize ActivityResultLauncher
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
    //        handleCameraResult(result)
        }

        binding.takePictureButton.setOnClickListener {
            if (isCameraPermissionGranted()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        switchButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showCustomDialog(switchButton)
            }
            else {
                livenessValue = 0
            }
            }

    }

    private fun showCustomDialog(switchButton: Switch) {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_liveness, null)

        // Create the AlertDialog builder and set the custom layout
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)

        // Create the AlertDialog
        val dialog = builder.create()

        // Set the dialog's window background to transparent to avoid default border
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(false)

        val editTextValue = dialogView.findViewById<EditText>(R.id.editTextValue)
        val buttonOk = dialogView.findViewById<Button>(R.id.buttonOk)
        val buttonCancel = dialogView.findViewById<Button>(R.id.buttonCancel)

// Set "OK" button click listener
        buttonOk.setOnClickListener {
            val textValue = editTextValue.text.toString().trim() // Get and trim the text value

            if (textValue.isNotEmpty()) {
                val intValue = textValue.toIntOrNull()

                if (intValue != null) {
                    // Define min and max threshold values for clarity
                    val MIN_THRESHOLD = 20
                    val MAX_THRESHOLD = 150

                    if (intValue < MIN_THRESHOLD || intValue > MAX_THRESHOLD) {
                        Toast.makeText(
                            this,
                            "Threshold value must be between $MIN_THRESHOLD & $MAX_THRESHOLD",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        livenessValue = intValue
                        Toast.makeText(
                            this,
                            "Threshold value is: $livenessValue",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss() // Close the dialog if input is valid
                    }
                } else {
                    // Provide feedback for invalid number formats
                    Toast.makeText(this, "Invalid number format!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Provide feedback for empty input
                Toast.makeText(this, "Please enter a threshold value!", Toast.LENGTH_SHORT).show()
            }
        }


        // Set "Cancel" button click listener
        buttonCancel.setOnClickListener {
            switchButton.isChecked = false // Turn off switch if user cancels
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
        Log.v("liveness value", livenessValue.toString())
    }



    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("source", "mainactivity")
        intent.putExtra("liveness", livenessValue) // Second value


        cameraLauncher?.launch(intent)
    }

//    private fun handleCameraResult(result: ActivityResult) {
//        if (result.resultCode == RESULT_OK) {
//            val imagePath = result.data?.getStringExtra("capturedImagePath")
//            if (imagePath != null) {
//                val imageFile = File(imagePath)
//                if (imageFile.exists()) {
//                    val tempImageFile = File(cacheDir, "captured_image.jpg")
//                    imageFile.copyTo(tempImageFile, overwrite = true)
//                    correctImageOrientationAndSave(tempImageFile)
//
//                    CoroutineScope(Dispatchers.IO).launch {
//                        processImage(tempImageFile)
//                    }
//                } else {
//                    Log.e("IntentDataError", "Image file does not exist at $imagePath")
//                }
//            } else {
//                Log.e("IntentDataError", "Image path not found in result data")
//            }
//        } else {
//            Log.e("IntentDataError", "Camera result not OK")
//        }
//    }
//
//
//    private fun correctImageOrientationAndSave(imageFile: File) {
//        try {
//            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
//            val correctedBitmap = correctImageOrientation(bitmap, imageFile.absolutePath)
//
//            FileOutputStream(imageFile).use { out ->
//                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
//            }
//
//            Log.d("ImageCorrection", "Image successfully corrected and saved at ${imageFile.absolutePath}")
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
//        }
//    }
//
//    private fun correctImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
//        val exif = ExifInterface(imagePath)
//        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
//
//        val matrix = Matrix()
//        when (orientation) {
//            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
//            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
//            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
//        }
//
//        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//    }
//
//    private suspend fun processImage(imageFile: File) {
//        runOnUiThread {
//            progressBar.visibility = ProgressBar.VISIBLE
//        }
//
//        val python = Python.getInstance()
//        val pythonModule = python.getModule("identification")
//
//        if (pythonModule == null) {
//            Log.e("PythonError", "Failed to load Python module")
//            runOnUiThread {
//                progressBar.visibility = ProgressBar.GONE
//                Toast.makeText(this, "Failed to load Python module", Toast.LENGTH_LONG).show()
//            }
//            return
//        }
//
//        val encodingFile = File(filesDir, "face_data.pkl")
//        if (!encodingFile.exists()) {
//            copyAssetToFile("face_data.pkl", encodingFile)
//        }
//
//        if (encodingFile.canRead() && encodingFile.canWrite()) {
//            Log.d("FileCheck", "Encoding file permissions are OK.")
//        } else {
//            Log.e("FileCheck", "Encoding file permissions are not sufficient.")
//            runOnUiThread {
//                progressBar.visibility = ProgressBar.GONE
//                resultTextView.text = "Error: Insufficient permissions for encoding file."
//            }
//            return
//        }
//
//        if (imageFile.exists()) {
//            Log.d("FileCheck", "Image file exists at ${imageFile.absolutePath}")
//        } else {
//            Log.e("FileCheck", "Image file does not exist at ${imageFile.absolutePath}")
//            runOnUiThread {
//                progressBar.visibility = ProgressBar.GONE
//                resultTextView.text = "Error: Image file does not exist."
//            }
//            return
//        }
//
//        val fileSize = imageFile.length()
//        Log.d("FileCheck", "Image file size: $fileSize bytes")
//        if (fileSize == 0L) {
//            Log.e("FileCheck", "Image file is empty")
//            runOnUiThread {
//                progressBar.visibility = ProgressBar.GONE
//                resultTextView.text = "Error: Image file is empty."
//            }
//            return
//        }
//
//        try {
//            Log.d("PythonExecution liveness value", livenessValue.toString())
//            val result: PyObject = withContext(Dispatchers.IO) {
//                pythonModule.callAttr("process_image", imageFile.absolutePath, encodingFile.absolutePath, livenessValue)
//            }
//
//            val resultJson = result.toString() // Ensure the result is a JSON string
//            Log.d("PythonResult", "Received result: $resultJson")
//
//            runOnUiThread {
//                try {
//                    val jsonObject = JSONObject(resultJson)
//                    val status = jsonObject.optString("status", "unknown")
//                    val message = jsonObject.optString("message", "No message")
//                    val timeAttendance = jsonObject.optString("attendance_time", "No time attendance")
//                    val lightThreshold = jsonObject.optDouble("light_threshold", 0.0)
//                    val recognitionThreshold = jsonObject.optDouble("recognition_threshold", 0.0)
//                    val livenessVariance = jsonObject.optDouble("liveness_variance",0.0)
//                    val id = jsonObject.optInt("id", -1)  // Get the ID, default to -1 if not present
//
//                    // Round numbers to two decimal places
//                    val roundedLightThreshold = String.format("%.2f", lightThreshold)
//                    val roundedRecognitionThreshold = String.format("%.2f", recognitionThreshold)
//                    val roundedLivenessVariance = String.format("%.2f", livenessVariance)
//
//                    // Adding log for time attendance
//                    Log.d("PythonResult", "Time Attendance: $timeAttendance")
//                    Log.d("PythonResult", "Light Threshold: $lightThreshold")
//                    Log.d("PythonResult", "Recognition Threshold: $recognitionThreshold")
//                    Log.d("PythonResult", "ID: $id")
//
//                    val idText = if (status == "success" && id != -1) "ID: $id" else "ID: Not Available"
//
//                    resultTextView.text = when (status) {
//                        "error" -> "$message\n$idText\nLight Threshold: $roundedLightThreshold\nRecognition Threshold: $roundedRecognitionThreshold\nLiveness Variance: $roundedLivenessVariance"
//                        "success" -> "$message\n$idText\nTime Attendance: $timeAttendance\nLight Threshold: $roundedLightThreshold\nRecognition Threshold: $roundedRecognitionThreshold\nLiveness Variance: $roundedLivenessVariance"
//                        else -> "Unknown status: $status\nLight Threshold: $roundedLightThreshold\nRecognition Threshold: $roundedRecognitionThreshold\nLiveness Variance: $roundedLivenessVariance\n$idText"
//                    }
//                } catch (e: Exception) {
//                    Log.e("JsonParsingError", "Failed to parse JSON result: ${e.message}")
//                    resultTextView.text = "Error: Failed to parse JSON result."
//                }
//                progressBar.visibility = ProgressBar.GONE
//            }
//
//            Log.d("PythonExecution", "Python function execution completed")
//
//        } catch (e: Exception) {
//            Log.e("PythonError", "Python function execution failed: ${e.message}")
//
//
//
//
//            runOnUiThread {
//                progressBar.visibility = ProgressBar.GONE
//                resultTextView.text = "Error: Python function execution failed."
//            }
//        }
//    }
//
//    private fun copyAssetToFile(assetName: String, file: File) {
//        try {
//            assets.open(assetName).use { inputStream ->
//                FileOutputStream(file).use { outputStream ->
//                    val buffer = ByteArray(1024)
//                    var length: Int
//                    while (inputStream.read(buffer).also { length = it } > 0) {
//                        outputStream.write(buffer, 0, length)
//                    }
//                }
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//            Log.e("FileCopy", "Failed to copy asset: ${e.message}")
//        }
//    }
}