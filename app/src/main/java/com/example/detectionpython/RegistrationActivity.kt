package com.example.detectionpython

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.detectionpython.databinding.ActivityRegistrationBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.media.ExifInterface
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONObject

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_READ_EXTERNAL_STORAGE = 101
    private val REQUEST_READ_MEDIA_IMAGES = 102
    private val REQUEST_IMAGE_CAPTURE = 200
    private val REQUEST_IMAGE_PICK = 300
    private var photoCaptured: Boolean = false
    private var userName: String = "test"
    private var selectedImageUri: Uri? = null
    private var imageFilePath: String? = null  // Store the image file path here
    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userName = binding.etName.text.toString().trim()

        // Request permissions
        checkAndRequestPermissions()

        // Button to take a picture
        binding.btnTakeImage.setOnClickListener {
            if (hasCameraPermission()) {
                openCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            }
        }

        // Button to select image from gallery
        binding.btnGallery.setOnClickListener {
            if (hasGalleryPermission()) {
                openGalleryToSelectImage()
            } else {
                requestGalleryPermissions()
            }
        }

        // Button to register
        binding.btnRegister.setOnClickListener {
            userName = binding.etName.text.toString().trim()
            if (userName.isEmpty()) {
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!photoCaptured && selectedImageUri == null) {
                Toast.makeText(
                    this,
                    "Please take a picture or select an image from the gallery.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val userIdText = binding.etUserid.text.toString().trim()

            if (userIdText.isEmpty()) {
                Toast.makeText(this, "Please enter your ID.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                enrollUser(userIdText)
            }
        }
    }

    private suspend fun enrollUser(userIdText: String) {
        userId = userIdText.toIntOrNull() ?: 0

        // Save the image with the correct filename
        imageFilePath = File(filesDir, "$userName.jpg").absolutePath

        if (photoCaptured) {
            saveImageToPath(imageFilePath!!)
        } else if (selectedImageUri != null) {
            saveImageFromUriToPath(selectedImageUri!!, imageFilePath!!)
        }

        Log.v("img file path", imageFilePath ?: "No file path")

        // Show loader on the main thread
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = android.view.View.VISIBLE
        }

        val checkUserId = checkUserId(imageFilePath ?: "")

        // Hide loader on the main thread
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = android.view.View.GONE
        }

        if (checkUserId.contains("already exists", ignoreCase = true)) {
            showConfirmationPopup(userId)
        } else {
            addNewUser()
        }
    }




    private suspend fun addNewUser() {

        val updateMessage = updateFaceEncodings(imageFilePath ?: "")
        withContext(Dispatchers.Main) {
            // Hide loader
            binding.progressBar.visibility = android.view.View.GONE

            Toast.makeText(this@RegistrationActivity, updateMessage, Toast.LENGTH_LONG).show()

            // Proceed to the next activity only if the update was successful
            if (updateMessage.contains("pdated face for user ID", ignoreCase = true)) {
                val intent = Intent(this@RegistrationActivity, MainActivity::class.java)
                startActivity(intent)
                finish()  // Optional: Close RegistrationActivity
            }
        }
    }

    private fun showConfirmationPopup(userId: Int) {
        // Switch to the main thread to interact with UI components like AlertDialog
        CoroutineScope(Dispatchers.Main).launch {
            val dialogView = LayoutInflater.from(this@RegistrationActivity).inflate(R.layout.existing_id, null)

            val builder = AlertDialog.Builder(this@RegistrationActivity)
                .setView(dialogView)

            val dialog = builder.create()

            dialog.setCanceledOnTouchOutside(false)

            val title = dialogView.findViewById<TextView>(R.id.title)
            val noBtn = dialogView.findViewById<Button>(R.id.btn_no)
            val yesBtn = dialogView.findViewById<Button>(R.id.btn_yes)

            title.text = "Update Face for ID: $userId"

            noBtn.setOnClickListener {
                dialog.dismiss()
            }

            yesBtn.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    addNewUser()
                }
                dialog.dismiss()
            }

            dialog.show()
        }
    }



    private suspend fun checkUserId(photoPath: String): String {
        val encodingFile = File(filesDir, "face_data.pkl")
        if (!encodingFile.exists() || encodingFile.length() == 0L) {
            copyAssetToFile("face_data.pkl", encodingFile)
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("enrollment")

        return try {
            val result: PyObject = withContext(Dispatchers.IO) {
                userId?.let { Log.v("user id:", it.toString()) }

                pythonModule.callAttr("IDcheck", encodingFile.absolutePath, userId, photoPath)

            }

            val response = result.toString()  // Convert the response to a String
            val jsonResponse = JSONObject(response)
            val message = jsonResponse.optString("message", "No message field in response")

            Log.d("UpdateFaceEncodings", "Response from Python: $message")
            Log.d("UpdateFaceEncodings", "face path $photoPath")
            Log.d("UpdateFaceEncodings", "encoding file ${encodingFile.absolutePath}")

            message
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to update face encodings"
        }
    }

    private fun checkAndRequestPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }

        val readExternalStoragePermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        if (readExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasGalleryPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestGalleryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_READ_MEDIA_IMAGES
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("source", "registration")
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun openGalleryToSelectImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val imagePath = data?.getStringExtra("capturedImagePath")
                    if (imagePath != null) {
                        photoCaptured = true
                        correctImageOrientationAndSave(imagePath)
                        Log.v("paaath", imagePath)
                    }
                }

                REQUEST_IMAGE_PICK -> {
                    selectedImageUri = data?.data
                    if (selectedImageUri != null) {
                        // Save image from gallery to the same location as camera images
                        imageFilePath = File(filesDir, "temp_image.jpg").absolutePath
                        Log.v("imgfilepath", imageFilePath!!)
                        Log.v("paaath", imageFilePath!!)

                        correctImageOrientationAndSave(imageFilePath ?: "")
                        //  saveImageFromUriToPath(selectedImageUri!!, imageFilePath!!)

                    }
                }
            }
        }
    }

    private fun correctImageOrientationAndSave(photoPath: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            val correctedBitmap = correctImageOrientation(bitmap, photoPath)
            FileOutputStream(photoPath).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d("ImageCorrection", "Image successfully saved at $photoPath")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap, photoPath: String): Bitmap {
        val exif = ExifInterface(photoPath)
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

    private fun saveImageFromUriToPath(imageUri: Uri, path: String) {
        try {
            val file = File(path)
            // Ensure the parent directory exists
            file.parentFile?.mkdirs()

            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // First save the raw image
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            // Correct the orientation after saving the image
            correctImageOrientationAndSave(path)

            Log.d("ImageSave", "Image successfully saved at $path")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ImageSave", "Failed to save image: ${e.message}")
        }
    }

    private fun saveImageToPath(path: String) {
        try {
            val tempImageFile = File(filesDir, "temp_image.jpg")

            // *** Check if the file exists and log error if not ***
            if (!tempImageFile.exists()) {
                Log.e("ImageSave", "Temp image file does not exist")
                return
            }

            // *** Check if the file is empty ***
            if (tempImageFile.length() == 0L) {
                Log.e("ImageSave", "Temp image file is empty")
                return
            }

            // *** Downsample the image to reduce memory usage if it's large ***
            val options = BitmapFactory.Options()

            // First, decode the image with just the out parameters to get its dimensions
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(tempImageFile.absolutePath, options)

            // Calculate the sample size to scale the image down if it's too large
            val imageHeight = options.outHeight
            val imageWidth = options.outWidth
            val requiredHeight = 800 // You can adjust this as per your requirement
            val requiredWidth = 800 // You can adjust this as per your requirement
            var sampleSize = 1

            if (imageHeight > requiredHeight || imageWidth > requiredWidth) {
                val halfHeight = imageHeight / 2
                val halfWidth = imageWidth / 2

                // Calculate the largest sample size that fits the required dimensions
                while ((halfHeight / sampleSize) > requiredHeight && (halfWidth / sampleSize) > requiredWidth) {
                    sampleSize *= 2
                }
            }

            // Decode the image with the calculated sample size
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            val resizedBitmap = BitmapFactory.decodeFile(tempImageFile.absolutePath, options)

            // *** Check if BitmapFactory.decodeFile returns null ***
            if (resizedBitmap == null) {
                Log.e("ImageSave", "Failed to decode image at ${tempImageFile.absolutePath}")
                return
            }

            // *** Proceed with saving the bitmap to the target path ***
            FileOutputStream(File(path)).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            Log.d("ImageSave", "Image successfully saved at $path")

        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ImageSave", "Failed to save image: ${e.message}")
        }
    }


    private suspend fun updateFaceEncodings(photoPath: String): String {

        Log.v("path for the saved img", photoPath)
        val encodingFile = File(filesDir, "face_data.pkl")
        if (!encodingFile.exists() || encodingFile.length() == 0L) {
            copyAssetToFile("face_data.pkl", encodingFile)
        }

        Log.v("path for the saved img12 3", photoPath)


        val python = Python.getInstance()
        val pythonModule = python.getModule("enrollment")

        return try {
            val result: PyObject = withContext(Dispatchers.IO) {
                userId?.let { Log.v("user id:", it.toString()) }

                pythonModule.callAttr(
                    "update_face_encodings",
                    photoPath,
                    encodingFile.absolutePath,
                    userId
                )

            }

            val response = result.toString()  // Convert the response to a String
            val jsonResponse = JSONObject(response)
            val message = jsonResponse.optString("message", "No message field in response")

            Log.d("UpdateFaceEncodings", "Response from Python: $message")
            Log.d("UpdateFaceEncodings", "face path $photoPath")
            Log.d("UpdateFaceEncodings", "encoding file ${encodingFile.absolutePath}")

            message
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to update face encodings"
        }
    }

    private fun copyAssetToFile(assetFileName: String, outFile: File) {
        try {
            assets.open(assetFileName).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
