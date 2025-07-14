package com.ibs.ibs_antdrivers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AnnouncementsAdapter(
    private val announcements: List<Announcement>
) : RecyclerView.Adapter<AnnouncementsAdapter.AnnouncementViewHolder>() {

    class AnnouncementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title = itemView.findViewById<TextView>(R.id.announcementTitle)
        val name = itemView.findViewById<TextView>(R.id.adminName)
        val date = itemView.findViewById<TextView>(R.id.announcementDate)
        val time = itemView.findViewById<TextView>(R.id.announcementTime)
        val preview = itemView.findViewById<TextView>(R.id.messagePreview)
        val card = itemView.findViewById<CardView>(R.id.announcementCard)

        val frontView = itemView.findViewById<LinearLayout>(R.id.frontView)
        val backView = itemView.findViewById<LinearLayout>(R.id.backView)
        val fullMessage = itemView.findViewById<TextView>(R.id.fullMessage)
        val viewAttachmentButton = itemView.findViewById<Button>(R.id.viewAttachmentButton)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.announcement_card, parent, false)
        return AnnouncementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        val ann = announcements[position]
        holder.title.text = ann.Title ?: ""
        holder.name.text = ann.AdminName ?: ""
        holder.preview.text = ann.Body?.take(500)?.toString() ?: ""

        ann.DatePosted?.let { isoString ->
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault())
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = isoFormat.parse(isoString)

                val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

                holder.date.text = dateFormatter.format(date!!)
                holder.time.text = timeFormatter.format(date)
            } catch (e: Exception) {
                holder.date.text = ""
                holder.time.text = ""
            }
        }

        if (!ann.FileUrl.isNullOrEmpty()) {
            holder.card.setOnClickListener {
                val isBackVisible = holder.backView.visibility == View.VISIBLE
                flipCard(!isBackVisible, holder.frontView, holder.backView)

                holder.fullMessage.text = ann.Body ?: ""

                holder.viewAttachmentButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ann.FileUrl))
                    it.context.startActivity(intent)
                }
            }
        } else {
            holder.card.setOnClickListener(null)
        }
    }

    private fun flipCard(showBack: Boolean, front: View, back: View) {
        val scale = front.context.resources.displayMetrics.density
        front.cameraDistance = 8000 * scale
        back.cameraDistance = 8000 * scale

        val animatorOut = ObjectAnimator.ofFloat(
            if (showBack) front else back,
            "rotationY",
            0f,
            90f
        ).apply {
            duration = 200
        }

        val animatorIn = ObjectAnimator.ofFloat(
            if (showBack) back else front,
            "rotationY",
            -90f,
            0f
        ).apply {
            duration = 200
        }

        animatorOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (showBack) {
                    front.visibility = View.GONE
                    back.visibility = View.VISIBLE
                } else {
                    back.visibility = View.GONE
                    front.visibility = View.VISIBLE
                }
                animatorIn.start()
            }
        })

        animatorOut.start()
    }


    override fun getItemCount(): Int {
        return announcements.size
    }

}

