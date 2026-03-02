package com.ibs.ibs_antdrivers

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(
    private val images: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgStore: ImageView = view.findViewById(R.id.imgStore)
        val btnRemoveImage: ImageView = view.findViewById(R.id.btnRemoveImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.store_image_item, parent, false)
        // Enforce square dimensions based on parent width divided by span count
        val spanCount = 3
        val spacing = (8 * parent.context.resources.displayMetrics.density).toInt() // 4dp margin * 2 sides
        val itemSize = (parent.measuredWidth - spacing * spanCount) / spanCount
        val safeSize = if (itemSize > 0) itemSize else parent.measuredWidth / spanCount
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, safeSize.coerceAtLeast(100))
        return ImageViewHolder(view)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.imgStore.setImageURI(images[position])
        holder.btnRemoveImage.setOnClickListener {
            onRemoveClick(position)
        }
    }
}