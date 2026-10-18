package com.ibs.ibs_antdrivers.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CataloguePdfStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `signed Google Cloud download links become Firebase object references`() {
        assertEquals(
            "gs://example.firebasestorage.app/catalogue/categories/2/current.pdf",
            catalogueStorageLocation(
                "https://storage.googleapis.com/example.firebasestorage.app/catalogue/categories/2/current.pdf?GoogleAccessId=test&Signature=secret"
            ),
        )
    }

    @Test fun `encoded object names are preserved without double decoding`() {
        assertEquals(
            "gs://bucket/catalogue/My%20PDF%2B100%25.pdf",
            catalogueStorageLocation("https://storage.googleapis.com/bucket/catalogue/My%20PDF%2B100%25.pdf?token=test"),
        )
    }

    @Test fun `existing Firebase URLs and relative Storage paths still work`() {
        val firebaseUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/catalogue%2Fcurrent.pdf?alt=media&token=test"
        assertEquals(firebaseUrl, catalogueStorageLocation(firebaseUrl))
        assertEquals("gs://bucket/current.pdf", catalogueStorageLocation("gs://bucket/current.pdf"))
        assertEquals("catalogue/current.pdf", catalogueStorageLocation("catalogue/current.pdf"))
    }

    @Test fun `unrelated and invalid URLs are not passed into Firebase URL parsing`() {
        listOf(
            "", "https://example.com/current.pdf", "https://storage.googleapis.com/bucket",
            "https://storage.googleapis.com.evil.example/bucket/current.pdf",
            "file:///current.pdf", "content://provider/current.pdf", "https://bad url/current.pdf",
        ).forEach { assertNull(it, catalogueStorageLocation(it)) }
    }

    @Test fun `cache follows document version and category instead of browser token`() {
        assertEquals(catalogueCacheName(2, "url?old", 100), catalogueCacheName(2, "url?new", 100))
        assertNotEquals(catalogueCacheName(2, "url", 100), catalogueCacheName(2, "url", 101))
        assertNotEquals(catalogueCacheName(2, "url", 100), catalogueCacheName(3, "url", 100))
        assertNotEquals(catalogueCacheName(2, "first", null), catalogueCacheName(2, "second", null))
    }

    @Test fun `empty downloads and error responses cannot become cached PDFs`() {
        val file = temporaryFolder.newFile()
        assertFalse(file.hasPdfHeader())
        file.writeText("<html>Access denied</html>")
        assertFalse(file.hasPdfHeader())
        file.writeText("%PDF-1.7\nPDF content")
        assertTrue(file.hasPdfHeader())
    }
}
