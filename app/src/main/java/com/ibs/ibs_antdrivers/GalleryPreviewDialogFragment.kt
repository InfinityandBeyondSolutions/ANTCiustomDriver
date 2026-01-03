package com.ibs.ibs_antdrivers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class GalleryPreviewDialogFragment : DialogFragment() {

    interface Listener {
        fun onDownloadRequested(imageUrl: String, imageName: String)
        fun onShareRequested(imageUrl: String, imageName: String)
    }

    private val imageUrls: ArrayList<String> by lazy {
        requireArguments().getStringArrayList(ARG_URLS) ?: arrayListOf()
    }

    private val imageNames: ArrayList<String> by lazy {
        requireArguments().getStringArrayList(ARG_NAMES) ?: arrayListOf()
    }

    private val startIndex: Int by lazy {
        requireArguments().getInt(ARG_INDEX, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.dialog_gallery_preview, container, false)

        val viewPager = root.findViewById<ViewPager2>(R.id.viewPager)
        val tvName = root.findViewById<TextView>(R.id.tvName)
        val btnPrev = root.findViewById<MaterialButton>(R.id.btnPrev)
        val btnNext = root.findViewById<MaterialButton>(R.id.btnNext)
        val btnShare = root.findViewById<MaterialButton>(R.id.btnShare)
        val btnDownload = root.findViewById<MaterialButton>(R.id.btnDownload)
        val btnClose = root.findViewById<MaterialButton>(R.id.btnClose)

        viewPager.adapter = GalleryPreviewPagerAdapter(imageUrls)
        viewPager.setCurrentItem(startIndex.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0)), false)

        fun updateUiForPosition(position: Int) {
            tvName.text = imageNames.getOrNull(position).orEmpty()

            val canGoPrev = position > 0
            val canGoNext = position < (imageUrls.size - 1)
            btnPrev.visibility = if (canGoPrev) View.VISIBLE else View.INVISIBLE
            btnNext.visibility = if (canGoNext) View.VISIBLE else View.INVISIBLE
        }

        updateUiForPosition(viewPager.currentItem)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUiForPosition(position)
            }
        })

        btnPrev.setOnClickListener {
            val i = viewPager.currentItem
            if (i > 0) viewPager.currentItem = i - 1
        }

        btnNext.setOnClickListener {
            val i = viewPager.currentItem
            if (i < imageUrls.size - 1) viewPager.currentItem = i + 1
        }

        btnShare.setOnClickListener {
            val i = viewPager.currentItem
            val url = imageUrls.getOrNull(i) ?: return@setOnClickListener
            val name = imageNames.getOrNull(i).orEmpty()
            (parentFragment as? Listener)?.onShareRequested(url, name)
                ?: (activity as? Listener)?.onShareRequested(url, name)
        }

        btnDownload.setOnClickListener {
            val i = viewPager.currentItem
            val url = imageUrls.getOrNull(i) ?: return@setOnClickListener
            val name = imageNames.getOrNull(i).orEmpty()
            (parentFragment as? Listener)?.onDownloadRequested(url, name)
                ?: (activity as? Listener)?.onDownloadRequested(url, name)
        }

        btnClose.setOnClickListener { dismissAllowingStateLoss() }

        return root
    }

    companion object {
        private const val ARG_URLS = "arg_urls"
        private const val ARG_NAMES = "arg_names"
        private const val ARG_INDEX = "arg_index"

        fun newInstance(imageUrls: List<String>, imageNames: List<String>, startIndex: Int): GalleryPreviewDialogFragment {
            return GalleryPreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_URLS, ArrayList(imageUrls))
                    putStringArrayList(ARG_NAMES, ArrayList(imageNames))
                    putInt(ARG_INDEX, startIndex)
                }
            }
        }
    }
}
