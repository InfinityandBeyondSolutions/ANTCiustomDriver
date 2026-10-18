package com.ibs.ibs_antdrivers

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.ibs.ibs_antdrivers.data.CataloguePdfStore
import com.ibs.ibs_antdrivers.ui.ZoomableImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

class CatalogueFragment : Fragment(R.layout.fragment_catalogue) {
    private var views: CatalogueViews? = null
    private lateinit var pdfStore: CataloguePdfStore
    private var localPdf: File? = null
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var saving = false
    private var pageIndex = 0
    private var totalPages = 0
    private var fullScreen = false
    private var fullScreenApplied = false
    private var fullScreenBack: OnBackPressedCallback? = null
    private var previousNavigationVisibility = View.VISIBLE
    private var previousSystemBarsBehavior = 0

    private val categoryId get() = arguments?.getInt("categoryId", 1) ?: 1
    private val categoryName get() = arguments?.getString("categoryName") ?: "Catalogue"
    private val fileUrl get() = arguments?.getString("fileUrl")
    private val uploadedAt get() = arguments?.getLong("uploadedAtUnixMs")?.takeIf { it > 0 }
    private val pdfFileName: String
        get() {
            val name = arguments?.getString("fileName")?.takeIf { it.isNotBlank() } ?: categoryName
            return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .replace(Regex("(?i)\\.pdf$"), "").take(120) + ".pdf"
        }

