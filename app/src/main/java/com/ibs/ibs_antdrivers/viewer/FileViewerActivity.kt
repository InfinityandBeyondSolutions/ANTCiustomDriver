package com.ibs.ibs_antdrivers.viewer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ibs.ibs_antdrivers.enableWhiteStatusBarEdgeToEdge

class FileViewerActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableWhiteStatusBarEdgeToEdge()
        val wv = WebView(this)
        setContentView(wv)

        ViewCompat.setOnApplyWindowInsetsListener(wv) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val url = intent.getStringExtra("url") ?: return finish()

        // Use Google Docs Viewer for PDFs/Office files
        val viewer = "https://docs.google.com/gview?embedded=1&url=" +
                Uri.encode(url)

        wv.settings.javaScriptEnabled = true
        wv.settings.setSupportZoom(true)
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.webViewClient = object : WebViewClient() {}
        wv.loadUrl(viewer)
    }
}
