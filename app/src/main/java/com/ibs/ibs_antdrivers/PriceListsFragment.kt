package com.ibs.ibs_antdrivers

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ibs.ibs_antdrivers.data.PriceList
import com.ibs.ibs_antdrivers.data.PriceListsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PriceListsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView

    private val repo = PriceListsRepository()
    private lateinit var adapter: PriceListsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_price_lists, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler  = view.findViewById(R.id.priceListsRecycler)
        progress  = view.findViewById(R.id.priceListsProgress)
        emptyText = view.findViewById(R.id.priceListsEmpty)

        adapter = PriceListsAdapter(
            onItemClick = { pl ->
                val bundle = Bundle().apply {
                    putString(PriceListDetailFragment.ARG_PRICE_LIST_ID, pl.id)
                }
                findNavController().navigate(R.id.priceListDetailFragment, bundle)
            },
            onShareClick = { pl -> generateAndSharePdf(pl) },
            onDownloadClick = { pl -> generateAndDownloadPdf(pl) },
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAllPriceLists(requireContext().applicationContext)
                .collect { data ->
                    progress.visibility = View.GONE
                    if (data.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        adapter.submitList(emptyList())
                    } else {
                        emptyText.visibility = View.GONE
                        adapter.submitList(data)
                    }
                }
        }
    }

    /**
     * Unified PDF flow used by BOTH the share and download buttons.
     *
     * 1. Generate the PDF (same file every time).
     * 2. Save / copy it to the public Downloads folder.
     * 3. If [thenShare] == true  → open the system share sheet immediately.
     *    If [thenShare] == false → show a Snackbar confirming the save, with a
     *                             "Share" action that re-uses the same cached file.
     */
    @Suppress("DEPRECATION")
    private fun generateSaveAndMaybeShare(pl: PriceList, thenShare: Boolean) {
        val dialog = ProgressDialog(requireContext()).apply {
            setMessage(if (thenShare) "Preparing PDF…" else "Saving to Downloads…")
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Always fetch a fully-loaded price list with items (Room-first, Firebase fallback)
                val full: PriceList? = withContext(Dispatchers.IO) {
                    repo.getPriceListWithItems(requireContext().applicationContext, pl.id)
                }

                val toExport = full ?: pl
                if (toExport.items.isEmpty()) {
                    dialog.dismiss()
                    Snackbar.make(requireView(), "This price list has no items to export", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }

                // Step 1 – generate (cached in filesDir)
                val pdfFile: File = withContext(Dispatchers.IO) {
                    PriceListPdfGenerator.generate(requireContext(), toExport)
                }

                // Step 2 – save the exact same file to public Downloads
                val savedFileName: String? = withContext(Dispatchers.IO) {
                    saveToDownloads(pdfFile)
                }

                dialog.dismiss()

                if (thenShare) {
                    // Step 3a – share the file that was just saved (same PDF)
                    sharePdf(pdfFile, toExport)
                } else {
                    // Step 3b – confirm the save with a Snackbar + optional Share action
                    val msg = if (savedFileName != null)
                        "Saved to Downloads: $savedFileName"
                    else
                        "Could not save to Downloads — tap Share to send the PDF"

                    Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG)
                        .setAction("Share") { sharePdf(pdfFile, toExport) }
                        .show()
                }
            } catch (t: Throwable) {
                dialog.dismiss()
                Snackbar.make(requireView(), "Failed to generate PDF: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun generateAndSharePdf(pl: PriceList)    = generateSaveAndMaybeShare(pl, thenShare = true)
    private fun generateAndDownloadPdf(pl: PriceList) = generateSaveAndMaybeShare(pl, thenShare = false)

    /** Opens the system share sheet for [pdfFile] via FileProvider. */
    private fun sharePdf(pdfFile: File, pl: PriceList) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            pdfFile
        )
        val shareTitle = pl.title.ifBlank { pl.name }.ifBlank { "Price List" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, "Please find the attached price list: $shareTitle")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share \"$shareTitle\" via…"))
    }

    /**
     * Copies [pdfFile] (from internal filesDir) into the device's public Downloads folder.
     * Uses [MediaStore] on Android 10+ (no storage permission needed) and
     * [Environment.getExternalStoragePublicDirectory] on older versions.
     *
     * @return the file-name as it was saved, or null on failure.
     */
    private fun saveToDownloads(pdfFile: File): String? {
        val fileName = pdfFile.name
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri: Uri = resolver.insert(collection, values) ?: return null

                resolver.openOutputStream(itemUri)?.use { out ->
                    pdfFile.inputStream().use { it.copyTo(out) }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                fileName
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val dest = File(downloadsDir, fileName)
                pdfFile.inputStream().use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                fileName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
