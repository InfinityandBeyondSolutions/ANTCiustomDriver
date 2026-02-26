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
            try {
                val data = withContext(Dispatchers.IO) { repo.getAllPriceLists() }
                progress.visibility = View.GONE

                if (data.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                } else {
                    emptyText.visibility = View.GONE
                    adapter.submitList(data)
                }
            } catch (t: Throwable) {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = getString(R.string.price_lists_failed)
                Snackbar.make(requireView(), t.message ?: "Failed to load price lists", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun generateAndSharePdf(pl: PriceList) {
        if (pl.items.isEmpty()) {
            Snackbar.make(requireView(), "This price list has no items to export", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialog = ProgressDialog(requireContext()).apply {
            setMessage("Generating PDF…")
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    PriceListPdfGenerator.generate(requireContext(), pl)
                }
                dialog.dismiss()

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
            } catch (t: Throwable) {
                dialog.dismiss()
                Snackbar.make(requireView(), "Failed to generate PDF: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun generateAndDownloadPdf(pl: PriceList) {
        if (pl.items.isEmpty()) {
            Snackbar.make(requireView(), "This price list has no items to export", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialog = ProgressDialog(requireContext()).apply {
            setMessage("Saving to Downloads…")
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    PriceListPdfGenerator.generate(requireContext(), pl)
                }
                val savedFileName = withContext(Dispatchers.IO) {
                    saveToDownloads(pdfFile)
                }
                dialog.dismiss()

                val msg = if (savedFileName != null)
                    "Saved to Downloads: $savedFileName"
                else
                    "Could not save to Downloads — tap Share to send the PDF"

                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG)
                    .setAction("Share") { generateAndSharePdf(pl) }
                    .show()
            } catch (t: Throwable) {
                dialog.dismiss()
                Snackbar.make(requireView(), "Failed to generate PDF: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** Copy a PDF file into public Downloads and return the saved filename, or null on failure. */
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
