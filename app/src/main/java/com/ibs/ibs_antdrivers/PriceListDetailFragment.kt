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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ibs.ibs_antdrivers.data.PriceList
import com.ibs.ibs_antdrivers.data.PriceListsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PriceListDetailFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var detailTitle: TextView
    private lateinit var detailSubtitle: TextView

    private val repo = PriceListsRepository()
    private lateinit var adapter: PriceListItemsAdapter

    private var priceListId: String? = null
    private var loaded: PriceList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priceListId = arguments?.getString(ARG_PRICE_LIST_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_pricelist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler  = view.findViewById(R.id.priceListItemsRecycler)
        progress  = view.findViewById(R.id.priceListItemsProgress)
        emptyText = view.findViewById(R.id.priceListItemsEmpty)

        detailTitle    = view.findViewById(R.id.detailTitle)
        detailSubtitle = view.findViewById(R.id.detailSubtitle)

        adapter = PriceListItemsAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        setupMenu()
        load()
    }

    private fun setupMenu() {
        val host: MenuHost = requireActivity()
        host.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_pricelist_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_download_pdf -> {
                        generateAndHandlePdf(share = false)
                        true
                    }
                    R.id.action_share_pricelist -> {
                        generateAndHandlePdf(share = true)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun load() {
        val id = priceListId
        if (id.isNullOrBlank()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Missing price list id"
            return
        }

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.getAllPriceLists() }
                val pl   = list.firstOrNull { it.id == id }

                progress.visibility = View.GONE

                if (pl == null) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Price list not found"
                    return@launch
                }

                loaded = pl

                val title = pl.title.ifBlank { pl.name }.ifBlank { "Price List" }
                requireActivity().title = title
                detailTitle.text = title

                val subtitleParts = buildList {
                    if (pl.companyName.isNotBlank())    add(pl.companyName)
                    if (pl.effectiveDate.isNotBlank())  add("Effective: ${pl.effectiveDate}")
                    if (pl.includeVat)                  add("VAT included")
                }
                detailSubtitle.text = subtitleParts.joinToString(" • ").ifBlank { "Tap the share icon to send as PDF" }

                if (pl.items.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No items"
                    adapter.submitList(emptyList())
                } else {
                    emptyText.visibility = View.GONE
                    adapter.submitList(pl.items)
                }
            } catch (t: Throwable) {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = getString(R.string.price_lists_failed)
                Snackbar.make(requireView(), t.message ?: "Failed to load price list", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // ── PDF generation ──────────────────────────────────────────────────────

    /**
     * Generates the PDF on the IO thread, then either:
     *  - [share] = true  → opens the system share sheet (WhatsApp, Email, Drive…)
     *  - [share] = false → copies PDF to the public Downloads folder so the user
     *                      can find it in the Files app, then shows a confirmation.
     */
    @Suppress("DEPRECATION")
    private fun generateAndHandlePdf(share: Boolean) {
        val pl = loaded
        if (pl == null) {
            Snackbar.make(requireView(), "Price list is still loading, please wait", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialog = ProgressDialog(requireContext()).apply {
            setMessage(if (share) "Generating PDF…" else "Saving to Downloads…")
            isIndeterminate = true
            setCancelable(false)
            show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pdfFile: File = withContext(Dispatchers.IO) {
                    PriceListPdfGenerator.generate(requireContext(), pl)
                }

                if (share) {
                    dialog.dismiss()
                    sharePdf(pdfFile, pl)
                } else {
                    // Copy to public Downloads folder
                    val savedFileName = withContext(Dispatchers.IO) {
                        saveToDownloads(pdfFile, pl)
                    }
                    dialog.dismiss()

                    val msg = if (savedFileName != null)
                        "Saved to Downloads: $savedFileName"
                    else
                        "Could not save to Downloads — tap Share to send the PDF"

                    Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG)
                        .setAction("Share") { sharePdf(pdfFile, pl) }
                        .show()
                }
            } catch (t: Throwable) {
                dialog.dismiss()
                Snackbar.make(requireView(), "Failed to generate PDF: ${t.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Copies [pdfFile] (from internal filesDir) into the device's public Downloads folder.
     * Uses [MediaStore] on Android 10+ (no storage permission needed) and
     * [Environment.getExternalStoragePublicDirectory] on older versions.
     *
     * @return the file-name as it was saved, or null on failure.
     */
    private fun saveToDownloads(pdfFile: File, pl: PriceList): String? {
        val fileName = pdfFile.name
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: use MediaStore — no WRITE_EXTERNAL_STORAGE needed
                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri: Uri = resolver.insert(collection, values)
                    ?: return null

                resolver.openOutputStream(itemUri)?.use { out ->
                    pdfFile.inputStream().use { it.copyTo(out) }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                fileName
            } else {
                // Android 9 and below: write directly to the public Downloads directory
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

    companion object {
        const val ARG_PRICE_LIST_ID = "priceListId"
    }
}
