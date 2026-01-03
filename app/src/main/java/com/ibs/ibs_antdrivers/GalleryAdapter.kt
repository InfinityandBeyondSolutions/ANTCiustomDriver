package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class GalleryImageAdapter(
    private val imageUrls: List<String>,
    private val imageNames: List<String>,
    private val onImageClick: (Int) -> Unit,
    private val onDownloadClick: (String, String) -> Unit,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<GalleryImageAdapter.ImageViewHolder>() {

    private val selectedPositions = linkedSetOf<Int>()
    private var selectionMode: Boolean = false

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgStore: ImageView = itemView.findViewById(R.id.imgStoreGallery)
        val tvImageName: TextView = itemView.findViewById(R.id.tvImageName)
        val btnDownload: MaterialButton = itemView.findViewById(R.id.btnDownload)
        val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        val selectionCheck: ImageView = itemView.findViewById(R.id.selectionCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        val imageName = imageNames[position]

        val isSelected = selectedPositions.contains(position)
        holder.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.selectionCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        fun toggleSelection(adapterPos: Int) {
            if (selectedPositions.contains(adapterPos)) selectedPositions.remove(adapterPos) else selectedPositions.add(adapterPos)
            selectionMode = selectedPositions.isNotEmpty()
            notifyItemChanged(adapterPos)
            onSelectionChanged(selectedPositions.size)
        }

        holder.itemView.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener

            if (selectionMode) {
                toggleSelection(adapterPos)
            } else {
                onImageClick(adapterPos)
            }
        }

        holder.itemView.setOnLongClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            toggleSelection(adapterPos)
            true
        }

        holder.btnDownload.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                onDownloadClick(imageUrls[adapterPos], imageNames[adapterPos])
            }
        }

        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .centerCrop()
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.error_image)
            .into(holder.imgStore)

        holder.tvImageName.text = imageName
    }

    override fun getItemCount(): Int = imageUrls.size

    fun clearSelection() {
        if (selectedPositions.isEmpty()) return
        val toUpdate = selectedPositions.toList()
        selectedPositions.clear()
        selectionMode = false
        toUpdate.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<Pair<String, String>> {
        return selectedPositions
            .sorted()
            .mapNotNull { idx ->
                val url = imageUrls.getOrNull(idx)
                val name = imageNames.getOrNull(idx)
                if (url == null || name == null) null else url to name
            }
    }

    fun isInSelectionMode(): Boolean = selectionMode
}