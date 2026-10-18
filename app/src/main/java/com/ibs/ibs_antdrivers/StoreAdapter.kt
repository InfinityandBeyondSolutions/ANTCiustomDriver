package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class StoreAdapter(
    stores: List<StoreData>,
    private val onCameraCapture: (StoreData) -> Unit,
    private val onItemClick: (StoreData) -> Unit
) : ListAdapter<StoreData, StoreAdapter.StoreViewHolder>(StoreDiff) {

    init { submitList(stores) }

    inner class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeName: TextView = itemView.findViewById(R.id.tvStoreName)
        val storeID: TextView = itemView.findViewById(R.id.tvStoreID)
        val storeAddress: TextView = itemView.findViewById(R.id.tvStoreAddress)
        val contactPerson: TextView = itemView.findViewById(R.id.tvStoreContactPerson)
        val cameraButton: ImageView = itemView.findViewById(R.id.ivCameraButton)
        val galleryButton: ImageView = itemView.findViewById(R.id.ivViewGallery)
        val monogram: TextView? = itemView.findViewById(R.id.storeMonogram)

        init {
            itemView.setOnClickListener {
                currentStore()?.let(onItemClick)
            }

            cameraButton.setOnClickListener {
                currentStore()?.let(onCameraCapture)
            }

            galleryButton.setOnClickListener {
                val store = currentStore() ?: return@setOnClickListener
                val bundle = Bundle().apply {
                    putString("storeId", store.StoreID)
                    putString("storeName", store.StoreName)
                }
                itemView.findNavController().navigate(
                    R.id.action_driverStoreSearch_to_storeGallery,
                    bundle
                )
            }
        }

        private fun currentStore(): StoreData? {
            val position = bindingAdapterPosition
            return if (position == RecyclerView.NO_POSITION) null else currentList.getOrNull(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_snippet, parent, false)
        return StoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = getItem(position)
        holder.storeName.text = currentStore.StoreName
        holder.storeID.text = "Store ID: ${currentStore.StoreID}"
        holder.storeAddress.text = "Address: ${currentStore.StoreAddress}"
        holder.contactPerson.text = "Manager: ${currentStore.ContactPerson}"
        holder.monogram?.text = currentStore.StoreName.firstOrNull()?.uppercase() ?: "S"
    }

    fun updateList(newList: List<StoreData>, committed: () -> Unit = {}) {
        submitList(newList) { committed() }
    }

    private object StoreDiff : DiffUtil.ItemCallback<StoreData>() {
        override fun areItemsTheSame(oldItem: StoreData, newItem: StoreData) = oldItem.StoreID == newItem.StoreID
        override fun areContentsTheSame(oldItem: StoreData, newItem: StoreData) = oldItem == newItem
    }
}
