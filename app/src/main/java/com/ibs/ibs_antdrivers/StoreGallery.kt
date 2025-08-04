package com.ibs.ibs_antdrivers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ListResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StoreGallery : Fragment() {

    private lateinit var tvStoreGalleryName: TextView
    private lateinit var rvStoreGalleryImages: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var storeId: String
    private lateinit var storeName: String
    private lateinit var galleryImageAdapter: GalleryImageAdapter
    private val imageUrls = mutableListOf<String>()
    private val imageNames = mutableListOf<String>()
    private var nextPageToken: String? = null
    private var isLoading = false

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) {
            Toast.makeText(requireContext(), "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Storage permission required to download images", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_store_gallery, container, false)

        storeId = arguments?.getString("storeId") ?: ""
        storeName = arguments?.getString("storeName") ?: "Unknown Store"

        tvStoreGalleryName = view.findViewById(R.id.tvStoreGalleryName)
        rvStoreGalleryImages = view.findViewById(R.id.rvStoreGalleryImages)
        progressBar = view.findViewById(R.id.progressBar)
        btnBack = view.findViewById(R.id.ivBackButton)

        tvStoreGalleryName.text = storeName

        btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.navStore, false)
        }

        rvStoreGalleryImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        galleryImageAdapter = GalleryImageAdapter(imageUrls, imageNames) { imageUrl, imageName ->
            downloadImage(imageUrl, imageName)
        }
        rvStoreGalleryImages.adapter = galleryImageAdapter

        // Add scroll listener for pagination
        rvStoreGalleryImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && totalItemCount <= (lastVisibleItem + 5) && nextPageToken != null) {
                    fetchImagesFromFirebase()
                }
            }
        })

        fetchImagesFromFirebase()

        return view
    }

    private fun fetchImagesFromFirebase() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        MainScope().launch {
            try {
                val storageRef = FirebaseStorage.getInstance().reference.child("store_images/$storeId")
                val maxResults = 10 // Fetch 10 images at a time
                // Capture nextPageToken to avoid smart cast issue
                val currentPageToken = nextPageToken
                val listResult: ListResult = if (currentPageToken == null) {
                    storageRef.list(maxResults).await()
                } else {
                    storageRef.list(maxResults, currentPageToken).await()
                }

                val tempUrls = mutableListOf<String>()
                val tempNames = mutableListOf<String>()

                for (item in listResult.items) {
                    try {
                        val uri = item.downloadUrl.await()
                        tempUrls.add(uri.toString())
                        tempNames.add(item.name)
                    } catch (e: Exception) {
                        Log.e("StoreGallery", "Failed to get download URL for ${item.name}: ${e.message}")
                    }
                }

                imageUrls.addAll(tempUrls)
                imageNames.addAll(tempNames)
                galleryImageAdapter.notifyDataSetChanged()

                nextPageToken = listResult.pageToken
                if (listResult.items.isEmpty() && imageUrls.isEmpty()) {
                    Toast.makeText(requireContext(), "No images found for this store", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("StoreGallery", "Failed to list images: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load images: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun downloadImage(imageUrl: String, imageName: String) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val localFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "StoreGallery_$imageName"
        )

        storageRef.getFile(localFile)
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "Image downloaded to ${localFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { exception ->
                Log.e("StoreGallery", "Failed to download $imageName: ${exception.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to download $imageName: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}