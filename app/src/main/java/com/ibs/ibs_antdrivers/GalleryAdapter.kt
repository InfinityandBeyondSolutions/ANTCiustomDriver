package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryImageAdapter(
    private val imageUrls: List<String>,
    private val imageNames: List<String>,
    private val onImageClick: (String, String) -> Unit
) : RecyclerView.Adapter<GalleryImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgStore: ImageView = itemView.findViewById(R.id.imgStoreGallery)
        val tvImageName: TextView = itemView.findViewById(R.id.tvImageName)

        init {
            itemView.setOnClickListener {
                onImageClick(imageUrls[adapterPosition], imageNames[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        val imageName = imageNames[position]
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .centerCrop()
            .into(holder.imgStore)
        holder.tvImageName.text = imageName
    }

    override fun getItemCount(): Int = imageUrls.size
}