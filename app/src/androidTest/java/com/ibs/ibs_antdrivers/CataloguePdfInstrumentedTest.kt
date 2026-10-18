package com.ibs.ibs_antdrivers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.storage.FirebaseStorage
import com.ibs.ibs_antdrivers.data.CataloguePdfStore
import com.ibs.ibs_antdrivers.data.catalogueCacheName
import com.ibs.ibs_antdrivers.data.catalogueStorageLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CataloguePdfInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun signedGoogleCloudUrlResolvesWithTheInstalledFirebaseSdk() {
        val storage = FirebaseStorage.getInstance()
        val bucket = storage.reference.bucket
        val location = catalogueStorageLocation(
            "https://storage.googleapis.com/$bucket/catalogue/categories/2/current.pdf?Signature=test"
        )!!
        // This is the call that used to throw before the viewer could even appear.
        val reference = storage.getReferenceFromUrl(location)
        assertEquals(bucket, reference.bucket)
        assertEquals("/catalogue/categories/2/current.pdf", reference.path)
    }

    @Test fun pagesRenderRepeatedlyAndReleaseNativeResources() {
        val file = File.createTempFile("catalogue_render_test", ".pdf", context.cacheDir)
        try {
            writePdf(file)
            repeat(6) { index ->
                val page = renderCataloguePage(file, index % 2, 1080)
                assertEquals(2, page.pageCount)
                assertEquals(index % 2, page.index)
                assertTrue(page.bitmap.width <= 2048 && page.bitmap.height <= 2048)
                assertFalse(page.bitmap.isRecycled)
                page.bitmap.recycle()
            }
            val restoredPage = renderCataloguePage(file, 999, 1080)
            assertEquals(1, restoredPage.index)
            restoredPage.bitmap.recycle()
        } finally {
            file.delete()
        }
    }

    @Test fun completedPdfCanBeLoadedOfflineWithoutUsingItsUrl() = runBlocking {
        val id = -9876
        val file = File(context.filesDir, "catalogue/${catalogueCacheName(id, "invalid://offline-test", 1)}")
        file.parentFile!!.mkdirs()
        try {
            writePdf(file)
            val cached = CataloguePdfStore(context).load(id, "invalid://offline-test", 1)
            assertEquals(file, cached)
            val page = renderCataloguePage(cached, 0, 1080)
            assertEquals(2, page.pageCount)
            page.bitmap.recycle()
        } finally {
            file.delete()
        }
    }

    @Test fun corruptPdfFailsWithoutKeepingTheFileDescriptorOpen() {
        val file = File.createTempFile("catalogue_invalid_test", ".pdf", context.cacheDir)
        try {
            file.writeText("%PDF-1.7\ntruncated download")
            repeat(3) {
                try {
                    renderCataloguePage(file, 0, 1080)
                    fail("Expected the invalid PDF to be rejected")
                } catch (_: java.io.IOException) {
                    // The fragment handles this and leaves export and external opening available.
                }
            }
            writePdf(file)
            val page = renderCataloguePage(file, 0, 1080)
            assertEquals(2, page.pageCount)
            page.bitmap.recycle()
        } finally {
            file.delete()
        }
    }

    @Test fun sharedPdfHasTheOriginalNameAndCanBeReadThroughTheFileProvider() {
        val file = File.createTempFile("catalogue_share_test", ".pdf", context.cacheDir)
        try {
            writePdf(file)
            val uri = ShareUtils.fileToContentUri(context, file, "Bestnut-Catalogue.pdf")
            assertEquals("content", uri.scheme)
            assertEquals("application/pdf", context.contentResolver.getType(uri))
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("Bestnut-Catalogue.pdf", it.getString(0))
            }
            context.contentResolver.openInputStream(uri)!!.use {
                assertEquals('%'.code, it.read())
            }
        } finally {
            file.delete()
        }
    }

    private fun writePdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(600, 900, index).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Catalogue page ${index + 1}", 40f, 80f, Paint().apply { textSize = 24f })
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }
}
