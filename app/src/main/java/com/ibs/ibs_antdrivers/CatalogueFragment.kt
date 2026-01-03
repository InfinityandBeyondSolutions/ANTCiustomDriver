package com.ibs.ibs_antdrivers

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import java.io.File

class CatalogueFragment : Fragment() {

    private lateinit var pdfPageImageView: ImageView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var pageIndicator: TextView

    private lateinit var downloadButton: View
    private lateinit var shareButton: View
    private lateinit var hintText: TextView

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageIndex = 0
    private var totalPages = 0

    private val cachedCatalogueFile: File
        get() = File(requireContext().filesDir, "catalogue/current.pdf")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_catalogue, container, false)

        pdfPageImageView = view.findViewById(R.id.pdfPage)
        prevButton = view.findViewById(R.id.prevPage)
        nextButton = view.findViewById(R.id.nextPage)
        pageIndicator = view.findViewById(R.id.pageIndicator)

        downloadButton = view.findViewById(R.id.downloadCatalogue)
        shareButton = view.findViewById(R.id.shareCatalogue)
        hintText = view.findViewById(R.id.catalogueHint)

        prevButton.setOnClickListener { showPage(pageIndex - 1) }
        nextButton.setOnClickListener { showPage(pageIndex + 1) }

        downloadButton.setOnClickListener { downloadCatalogueForOffline(showToastOnSuccess = true) }
        shareButton.setOnClickListener { shareCatalogueIfAvailable() }

        // Prefer cached PDF (offline), otherwise fetch from Firebase.
        if (cachedCatalogueFile.exists()) {
            openRenderer(cachedCatalogueFile)
            showPage(0)
            setShareEnabled(true)
        } else {
            setShareEnabled(false)
            loadPdfFromFirebaseTempPreview()
        }

        return view
    }

    /**
     * Loads the PDF from Firebase into a temp file for preview.
     * This keeps the existing behavior (fast preview) without forcing a permanent download.
     */
    private fun loadPdfFromFirebaseTempPreview() {
        showHint(getString(R.string.catalogue_loading), true)

        val storageRef = Firebase.storage.getReference("catalogue/current.pdf")
        val localFile = File.createTempFile("catalogue", "pdf", requireContext().cacheDir)

        storageRef.getFile(localFile).addOnSuccessListener {
            openRenderer(localFile)
            showPage(0)
            showHint("", false)
        }.addOnFailureListener {
            showHint("", false)
            Toast.makeText(context, getString(R.string.catalogue_download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /** Download PDF into app internal storage so it can be opened offline and safely shared. */
    private fun downloadCatalogueForOffline(showToastOnSuccess: Boolean) {
        showHint(getString(R.string.catalogue_loading), true)
        setShareEnabled(false)

        val storageRef = Firebase.storage.getReference("catalogue/current.pdf")

        val target = cachedCatalogueFile
        target.parentFile?.mkdirs()

        storageRef.getFile(target).addOnSuccessListener {
            // Re-open renderer from the cached file so we're guaranteed to be using the offline copy.
            openRenderer(target)
            if (pageIndex !in 0 until totalPages) pageIndex = 0
            showPage(pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0)))

            showHint("", false)
            setShareEnabled(true)
            if (showToastOnSuccess) {
                Toast.makeText(context, getString(R.string.catalogue_download_complete), Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            showHint("", false)
            Toast.makeText(context, getString(R.string.catalogue_download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCatalogueIfAvailable() {
        val ctx = context ?: return
        val file = cachedCatalogueFile

        if (!file.exists()) {
            Toast.makeText(ctx, getString(R.string.catalogue_share_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = ShareUtils.fileToContentUri(ctx, file)
        val intent = ShareUtils.createShareFileIntent(
            ctx,
            uri,
            mimeType = "application/pdf",
            subject = getString(R.string.catalogue_title),
            chooserTitle = getString(R.string.catalogue_share)
        )

        // Safety: in case chooser isn't available (rare), don't crash.
        if (intent.resolveActivity(ctx.packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun openRenderer(file: File) {
        // Close old renderer if we are switching files.
        currentPage?.close()
        currentPage = null
        pdfRenderer?.close()
        pdfRenderer = null

        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor)
        totalPages = pdfRenderer?.pageCount ?: 0
    }

    private fun showPage(index: Int) {
        if (pdfRenderer == null || index < 0 || index >= totalPages) return

        currentPage?.close()
        currentPage = pdfRenderer?.openPage(index)
        val page = currentPage ?: return

        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pdfPageImageView.setImageBitmap(bitmap)

        pageIndex = index
        updateUI()
    }

    private fun updateUI() {
        pageIndicator.text = "Page ${pageIndex + 1} of $totalPages"
        prevButton.isEnabled = pageIndex > 0
        nextButton.isEnabled = pageIndex < totalPages - 1

        // Keep share enabled only if we have an offline file we can safely share.
        setShareEnabled(cachedCatalogueFile.exists())
    }

    private fun setShareEnabled(enabled: Boolean) {
        shareButton.isEnabled = enabled
        shareButton.alpha = if (enabled) 1f else 0.55f
    }

    private fun showHint(text: String, show: Boolean) {
        hintText.text = text
        hintText.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
