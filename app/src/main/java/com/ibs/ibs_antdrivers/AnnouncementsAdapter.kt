package com.ibs.ibs_antdrivers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.recyclerview.widget.LinearLayoutManager
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
        val attachmentsRecyclerView = itemView.findViewById<RecyclerView>(R.id.attachmentsRecyclerView)
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
        holder.preview.text = ann.Body?.take(500) ?: ""

        // Reset state to avoid RecyclerView recycling artifacts
        holder.frontView.visibility = View.VISIBLE
        holder.backView.visibility = View.GONE
        holder.frontView.rotationY = 0f
        holder.backView.rotationY = 0f
        holder.frontView.scaleX = 1f
        holder.frontView.scaleY = 1f
        holder.backView.scaleX = 1f
        holder.backView.scaleY = 1f

        // Apply camera distance once (helps the 3D flip feel less distorted)
        val scale = holder.itemView.context.resources.displayMetrics.density
        val camera = 10000f * scale
        holder.frontView.cameraDistance = camera
        holder.backView.cameraDistance = camera

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

        // Get all attachments - from new Attachments list or legacy FileUrl
        val attachments = mutableListOf<Attachment>()

        // Add attachments from new structure
        ann.Attachments?.let {
            attachments.addAll(it)
        }

        // Add legacy FileUrl if it exists and isn't in the attachments list
        if (!ann.FileUrl.isNullOrEmpty() && attachments.none { it.FileUrl == ann.FileUrl }) {
            attachments.add(Attachment(
                FileUrl = ann.FileUrl,
                FileName = "Attachment"
            ))
        }

        if (attachments.isNotEmpty()) {
            holder.card.setOnClickListener {
                val isBackVisible = holder.backView.visibility == View.VISIBLE
                flipCard(!isBackVisible, holder.frontView, holder.backView)

                holder.fullMessage.text = ann.Body ?: ""

                // Hide single button, show RecyclerView with all attachments
                holder.viewAttachmentButton.visibility = View.GONE
                holder.attachmentsRecyclerView.visibility = View.VISIBLE

                // Set up attachments RecyclerView
                holder.attachmentsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
                holder.attachmentsRecyclerView.adapter = AttachmentsAdapter(attachments)
            }
        } else {
            holder.card.setOnClickListener(null)
        }
    }

    private fun flipCard(showBack: Boolean, front: View, back: View) {
        // Material-ish easing without adding new deps
        val ease: Interpolator = PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)

        val outView = if (showBack) front else back
        val inView = if (showBack) back else front

        // Make sure incoming view starts hidden and rotated
        inView.visibility = View.VISIBLE
        inView.rotationY = -90f

        val durationOut = 240L
        val durationIn = 260L

        val rotationOut = ObjectAnimator.ofFloat(outView, View.ROTATION_Y, 0f, 90f).apply {
            duration = durationOut
            interpolator = ease
        }
        val scaleDown = ObjectAnimator.ofFloat(outView, View.SCALE_X, 1f, 0.98f).apply {
            duration = durationOut
            interpolator = ease
        }
        val scaleDownY = ObjectAnimator.ofFloat(outView, View.SCALE_Y, 1f, 0.98f).apply {
            duration = durationOut
            interpolator = ease
        }

        val outSet = AnimatorSet().apply { playTogether(rotationOut, scaleDown, scaleDownY) }

        val rotationIn = ObjectAnimator.ofFloat(inView, View.ROTATION_Y, -90f, 0f).apply {
            duration = durationIn
            interpolator = ease
        }
        val scaleUp = ObjectAnimator.ofFloat(inView, View.SCALE_X, 0.98f, 1f).apply {
            duration = durationIn
            interpolator = ease
        }
        val scaleUpY = ObjectAnimator.ofFloat(inView, View.SCALE_Y, 0.98f, 1f).apply {
            duration = durationIn
            interpolator = ease
        }

        val inSet = AnimatorSet().apply { playTogether(rotationIn, scaleUp, scaleUpY) }

        outSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                outView.visibility = View.GONE
                outView.rotationY = 0f
                outView.scaleX = 1f
                outView.scaleY = 1f
                inSet.start()
            }
        })

        outSet.start()
    }

    override fun getItemCount(): Int {
        return announcements.size
    }

    // Inner adapter for attachments list
    class AttachmentsAdapter(
        private val attachments: List<Attachment>
    ) : RecyclerView.Adapter<AttachmentsAdapter.AttachmentViewHolder>() {

        class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val fileNameText = itemView.findViewById<TextView>(R.id.attachmentFileName)
            val fileSizeText = itemView.findViewById<TextView>(R.id.attachmentFileSize)
            val viewButton = itemView.findViewById<Button>(R.id.viewAttachmentItemButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.attachment_item, parent, false)
            return AttachmentViewHolder(view)
        }

        override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
            val attachment = attachments[position]

            holder.fileNameText.text = attachment.FileName ?: "Attachment ${position + 1}"

            // Format file size
            attachment.FileSizeBytes?.let { bytes ->
                val sizeStr = when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                }
                holder.fileSizeText.text = sizeStr
                holder.fileSizeText.visibility = View.VISIBLE
            } ?: run {
                holder.fileSizeText.visibility = View.GONE
            }

            holder.viewButton.setOnClickListener {
                attachment.FileUrl?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    it.context.startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int = attachments.size
    }
}

