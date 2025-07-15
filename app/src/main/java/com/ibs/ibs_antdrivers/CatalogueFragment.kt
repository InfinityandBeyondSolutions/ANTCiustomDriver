package com.ibs.ibs_antdrivers

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

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageIndex = 0
    private var totalPages = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_catalogue, container, false)

        pdfPageImageView = view.findViewById(R.id.pdfPage)
        prevButton = view.findViewById(R.id.prevPage)
        nextButton = view.findViewById(R.id.nextPage)
        pageIndicator = view.findViewById(R.id.pageIndicator)

        loadPdfFromFirebase()

        prevButton.setOnClickListener { showPage(pageIndex - 1) }
        nextButton.setOnClickListener { showPage(pageIndex + 1) }

        return view
    }

    private fun loadPdfFromFirebase() {
        val storageRef = Firebase.storage.getReference("catalogue/current.pdf")
        val localFile = File.createTempFile("catalogue", "pdf")

        storageRef.getFile(localFile).addOnSuccessListener {
            openRenderer(localFile)
            showPage(0)
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRenderer(file: File) {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentPage?.close()
        pdfRenderer?.close()
    }
}