    private val saveDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && views != null) savePdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfStore = CataloguePdfStore(requireContext())
        pageIndex = savedInstanceState?.getInt("pageIndex") ?: 0
        fullScreen = savedInstanceState?.getBoolean("fullScreen") ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = CatalogueViews(view)
        views = ui
        saving = false
        ui.title.text = categoryName
        ui.back.setOnClickListener {
            if (fullScreen) setFullScreen(false) else findNavController().navigateUp()
        }
        ui.fullScreen.setOnClickListener { setFullScreen(!fullScreen) }
        ui.zoomIn.setOnClickListener { ui.image.zoomIn() }
        ui.zoomOut.setOnClickListener { ui.image.zoomOut() }
        ui.zoomReset.setOnClickListener { ui.image.resetZoom() }
        ui.image.onZoomChanged = { updateZoomControls(it) }
        fullScreenBack = object : OnBackPressedCallback(fullScreen) {
            override fun handleOnBackPressed() = setFullScreen(false)
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            applyReaderPadding(insets)
            insets
        }
        ui.previous.setOnClickListener { showPage(pageIndex - 1) }
        ui.next.setOnClickListener { showPage(pageIndex + 1) }
        ui.download.setOnClickListener { chooseDownloadLocation() }
        ui.share.setOnClickListener { sharePdf() }
        ui.openExternal.setOnClickListener { openPdfExternally() }
        ui.retry.setOnClickListener { loadPdf(refresh = true) }
        setFullScreen(fullScreen)
        loadPdf()
    }

    override fun onResume() {
        super.onResume()
        // Register after the activity restores its navigation callback during rotation.
        fullScreenBack?.let { requireActivity().onBackPressedDispatcher.addCallback(it) }
    }

    override fun onPause() {
        fullScreenBack?.remove()
        super.onPause()
    }

    private fun setFullScreen(enabled: Boolean) {
        val ui = views ?: return
        fullScreen = enabled
        fullScreenBack?.isEnabled = enabled
        ui.fullScreen.setText(if (enabled) R.string.catalogue_exit_full_screen else R.string.catalogue_full_screen)
        ui.fullScreen.setIconResource(if (enabled) R.drawable.ic_fullscreen_exit_24 else R.drawable.ic_fullscreen_24)
        ui.back.contentDescription = getString(if (enabled) R.string.catalogue_exit_full_screen else R.string.catalogue_back)
        ui.fileActions.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            val window = requireActivity().window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val navigation = requireActivity().findViewById<View>(R.id.bottomNavContainer)
            if (!fullScreenApplied) {
                previousNavigationVisibility = navigation.visibility
                previousSystemBarsBehavior = controller.systemBarsBehavior
                fullScreenApplied = true
            }
            navigation.visibility = View.GONE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            restoreReaderChrome()
        }
        applyReaderPadding(ViewCompat.getRootWindowInsets(ui.root))
        ViewCompat.requestApplyInsets(ui.root)
    }

    private fun applyReaderPadding(insets: WindowInsetsCompat?) {
        val root = views?.root ?: return
        val cutout = insets?.getInsets(WindowInsetsCompat.Type.displayCutout())
        root.setPadding(
            if (fullScreen) cutout?.left ?: 0 else 0,
            if (fullScreen) cutout?.top ?: 0 else 0,
            if (fullScreen) cutout?.right ?: 0 else 0,
            if (fullScreen) cutout?.bottom ?: 0
            else resources.getDimensionPixelSize(R.dimen.bottom_overlay_action_clearance),
        )
    }

    private fun restoreReaderChrome() {
        if (!fullScreenApplied) return
        activity?.let { host ->
            host.findViewById<View>(R.id.bottomNavContainer).visibility = previousNavigationVisibility
            WindowCompat.getInsetsController(host.window, host.window.decorView).apply {
                systemBarsBehavior = previousSystemBarsBehavior
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        fullScreenApplied = false
    }

    private fun updateZoomControls(zoom: Float) {
        val ui = views ?: return
        val ready = ui.image.drawable != null
        ui.zoomIn.isEnabled = ready && zoom < 4.99f
        ui.zoomOut.isEnabled = ready && zoom > 1.01f
        ui.zoomReset.isEnabled = ready
        ui.zoomReset.text = getString(R.string.catalogue_zoom_percentage, (zoom * 100).roundToInt())
    }

    private fun loadPdf(refresh: Boolean = false) {
        loadJob?.cancel()
        renderJob?.cancel()
        localPdf = null
        totalPages = 0
        val ui = views ?: return
        ui.image.setImageDrawable(null)
        updateZoomControls(1f)
        ui.navigation.visibility = View.INVISIBLE
        showHint(R.string.catalogue_loading)
        setFileActionsEnabled(false)
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                localPdf = pdfStore.load(categoryId, fileUrl, uploadedAt, refresh)
                setFileActionsEnabled(true)
                showPage(pageIndex)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Catalogue $categoryId could not be loaded (${error.javaClass.simpleName})")
                showHint(R.string.catalogue_load_error, retry = true)
            }
        }
    }

    private fun showPage(index: Int) {
        val file = localPdf ?: return
        val ui = views ?: return
        if (renderJob?.isActive == true) return
        showHint(R.string.catalogue_loading)
        ui.previous.isEnabled = false
        ui.next.isEnabled = false
        // Each render owns and closes its page, renderer and descriptor on the worker thread.
        // No native PDF object survives view destruction or races with onDestroyView().
        val displayWidth = (resources.displayMetrics.widthPixels * 2).coerceAtMost(2048)
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) {
                    renderCataloguePage(file, index, displayWidth)
                }
                ui.image.setImageBitmap(rendered.bitmap)
                pageIndex = rendered.index
                totalPages = rendered.pageCount
                ui.hint.visibility = View.GONE
                ui.navigation.visibility = View.VISIBLE
                ui.indicator.visibility = View.VISIBLE
                ui.indicator.text = getString(R.string.catalogue_page_number, pageIndex + 1, totalPages)
                ui.previous.isEnabled = pageIndex > 0
                ui.next.isEnabled = pageIndex < totalPages - 1
                ui.previous.alpha = if (ui.previous.isEnabled) 1f else 0.35f
                ui.next.alpha = if (ui.next.isEnabled) 1f else 0.35f
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Catalogue $categoryId could not be rendered (${error.javaClass.simpleName})")
                ui.navigation.visibility = View.INVISIBLE
                showHint(R.string.catalogue_preview_error, retry = true)
            } catch (_: OutOfMemoryError) {
                ui.navigation.visibility = View.INVISIBLE
                showHint(R.string.catalogue_preview_error, retry = true)
            }
        }
    }

    private fun chooseDownloadLocation() {
        if (localPdf == null || saving) return
        try {
            saveDocument.launch(pdfFileName)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.catalogue_no_file_picker)
        }
    }

    private fun savePdf(uri: Uri) {
        val ui = views ?: return
        val resolver = requireContext().applicationContext.contentResolver
        saving = true
        views?.download?.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Also works if Android recreated this fragment while the picker was open.
                    val file = localPdf ?: pdfStore.load(categoryId, fileUrl, uploadedAt)
                    val output = resolver.openOutputStream(uri, "w")
                        ?: throw IOException("Could not open the selected destination")
                    output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
                }
                toast(R.string.catalogue_download_saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.catalogue_download_failed)
            } finally {
                if (views === ui) {
                    saving = false
                    ui.download.isEnabled = localPdf != null
                }
            }
        }
    }

    private fun sharePdf() = launchPdfAction {
        val uri = ShareUtils.fileToContentUri(requireContext(), it, pdfFileName)
        ShareUtils.createShareFileIntent(
            requireContext(), uri, "application/pdf", categoryName, getString(R.string.catalogue_share)
        )
    }

    private fun openPdfExternally() = launchPdfAction {
        val uri = ShareUtils.fileToContentUri(requireContext(), it, pdfFileName)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(categoryName, uri)
        }
    }

    private fun launchPdfAction(createIntent: (File) -> Intent) {
        val file = localPdf ?: return
        try {
            startActivity(createIntent(file))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.catalogue_no_pdf_app)
        } catch (_: Exception) {
            toast(R.string.catalogue_open_failed)
        }
    }

    private fun setFileActionsEnabled(enabled: Boolean) {
        val ui = views ?: return
        listOf(ui.download, ui.share, ui.openExternal).forEach {
            it.isEnabled = enabled && (it !== ui.download || !saving)
            it.alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun showHint(message: Int, retry: Boolean = false) {
        val ui = views ?: return
        ui.hint.text = getString(message)
        ui.hint.visibility = View.VISIBLE
        ui.retry.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun toast(message: Int) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pageIndex", pageIndex)
        outState.putBoolean("fullScreen", fullScreen)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        renderJob?.cancel()
        loadJob = null
        renderJob = null
        restoreReaderChrome()
        fullScreenBack?.remove()
        fullScreenBack = null
        views?.image?.onZoomChanged = null
        views?.image?.setImageDrawable(null)
        views = null
        localPdf = null
        super.onDestroyView()
    }

    private class CatalogueViews(val root: View) {
        val title: TextView = root.findViewById(R.id.catalogueTitle)
        val image: ZoomableImageView = root.findViewById(R.id.pdfPage)
        val back: View = root.findViewById(R.id.backCatalogue)
        val fullScreen: MaterialButton = root.findViewById(R.id.fullScreenCatalogue)
        val zoomIn: View = root.findViewById(R.id.zoomInCatalogue)
        val zoomOut: View = root.findViewById(R.id.zoomOutCatalogue)
        val zoomReset: MaterialButton = root.findViewById(R.id.resetCatalogueZoom)
        val fileActions: View = root.findViewById(R.id.catalogueFileActions)
        val previous: ImageButton = root.findViewById(R.id.prevPage)
        val next: ImageButton = root.findViewById(R.id.nextPage)
        val indicator: TextView = root.findViewById(R.id.pageIndicator)
        val navigation: View = root.findViewById(R.id.navRow)
        val download: View = root.findViewById(R.id.downloadCatalogue)
        val share: View = root.findViewById(R.id.shareCatalogue)
        val openExternal: View = root.findViewById(R.id.openCatalogueExternally)
        val hint: TextView = root.findViewById(R.id.catalogueHint)
        val retry: View = root.findViewById(R.id.retryCatalogue)
    }

    companion object {
        private const val TAG = "CataloguePdf"
    }
}

internal data class RenderedCataloguePage(val bitmap: Bitmap, val index: Int, val pageCount: Int)

internal fun renderCataloguePage(file: File, index: Int, displayWidth: Int): RenderedCataloguePage {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount == 0) throw IOException("The PDF has no pages")
            val pageIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(pageIndex).use { page ->
                val (width, height) = catalogueBitmapSize(page.width, page.height, displayWidth)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return RenderedCataloguePage(bitmap, pageIndex, renderer.pageCount)
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    }
}

/** Bound memory even when a PDF describes a very large poster or planogram. */
internal fun catalogueBitmapSize(pageWidth: Int, pageHeight: Int, displayWidth: Int): Pair<Int, Int> {
    require(pageWidth > 0 && pageHeight > 0)
    val scale = min(
        displayWidth.coerceIn(1, 2048).toDouble() / pageWidth,
        2048.0 / pageHeight,
    )
    return (pageWidth * scale).roundToInt().coerceIn(1, 2048) to
        (pageHeight * scale).roundToInt().coerceIn(1, 2048)
}
