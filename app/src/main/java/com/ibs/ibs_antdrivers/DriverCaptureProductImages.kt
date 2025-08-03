package com.ibs.ibs_antdrivers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DriverCaptureProductImages : Fragment() {

    private lateinit var rvStoreImages: RecyclerView
    private lateinit var btnAddImage: Button
    private lateinit var btnCaptureImage: Button
    private lateinit var btnUploadImages: Button
    private lateinit var tvStoreName: TextView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var storeId: String
    private lateinit var driverName: String
    private lateinit var storeName: String
    private lateinit var btnBack: ImageView
    private val imageList = mutableListOf<Uri>()
    private var currentPhotoPath: String = ""

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                if (validateImageSize(requireContext(), it)) {
                    imageList.add(it)
                    imageAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(requireContext(), "Image must be under 2MB", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = Uri.fromFile(File(currentPhotoPath))
            if (validateImageSize(requireContext(), imageUri)) {
                imageList.add(imageUri)
                imageAdapter.notifyDataSetChanged()
            } else {
                Toast.makeText(requireContext(), "Image must be under 2MB", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driver_capture_product_images, container, false)

        storeId = arguments?.getString("storeId") ?: ""
        storeName = arguments?.getString("storeName") ?: "Unknown Store"

        rvStoreImages = view.findViewById(R.id.rvStoreImages)
        btnAddImage = view.findViewById(R.id.btnAddImage)
        btnCaptureImage = view.findViewById(R.id.btnCaptureImage)
        btnUploadImages = view.findViewById(R.id.btnUploadImages)
        tvStoreName = view.findViewById(R.id.tvStoreName)
        btnBack = view.findViewById(R.id.ivBackButton)

        tvStoreName.text = storeName

        btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.navStore, false)
        }

        rvStoreImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        imageAdapter = ImageAdapter(imageList) { position ->
            imageList.removeAt(position)
            imageAdapter.notifyDataSetChanged()
        }
        rvStoreImages.adapter = imageAdapter

        btnAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        btnCaptureImage.setOnClickListener {
            launchCamera()
        }

        btnUploadImages.setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            uploadImagesToFirebase()
        }

        return view
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}_"
        val storageDir: File = requireContext().cacheDir
        val imageFile = File.createTempFile(fileName, ".jpg", storageDir)
        currentPhotoPath = imageFile.absolutePath

        val imageUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        }

        try {
            cameraLauncher.launch(intent)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun validateImageSize(context: Context, uri: Uri): Boolean {
        val inputStream = context.contentResolver.openInputStream(uri)
        val sizeInBytes = inputStream?.available() ?: 0
        inputStream?.close()
        return sizeInBytes <= 5 * 1024 * 1024 // 5MB
    }

    private suspend fun getDriverFullName(): String = suspendCoroutine { continuation ->
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            continuation.resume("Unknown_Driver")
            return@suspendCoroutine
        }

        FirebaseDatabase.getInstance().getReference("users/$userId")
            .get()
            .addOnSuccessListener { snapshot ->
                val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                val fullName = "$firstName $lastName".trim().ifEmpty { "Unknown_Driver" }
                continuation.resume(fullName.replace(" ", "_"))
            }
            .addOnFailureListener {
                Log.e("DriverCapture", "Failed to fetch driver name: ${it.message}")
                continuation.resume("Unknown_Driver")
            }
    }

    private fun uploadImagesToFirebase() {
        if (imageList.isEmpty()) {
            Toast.makeText(requireContext(), "No images to upload", Toast.LENGTH_SHORT).show()
            return
        }

        btnUploadImages.isEnabled = false // Disable button to prevent multiple clicks
        Toast.makeText(requireContext(), "Uploading images...", Toast.LENGTH_SHORT).show()

        // Launch coroutine to get driver name and upload images
        requireActivity().runOnUiThread {
            kotlinx.coroutines.MainScope().launch {
                val driverFullName = getDriverFullName()
                val storageRef = FirebaseStorage.getInstance().reference.child("store_images/$storeId")
                val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("HHmmssSSS", Locale.getDefault()) // Include milliseconds for uniqueness
                var uploadCount = 0
                var failureCount = 0

                imageList.forEachIndexed { index, uri ->
                    val date = dateFormatter.format(Date())
                    val time = timeFormatter.format(Date(System.currentTimeMillis() + index)) // Ensure unique timestamp
                    val fileName = "${date}_${time}_${driverFullName}.jpg"
                    val fileRef = storageRef.child(fileName)

                    Log.d("DriverCapture", "Uploading: store_images/$storeId/$fileName")

                    fileRef.putFile(uri)
                        .addOnSuccessListener {
                            uploadCount++
                            Toast.makeText(requireContext(), "Uploaded: $fileName", Toast.LENGTH_SHORT).show()
                            if (uploadCount + failureCount == imageList.size) {
                                finalizeUpload(uploadCount, failureCount)
                            }
                        }
                        .addOnFailureListener { exception ->
                            failureCount++
                            Log.e("DriverCapture", "Failed to upload $fileName: ${exception.message}")
                            Toast.makeText(requireContext(), "Failed: $fileName - ${exception.message}", Toast.LENGTH_SHORT).show()
                            if (uploadCount + failureCount == imageList.size) {
                                finalizeUpload(uploadCount, failureCount)
                            }
                        }
                }
            }
        }
    }

    private fun finalizeUpload(uploadCount: Int, failureCount: Int) {
        btnUploadImages.isEnabled = true
        imageList.clear()
        imageAdapter.notifyDataSetChanged()

        if (failureCount == 0) {
            Toast.makeText(requireContext(), "All $uploadCount images uploaded successfully", Toast.LENGTH_LONG).show()
            findNavController().popBackStack(R.id.navStore, false)
        } else {
            Toast.makeText(
                requireContext(),
                "$uploadCount images uploaded, $failureCount failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}