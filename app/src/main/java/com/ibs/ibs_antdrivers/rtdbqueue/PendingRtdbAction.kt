package com.ibs.ibs_antdrivers.rtdbqueue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RtdbActionType {
    SET,
    UPDATE,
    DELETE
}

enum class PendingStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}

/**
 * Durable queue item for RTDB writes when the device is offline.
 */
@Entity(
    tableName = "pending_rtdb_actions",
    indices = [
        Index(value = ["status", "priority", "createdAtMs"]),
        Index(value = ["dedupeKey"], unique = true)
    ]
)
data class PendingRtdbAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    /** Lower value = higher priority. Chat messages should use 0. */
    val priority: Int = 1,
    val status: PendingStatus = PendingStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,

    /** RTDB path like: messages/{groupId}/{messageId} */
    val path: String,
    val type: RtdbActionType,

    /** Optional idempotency key to collapse repeated writes (e.g., settings toggles). */
    val dedupeKey: String? = null,

    /**
     * For SET: store a string value.
     * For UPDATE: store a JSON object string.
     * For DELETE: null.
     */
    val payloadJson: String? = null
)

