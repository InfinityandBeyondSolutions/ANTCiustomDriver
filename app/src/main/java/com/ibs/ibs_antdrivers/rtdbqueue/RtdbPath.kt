package com.ibs.ibs_antdrivers.rtdbqueue

/**
 * Tiny helper to build RTDB paths safely.
 *
 * Note: Firebase RTDB keys cannot contain '.', '#', '$', '[', ']' characters.
 */
object RtdbPath {
    fun child(vararg segments: String): String {
        return segments
            .filter { it.isNotBlank() }
            .joinToString("/") { it.trim('/') }
    }
}

