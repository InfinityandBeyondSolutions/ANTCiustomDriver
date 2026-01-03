package com.ibs.ibs_antdrivers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class UploadingDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "UploadingDialogFragment"

        fun newInstance(): UploadingDialogFragment = UploadingDialogFragment()
    }

    private var progressTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_uploading, null, false)
        progressTextView = content.findViewById(R.id.progressText)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(content)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
        return dialog
    }

    fun updateProgress(done: Int, total: Int) {
        progressTextView?.text = "$done / $total"
    }
}

