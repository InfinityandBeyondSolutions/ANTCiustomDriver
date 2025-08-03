package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import android.widget.Filter
import android.widget.Filterable

class StoreAdapter(
    private var storeList: List<StoreData>,
    private val onCameraCapture: (StoreData) -> Unit,
    private val onItemClick: (StoreData) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>(), Filterable {

    private var storeListFull: List<StoreData> = ArrayList(storeList)

    inner class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeName: TextView = itemView.findViewById(R.id.tvStoreName)
        val storeID: TextView = itemView.findViewById(R.id.tvStoreID)
        val storeAddress: TextView = itemView.findViewById(R.id.tvStoreAddress)
        val contactPerson: TextView = itemView.findViewById(R.id.tvStoreContactPerson)
        val cameraButton: ImageView = itemView.findViewById(R.id.ivCameraButton)
        val galleryButton: ImageView = itemView.findViewById(R.id.ivViewGallery)

        init {
            itemView.setOnClickListener {
                onItemClick(storeList[adapterPosition])
            }

            cameraButton.setOnClickListener {
                onCameraCapture(storeList[adapterPosition])
            }

            galleryButton.setOnClickListener {
                val store = storeList[adapterPosition]
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_snippet, parent, false)
        return StoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = storeList[position]
        holder.storeName.text = currentStore.StoreName
        holder.storeID.text = "Store ID: ${currentStore.StoreID}"
        holder.storeAddress.text = "Address: ${currentStore.StoreAddress}"
        holder.contactPerson.text = "Manager: ${currentStore.ContactPerson}"
    }

    override fun getItemCount(): Int = storeList.size

    override fun getFilter(): Filter = storeFilter

    private val storeFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList = ArrayList<StoreData>()
            val filterPattern = constraint?.toString()?.lowercase()?.trim() ?: ""

            if (filterPattern.isEmpty()) {
                filteredList.addAll(storeListFull)
            } else {
                for (store in storeListFull) {
                    if (store.StoreName.lowercase().contains(filterPattern)) {
                        filteredList.add(store)
                    }
                }
            }

            return FilterResults().apply { values = filteredList }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            storeList = results?.values as List<StoreData>
            notifyDataSetChanged()
        }
    }

    fun updateList(newList: List<StoreData>) {
        storeList = newList
        storeListFull = ArrayList(newList)
        notifyDataSetChanged()
    }
}