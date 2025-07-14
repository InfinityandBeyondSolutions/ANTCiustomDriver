package com.ibs.ibs_antdrivers

data class Announcement(
    val Id: String? = null,
    val Title: String? = null,
    val Body: String? = null,
    val FileUrl: String? = null,         // optional
    val AdminName: String? = null,
    val DatePosted: String? = null,
    val IsDraft: Boolean = false
)

