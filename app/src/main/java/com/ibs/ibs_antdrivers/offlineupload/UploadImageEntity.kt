package com.ibs.ibs_antdrivers.offlineupload

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_images",
    indices = [
        Index(value = ["status"]),
        Index(value = ["storeId"])
    ]
)
data class UploadImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeId: String,
    val storeName: String,
    val driverUid: String,
    val driverName: String,
    /** Absolute path to an app-private file (durable). */
    val localFilePath: String,
    /** Original uri string (optional; debug/reference). */
    val sourceUri: String,
    /** Firebase Storage relative path e.g. store_images/{storeId}/file.jpg */
    val remotePath: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val status: UploadStatus = UploadStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

