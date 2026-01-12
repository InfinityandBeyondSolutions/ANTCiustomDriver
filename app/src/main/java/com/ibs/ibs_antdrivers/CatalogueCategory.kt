package com.ibs.ibs_antdrivers

data class CatalogueCategory(
    val Id: Int = 0,
    val Name: String? = null,
    val IsActive: Boolean = false,
    val Catalogue: CatalogueFile? = null
)

data class CatalogueFile(
    val FileName: String? = null,
    val FileSizeBytes: Long? = null,
    val FileUrl: String? = null,
    val UploadedAtUnixMs: Long? = null,
    val UploadedBy: String? = null
)

