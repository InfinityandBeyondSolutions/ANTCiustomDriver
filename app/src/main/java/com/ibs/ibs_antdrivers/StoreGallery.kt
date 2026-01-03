package com.ibs.ibs_antdrivers

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class StoreGallery : Fragment(), GalleryPreviewDialogFragment.Listener {

    private lateinit var tvStoreGalleryName: TextView
    private lateinit var tvPhotoCount: TextView
    private lateinit var rvStoreGalleryImages: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView

    private lateinit var selectionBar: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnShareSelected: View
    private lateinit var btnCancelSelection: View

    private lateinit var storeId: String
    private lateinit var storeName: String
    private lateinit var galleryImageAdapter: GalleryImageAdapter
    private val imageUrls = mutableListOf<String>()
    private val imageNames = mutableListOf<String>()
    private var nextPageToken: String? = null
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_store_gallery, container, false)

        storeId = arguments?.getString("storeId") ?: ""
        storeName = arguments?.getString("storeName") ?: "Unknown Store"

        tvStoreGalleryName = view.findViewById(R.id.tvStoreGalleryName)
        tvPhotoCount = view.findViewById(R.id.photoCount)
        rvStoreGalleryImages = view.findViewById(R.id.rvStoreGalleryImages)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.empty)
        btnBack = view.findViewById(R.id.ivBackButton)

        selectionBar = view.findViewById(R.id.selectionBar)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        btnShareSelected = view.findViewById(R.id.btnShareSelected)
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection)

        tvStoreGalleryName.text = storeName
        updateCountAndEmpty()

        btnBack.setOnClickListener {
            if (::galleryImageAdapter.isInitialized && galleryImageAdapter.isInSelectionMode()) {
                galleryImageAdapter.clearSelection()
            } else {
                findNavController().popBackStack(R.id.navStore, false)
            }
        }

        btnCancelSelection.setOnClickListener {
            galleryImageAdapter.clearSelection()
        }

        btnShareSelected.setOnClickListener {
            val items = galleryImageAdapter.getSelectedItems()
            if (items.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least 1 image to share", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareImages(items)
        }

        val spanCount = 2
        rvStoreGalleryImages.layoutManager = GridLayoutManager(requireContext(), spanCount)

        galleryImageAdapter = GalleryImageAdapter(
            imageUrls,
            imageNames,
            onImageClick = { index ->
                if (imageUrls.isEmpty()) return@GalleryImageAdapter
                GalleryPreviewDialogFragment
                    .newInstance(imageUrls, imageNames, index)
                    .show(childFragmentManager, "GalleryPreview")
            },
            onDownloadClick = { imageUrl, imageName ->
                downloadImage(imageUrl, imageName)
            },
            onSelectionChanged = { selectedCount ->
                selectionBar.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
                tvSelectedCount.text = "$selectedCount selected"
            }
        )

        rvStoreGalleryImages.adapter = galleryImageAdapter

        // Pagination when nearing bottom
        rvStoreGalleryImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return

                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && totalItemCount <= (lastVisibleItem + 6) && nextPageToken != null) {
                    fetchImagesFromFirebase()
                }
            }
        })

        fetchImagesFromFirebase()

        return view
    }

    private fun updateCountAndEmpty() {
        tvPhotoCount.text = imageUrls.size.toString()
        tvEmpty.visibility = if (!isLoading && imageUrls.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun fetchImagesFromFirebase() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        updateCountAndEmpty()

        MainScope().launch {
            try {
                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("store_images/$storeId")
                val maxResults = 20
                val currentPageToken = nextPageToken

                val listResult: com.google.firebase.storage.ListResult = if (currentPageToken == null) {
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

                updateCountAndEmpty()

                if (listResult.items.isEmpty() && imageUrls.isEmpty()) {
                    Toast.makeText(requireContext(), "No images found for this store", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("StoreGallery", "Failed to list images: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load images: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                updateCountAndEmpty()
            }
        }
    }

    override fun onDownloadRequested(imageUrl: String, imageName: String) {
        downloadImage(imageUrl, imageName)
    }

    override fun onShareRequested(imageUrl: String, imageName: String) {
        shareImages(listOf(imageUrl to imageName))
    }

    private fun downloadImage(imageUrl: String, imageName: String) {
        try {
            val safeName = imageName
                .replace("\u0000", "")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")

            val request = DownloadManager.Request(Uri.parse(imageUrl))
                .setTitle("Downloading image")
                .setDescription(safeName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_PICTURES,
                    "StoreGallery/$safeName"
                )

            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(requireContext(), "Download started…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("StoreGallery", "Failed to enqueue download for $imageName: ${e.message}")
            Toast.makeText(requireContext(), "Failed to download $imageName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImages(items: List<Pair<String, String>>) {
        MainScope().launch {
            try {
                progressBar.visibility = View.VISIBLE

                val files: List<File> = withContext(Dispatchers.IO) {
                    items.map { (url, name) ->
                        downloadForShare(url, name)
                    }
                }

                val uris = files.map { ShareUtils.fileToContentUri(requireContext(), it) }

                val subject = if (items.size == 1) {
                    items.first().second
                } else {
                    "${items.size} store images"
                }

                val intent = ShareUtils.createShareIntent(
                    context = requireContext(),
                    uris = uris,
                    subject = subject,
                    chooserTitle = "Share image${if (items.size > 1) "s" else ""}"
                )

                startActivity(intent)

                // If sharing from selection mode, clear selection afterwards.
                if (::galleryImageAdapter.isInitialized && galleryImageAdapter.isInSelectionMode()) {
                    galleryImageAdapter.clearSelection()
                }
            } catch (e: Exception) {
                Log.e("StoreGallery", "Share failed: ${e.message}")
                Toast.makeText(requireContext(), "Failed to share images", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun downloadForShare(imageUrl: String, imageName: String): File {
        val safeName = imageName
            .ifBlank { "image_${System.currentTimeMillis()}.jpg" }
            .replace("\u0000", "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")

        val picturesDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val outDir = File(picturesDir, "StoreGalleryShare")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, safeName)

        // If already downloaded, reuse.
        if (outFile.exists() && outFile.length() > 0) return outFile

        URL(imageUrl).openStream().use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }
}