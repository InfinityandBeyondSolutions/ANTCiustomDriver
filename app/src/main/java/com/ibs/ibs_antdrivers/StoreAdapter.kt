package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class StoreAdapter(private var storeList: List<StoreData>,
                      private val onItemClick: (StoreData) -> Unit // Callback for item clicks
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>(), Filterable {

    // Original full list of customers for filtering
    private var storeListFull: List<StoreData> = ArrayList(storeList)

    // ViewHolder for Store items
    inner class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeName: TextView = itemView.findViewById(R.id.tvStoreName)
        val storeID: TextView = itemView.findViewById(R.id.tvStoreID)
        val storeAddress: TextView = itemView.findViewById(R.id.tvStoreAddress)
        val storeManager: TextView = itemView.findViewById(R.id.tvStoreManager)
        val storeFranchise: TextView = itemView.findViewById(R.id.tvStoreFranchise)

        init {
            itemView.setOnClickListener {
                onItemClick(storeList[adapterPosition]) // Pass selected customer to callback
            }
        }
    }

    // Creating the ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_snippet, parent, false)
        return StoreViewHolder(itemView)
    }

    // Binding customer data to the ViewHolder
    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = storeList[position]
        holder.storeName.text = "${currentStore.StoreName}"
        holder.storeID.text = "Store ID: ${currentStore.StoreID}"
        holder.storeAddress.text = "Address: ${currentStore.StoreAddress}"
        holder.storeFranchise.text = "Address: ${currentStore.StoreFranchise}"
        holder.storeManager.text = "Address: ${currentStore.StoreManager}"
    }

    // Get the number of items in the list
    override fun getItemCount(): Int {
        return storeList.size
    }

    // Providing the filter implementation
    override fun getFilter(): Filter {
        return storeFilter
    }

    private val storeFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList = ArrayList<StoreData>()

            if (constraint == null || constraint.isEmpty()) {
                filteredList.addAll(storeListFull)
            } else {
                val filterPattern = constraint.toString().lowercase().trim()

                for (store in storeListFull) {
                    if (store.StoreName.lowercase().contains(filterPattern)) {
                        filteredList.add(store)
                    }
                }
            }

            return FilterResults().apply { values = filteredList }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            storeList = results?.values as List<StoreData>
            notifyDataSetChanged()
        }
    }


}
