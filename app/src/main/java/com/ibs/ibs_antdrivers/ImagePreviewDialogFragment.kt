package com.ibs.ibs_antdrivers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class ImagePreviewDialogFragment : DialogFragment() {

    interface Listener {
        fun onDownloadRequested(imageUrl: String, imageName: String)
    }

    private var imageUrl: String = ""
    private var imageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        imageUrl = requireArguments().getString(ARG_URL).orEmpty()
        imageName = requireArguments().getString(ARG_NAME).orEmpty()
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
        val root = inflater.inflate(R.layout.dialog_image_preview, container, false)

        val ivPreview = root.findViewById<ImageView>(R.id.ivPreview)
        val tvName = root.findViewById<TextView>(R.id.tvName)
        val btnDownload = root.findViewById<MaterialButton>(R.id.btnDownload)
        val btnClose = root.findViewById<MaterialButton>(R.id.btnClose)

        tvName.text = imageName

        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .into(ivPreview)

        btnClose.setOnClickListener { dismissAllowingStateLoss() }

        btnDownload.setOnClickListener {
            (parentFragment as? Listener)?.onDownloadRequested(imageUrl, imageName)
                ?: (activity as? Listener)?.onDownloadRequested(imageUrl, imageName)
            dismissAllowingStateLoss()
        }

        return root
    }

    companion object {
        private const val ARG_URL = "arg_url"
        private const val ARG_NAME = "arg_name"

        fun newInstance(imageUrl: String, imageName: String): ImagePreviewDialogFragment {
            return ImagePreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, imageUrl)
                    putString(ARG_NAME, imageName)
                }
            }
        }
    }
}

