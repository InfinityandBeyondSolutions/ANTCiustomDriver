package com.ibs.ibs_antdrivers.offlineupload

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UploadImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<UploadImageEntity>): List<Long>

    @Query("SELECT * FROM upload_images WHERE status IN ('PENDING','FAILED') ORDER BY createdAtMs ASC LIMIT :limit")
    suspend fun getNextPending(limit: Int): List<UploadImageEntity>

    @Query("UPDATE upload_images SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE id = :id")
    suspend fun updateStatus(id: Long, status: UploadStatus, retryCount: Int, lastError: String?)

    @Query("DELETE FROM upload_images WHERE status = 'UPLOADED' AND createdAtMs < :beforeMs")
    suspend fun deleteUploadedBefore(beforeMs: Long): Int
}

