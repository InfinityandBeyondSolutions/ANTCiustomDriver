package com.ibs.ibs_antdrivers

data class Attachment(
    val Id: String? = null,
    val FileName: String? = null,
    val FileUrl: String? = null,
    val ContentType: String? = null,
    val FileSizeBytes: Long? = null,
    val StoragePath: String? = null,
    val UploadedAt: String? = null
)

data class Announcement(
    val Id: String? = null,
    val Title: String? = null,
    val Body: String? = null,
    val FileUrl: String? = null,         // deprecated, kept for backward compatibility
    val Attachments: List<Attachment>? = null,
    val AdminName: String? = null,
    val DatePosted: String? = null,
    val IsDraft: Boolean = false
)

