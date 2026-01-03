package com.ibs.ibs_antdrivers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    fun createShareIntent(context: Context, uris: List<Uri>, subject: String? = null, chooserTitle: String = "Share") : Intent {
        val intent = if (uris.size <= 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uris.firstOrNull())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }

        // Grant read permission for FileProvider URIs.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Some apps rely on ClipData for persistable read access.
        if (uris.isNotEmpty()) {
            val clip = ClipData.newUri(context.contentResolver, "shared_images", uris[0])
            for (i in 1 until uris.size) {
                clip.addItem(ClipData.Item(uris[i]))
            }
            intent.clipData = clip
        }

        return Intent.createChooser(intent, chooserTitle)
    }

    fun fileToContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
    }
}

