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


class AnnouncementsFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var containerLayout: LinearLayout
    private lateinit var noText: TextView
    private lateinit var recyclerView: RecyclerView
    private val announcements = mutableListOf<Announcement>()

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

        database.get().addOnSuccessListener { snapshot ->
            announcements.clear()

            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val ann = child.getValue(Announcement::class.java)
                    if (ann != null && !ann.IsDraft) {
                        announcements.add(ann)
                    }
                }

                if (announcements.isNotEmpty()) {
                    noText.visibility = View.GONE
                    recyclerView.adapter = AnnouncementsAdapter(announcements)
                } else {
                    noText.visibility = View.VISIBLE
                }
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to load announcements", Toast.LENGTH_SHORT).show()
        }
    }

}
