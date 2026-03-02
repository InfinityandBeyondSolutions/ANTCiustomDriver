package com.ibs.ibs_antdrivers

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.time.Instant

class AnnouncementsFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var containerLayout: LinearLayout
    private lateinit var noText: TextView
    private lateinit var recyclerView: RecyclerView
    private val announcements = mutableListOf<Announcement>()

    // Parses DatePosted/UploadedAt to epoch millis so we can sort newest -> oldest.
    // Examples we expect from Firebase:
    // - 2026-03-02T17:42:44.7544295Z   (7 fractional digits)
    // - 2026-03-02T17:42:44.754Z
    // - 2026-03-02T17:42:44Z
    private fun parseIsoToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null

        // Prefer Instant for best ISO-8601 coverage (API 26+). This project targets modern Android,
        // but keep a fallback just in case.
        try {
            return Instant.parse(value).toEpochMilli()
        } catch (_: Throwable) {
            // ignore; fall back to SimpleDateFormat(s)
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX", // 7 fractional digits
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",  // 6
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSX",   // 5
            "yyyy-MM-dd'T'HH:mm:ss.SSSSX",    // 4
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",     // 3
            "yyyy-MM-dd'T'HH:mm:ssX"          // none
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(value)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next
            }
        }

        return null
    }

    private fun sortKeyMillis(ann: Announcement): Long {
        // Primary: DatePosted
        parseIsoToEpochMillis(ann.DatePosted)?.let { return it }
        // Secondary: latest attachment upload time (if present)
        val latestAttachment = ann.Attachments
            ?.mapNotNull { parseIsoToEpochMillis(it.UploadedAt) }
            ?.maxOrNull()
        if (latestAttachment != null) return latestAttachment

        // Unknown dates go to bottom.
        return Long.MIN_VALUE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_announcements, container, false)

        containerLayout = view.findViewById(R.id.announcementContainer)
        noText = view.findViewById(R.id.noAnnouncementsText)

        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
        containerLayout.addView(recyclerView)

        fetchAnnouncements()

        return view
    }

    private fun fetchAnnouncements() {
        database = FirebaseDatabase.getInstance().getReference("announcements")

        // Fetch without an ordered query so we don't depend on a Realtime Database index/rule.
        // We sort in-memory to guarantee newest-first in the UI.
        database.get().addOnSuccessListener { snapshot ->
            announcements.clear()

            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val ann = child.getValue(Announcement::class.java)
                    if (ann != null && !ann.IsDraft) {
                        announcements.add(ann)
                    }
                }

                // Newest first (top of the list)
                announcements.sortWith(
                    compareByDescending<Announcement> { sortKeyMillis(it) }
                        .thenByDescending { it.Id ?: "" }
                )

                if (announcements.isNotEmpty()) {
                    noText.visibility = View.GONE
                    recyclerView.adapter = AnnouncementsAdapter(announcements)
                } else {
                    noText.visibility = View.VISIBLE
                }
            } else {
                noText.visibility = View.VISIBLE
            }
        }.addOnFailureListener { e ->
            Toast.makeText(
                requireContext(),
                "Failed to load announcements: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            noText.visibility = View.VISIBLE
        }
    }

}
