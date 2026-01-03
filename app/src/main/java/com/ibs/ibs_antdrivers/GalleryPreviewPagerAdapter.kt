package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryPreviewPagerAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<GalleryPreviewPagerAdapter.PageVH>() {

    class PageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPreview: ImageView = itemView.findViewById(R.id.ivPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_preview_page, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        Glide.with(holder.itemView)
            .load(imageUrls[position])
            .fitCenter()
            .into(holder.ivPreview)
    }

    override fun getItemCount(): Int = imageUrls.size
}

