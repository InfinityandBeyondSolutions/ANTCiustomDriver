package com.ibs.ibs_antdrivers.rtdbqueue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingRtdbActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: PendingRtdbAction): Long

    @Update
    suspend fun update(action: PendingRtdbAction)

    @Query(
        """
        SELECT * FROM pending_rtdb_actions
        WHERE status IN (:pending, :failed)
        ORDER BY priority ASC, createdAtMs ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun nextBatch(
        limit: Int,
        pending: PendingStatus = PendingStatus.PENDING,
        failed: PendingStatus = PendingStatus.FAILED
    ): List<PendingRtdbAction>

    @Query("UPDATE pending_rtdb_actions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PendingStatus)

    @Query(
        "UPDATE pending_rtdb_actions SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE id = :id"
    )
    suspend fun updateResult(id: Long, status: PendingStatus, retryCount: Int, lastError: String?)

    @Query("DELETE FROM pending_rtdb_actions WHERE status = :done")
    suspend fun deleteDone(done: PendingStatus = PendingStatus.DONE): Int

    @Query("SELECT COUNT(1) FROM pending_rtdb_actions WHERE status IN (:pending, :failed)")
    suspend fun countPending(
        pending: PendingStatus = PendingStatus.PENDING,
        failed: PendingStatus = PendingStatus.FAILED
    ): Int
}

