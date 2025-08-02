package com.ibs.ibs_antdrivers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriverCaptureProductImages : Fragment() {

    private lateinit var rvFrontSideImages: RecyclerView
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
        driverName = arguments?.getString("driverName") ?: ""
        storeName = arguments?.getString("storeName") ?: "Unknown Store"

        rvFrontSideImages = view.findViewById(R.id.rvFrontSideImages)
        btnAddImage = view.findViewById(R.id.btnAddImage)
        btnCaptureImage = view.findViewById(R.id.btnCaptureImage)
        btnUploadImages = view.findViewById(R.id.btnUploadImages)
        tvStoreName = view.findViewById(R.id.tvStoreName)
        btnBack = view.findViewById(R.id.ivBackButton)

        tvStoreName.text = storeName

        btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.navStore, false)
        }

        rvFrontSideImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        imageAdapter = ImageAdapter(imageList) { position ->
            imageList.removeAt(position)
            imageAdapter.notifyDataSetChanged()
        }
        rvFrontSideImages.adapter = imageAdapter

        btnAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        btnCaptureImage.setOnClickListener {
            launchCamera()
        }

        btnUploadImages.setOnClickListener {
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
        return sizeInBytes <= 2 * 1024 * 1024 // 2MB
    }

    private fun uploadImagesToFirebase() {
        if (imageList.isEmpty()) {
            Toast.makeText(requireContext(), "No images to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val storageRef = FirebaseStorage.getInstance().reference.child("store_images/$storeId")
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        var uploadCount = 0

        for (uri in imageList) {
            val timestamp = formatter.format(Date())
            val fileName = "${timestamp}_${driverName.replace(" ", "_")}.jpg"
            val fileRef = storageRef.child(fileName)

            fileRef.putFile(uri)
                .addOnSuccessListener {
                    uploadCount++
                    Toast.makeText(requireContext(), "Uploaded: $fileName", Toast.LENGTH_SHORT).show()
                    if (uploadCount == imageList.size) {
                        imageList.clear()
                        imageAdapter.notifyDataSetChanged()
                        Toast.makeText(requireContext(), "All images uploaded successfully", Toast.LENGTH_LONG).show()
                        findNavController().popBackStack(R.id.navStore, false)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed: $fileName", Toast.LENGTH_SHORT).show()
                }
        }
    }
}