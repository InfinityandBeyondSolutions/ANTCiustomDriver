package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/** A completed PDF is cached once and reused for preview, offline reading, export and sharing. */
class CataloguePdfStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "catalogue")
    private val mutex = Mutex()

    suspend fun load(
        categoryId: Int,
        fileUrl: String?,
        uploadedAt: Long?,
        refresh: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Could not create the catalogue directory")
            }
            val target = File(directory, catalogueCacheName(categoryId, fileUrl, uploadedAt))
            if (!refresh && target.isFile && target.hasPdfHeader()) return@withLock target

            val url = fileUrl?.trim().orEmpty()
            val locations = listOfNotNull(
                catalogueStorageLocation(url),
                "catalogue/categories/$categoryId/current.pdf",
            ).distinct()
            val attempts = mutableListOf<suspend (File) -> Unit>()
            locations.forEach { location ->
                attempts.add { file ->
                    val storage = FirebaseStorage.getInstance()
                    val reference = if (location.contains("://")) {
                        storage.getReferenceFromUrl(location)
                    } else {
                        storage.getReference(location)
                    }
                    val task = reference.getFile(file)
                    try {
                        task.await()
                    } finally {
                        if (!task.isComplete) task.cancel()
                    }
                }
            }
            // Keep signed HTTPS links intact. They also support PDFs hosted outside Firebase.
            if (url.startsWith("https://", ignoreCase = true)) {
                attempts.add { file -> downloadHttps(url, file) }
            }

            for (download in attempts) {
                currentCoroutineContext().ensureActive()
                val partial = File.createTempFile("catalogue_${categoryId}_", ".part", directory)
                try {
                    download(partial)
                    if (!partial.hasPdfHeader()) throw IOException("The response is not a PDF")
                    currentCoroutineContext().ensureActive()
                    // Never expose a partial download as an offline PDF.
                    if (!partial.renameTo(target)) throw IOException("Could not save the PDF")
                    return@withLock target
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Try the canonical authenticated Storage path, then the original HTTPS URL.
                    // Do not log signed URLs or their access tokens.
                } finally {
                    partial.delete()
                }
            }
            throw IOException("The catalogue PDF could not be downloaded")
        }
    }

    private suspend fun downloadHttps(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/pdf")
            if (connection.responseCode !in 200..299) throw IOException("PDF download failed")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** Firebase's Android SDK does not accept storage.googleapis.com download URLs directly. */
internal fun catalogueStorageLocation(url: String): String? {
    if (url.isBlank()) return null
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return null
    }
    return when {
        uri.scheme.equals("gs", ignoreCase = true) -> url
        uri.scheme == null && !url.startsWith("/") -> url
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("firebasestorage.googleapis.com", ignoreCase = true) -> url
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("storage.googleapis.com", ignoreCase = true) -> {
            val path = uri.rawPath.orEmpty().removePrefix("/")
            val bucket = path.substringBefore('/')
            val objectPath = path.substringAfter('/', "")
            if (bucket.isBlank() || objectPath.isBlank()) null else "gs://$bucket/$objectPath"
        }
        else -> null
    }
}

internal fun catalogueCacheName(categoryId: Int, url: String?, uploadedAt: Long?): String {
    val version = uploadedAt?.toString() ?: url?.trim().orEmpty()
    val digest = MessageDigest.getInstance("SHA-256").digest(version.toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
    return "catalogue_${categoryId}_$digest.pdf"
}

internal fun File.hasPdfHeader(): Boolean {
    if (!isFile || length() < 5) return false
    return inputStream().use { input ->
        val prefix = ByteArray(1024)
        val count = input.read(prefix)
        count >= 5 && String(prefix, 0, count, Charsets.ISO_8859_1).contains("%PDF-")
    }
}
