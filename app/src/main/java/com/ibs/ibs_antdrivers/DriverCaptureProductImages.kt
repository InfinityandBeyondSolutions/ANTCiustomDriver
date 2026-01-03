package com.ibs.ibs_antdrivers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import com.ibs.ibs_antdrivers.offlineupload.UploadImageEntity
import com.ibs.ibs_antdrivers.offlineupload.UploadStatus
import com.ibs.ibs_antdrivers.offlineupload.UploadWorkScheduler
import com.ibs.ibs_antdrivers.permissions.RuntimePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private lateinit var btnBack: ImageView

    private var storeIdBadge: TextView? = null
    private var imageCountBadge: TextView? = null
    private var emptyImages: TextView? = null
    private var storeMonogram: TextView? = null

    private val imageList = mutableListOf<Uri>()
    private var currentPhotoPath: String = ""

    private var isUploading: Boolean = false
    private var uploadingDialog: UploadingDialogFragment? = null

    private var pendingActionAfterPermission: (() -> Unit)? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingActionAfterPermission?.invoke()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_LONG).show()
            maybeShowOpenSettings("Camera permission is required to take pictures.")
        }
        pendingActionAfterPermission = null
    }

    private val requestNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Notifications are optional, but we nudge users towards enabling them.
            maybeShowOpenSettings("Please allow notifications so we can keep you updated.")
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                if (validateImageSize(requireContext(), it)) {
                    imageList.add(it)
                    imageAdapter.notifyDataSetChanged()
                    updateUiState()
                } else {
                    Toast.makeText(requireContext(), "Image must be under 5MB", Toast.LENGTH_SHORT).show()
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
                updateUiState()
            } else {
                Toast.makeText(requireContext(), "Image must be under 5MB", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var imageAdapter: ImageAdapter
    private lateinit var storeId: String
    private lateinit var storeName: String

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

        storeIdBadge = view.findViewById(R.id.storeIdBadge)
        imageCountBadge = view.findViewById(R.id.imageCountBadge)
        emptyImages = view.findViewById(R.id.emptyImages)
        storeMonogram = view.findViewById(R.id.storeMonogram)

        tvStoreName.text = storeName
        storeIdBadge?.text = storeId
        storeMonogram?.text = storeName.firstOrNull()?.uppercase() ?: "S"

        updateUiState()

        btnBack.setOnClickListener {
            if (isUploading) {
                Toast.makeText(requireContext(), "Upload in progress… please wait", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().popBackStack(R.id.navStore, false)
        }

        // Block system back while uploading.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isUploading) {
                        Toast.makeText(requireContext(), "Upload in progress… please wait", Toast.LENGTH_SHORT).show()
                    } else {
                        // Temporarily disable this callback and let NavController handle the back stack.
                        isEnabled = false
                        findNavController().popBackStack(R.id.navStore, false)
                    }
                }
            }
        )

        rvStoreImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        imageAdapter = ImageAdapter(imageList) { position ->
            if (isUploading) {
                Toast.makeText(requireContext(), "Upload in progress…", Toast.LENGTH_SHORT).show()
                return@ImageAdapter
            }
            imageList.removeAt(position)
            imageAdapter.notifyDataSetChanged()
            updateUiState()
        }
        rvStoreImages.adapter = imageAdapter

        btnAddImage.setOnClickListener {
            if (isUploading) {
                Toast.makeText(requireContext(), "Upload in progress…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        btnCaptureImage.setOnClickListener {
            if (isUploading) {
                Toast.makeText(requireContext(), "Upload in progress…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureCameraPermissionThenLaunch()
        }

        btnUploadImages.setOnClickListener {
            if (isUploading) return@setOnClickListener
            if (FirebaseAuth.getInstance().currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            queueImagesForBackgroundUpload()
        }

        // Gently prompt for notification permission (Android 13+) so background uploads can notify later.
        maybeRequestNotificationPermission()

        return view
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (RuntimePermissions.hasPostNotifications(requireContext())) return

        // Only request once user is in the app; you can move this to a splash/onboarding screen later.
        requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureCameraPermissionThenLaunch() {
        if (RuntimePermissions.hasCamera(requireContext())) {
            launchCamera()
            return
        }

        pendingActionAfterPermission = { launchCamera() }

        // Rationale if needed.
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(requireContext())
                .setTitle("Camera permission")
                .setMessage("We need camera access so you can take product photos.")
                .setPositiveButton("Continue") { _, _ ->
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun maybeShowOpenSettings(message: String) {
        // If the user checked "Don't ask again", guide them to Settings.
        AlertDialog.Builder(requireContext())
            .setTitle("Permission needed")
            .setMessage(message)
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(RuntimePermissions.appSettingsIntent(requireContext()))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun updateUiState() {
        imageCountBadge?.text = imageList.size.toString()
        emptyImages?.visibility = if (imageList.isEmpty()) View.VISIBLE else View.GONE
        btnUploadImages.isEnabled = imageList.isNotEmpty() && !isUploading
        btnAddImage.isEnabled = !isUploading
        btnCaptureImage.isEnabled = !isUploading
    }

    private fun showUploadingModal(total: Int) {
        if (uploadingDialog?.isAdded == true) return

        val dialog = UploadingDialogFragment.newInstance()
        uploadingDialog = dialog
        dialog.show(parentFragmentManager, UploadingDialogFragment.TAG)
        dialog.updateProgress(0, total)
    }

    private fun hideUploadingModal() {
        val dialog = uploadingDialog
        uploadingDialog = null
        dialog?.dismissAllowingStateLoss()
    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}_"

        // Use app-private external files dir so images survive process death and are not cleared like cache.
        val storageDir: File = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: requireContext().filesDir

        val imageFile = File.createTempFile(fileName, ".jpg", storageDir)
        currentPhotoPath = imageFile.absolutePath

        val imageUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            cameraLauncher.launch(intent)
        } catch (_: SecurityException) {
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

    private fun queueImagesForBackgroundUpload() {
        if (imageList.isEmpty()) {
            Toast.makeText(requireContext(), "No images to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Please log in to upload images", Toast.LENGTH_LONG).show()
            return
        }

        isUploading = true
        updateUiState()
        showUploadingModal(imageList.size)

        viewLifecycleOwner.lifecycleScope.launch {
            val driverFullName = getDriverFullName()

            val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val timeFormatter = SimpleDateFormat("HHmmssSSS", Locale.getDefault())

            val enqueueItems = mutableListOf<UploadImageEntity>()

            withContext(Dispatchers.IO) {
                imageList.forEachIndexed { index, uri ->
                    val date = dateFormatter.format(Date())
                    val time = timeFormatter.format(Date(System.currentTimeMillis() + index))
                    val fileName = "${date}_${time}_${driverFullName}.jpg"

                    val localFile = copyUriToAppStorage(uri, fileName)
                    val remotePath = "store_images/$storeId/$fileName"

                    enqueueItems += UploadImageEntity(
                        storeId = storeId,
                        storeName = storeName,
                        driverUid = user.uid,
                        driverName = driverFullName,
                        localFilePath = localFile.absolutePath,
                        sourceUri = uri.toString(),
                        remotePath = remotePath,
                        status = UploadStatus.PENDING
                    )
                }

                AppDatabase.get(requireContext()).uploadImageDao().insertAll(enqueueItems)
            }

            if (!isAdded) return@launch

            // Kick off background worker (will run immediately if Wi‑Fi/unmetered is available).
            UploadWorkScheduler.enqueue(requireContext())

            // UI feedback: queued, clear list.
            hideUploadingModal()
            isUploading = false

            Toast.makeText(
                requireContext(),
                "Queued ${enqueueItems.size} images. They’ll upload automatically when internet is stable.",
                Toast.LENGTH_LONG
            ).show()

            imageList.clear()
            imageAdapter.notifyDataSetChanged()
            updateUiState()
            findNavController().popBackStack(R.id.navStore, false)
        }
    }

    private fun copyUriToAppStorage(sourceUri: Uri, fileName: String): File {
        val storageDir: File = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: requireContext().filesDir

        if (!storageDir.exists()) storageDir.mkdirs()

        val destFile = File(storageDir, fileName)

        requireContext().contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Unable to open input stream for $sourceUri" }
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        return destFile
    }

    // NOTE: Old uploadImagesToFirebase() kept intentionally unused; we now queue + upload via WorkManager.
}