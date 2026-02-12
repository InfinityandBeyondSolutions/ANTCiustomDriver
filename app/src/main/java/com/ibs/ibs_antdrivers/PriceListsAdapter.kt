package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.data.PriceList

class PriceListsAdapter(
    private val onItemClick: (PriceList) -> Unit,
) : ListAdapter<PriceList, PriceListsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<PriceList>() {
        override fun areItemsTheSame(oldItem: PriceList, newItem: PriceList): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PriceList, newItem: PriceList): Boolean = oldItem == newItem
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.priceListTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.priceListSubtitle)
        private val badge: TextView = itemView.findViewById(R.id.priceListBadge)

        fun bind(item: PriceList) {
            val displayTitle = item.title.ifBlank { item.name }.ifBlank { "Price List" }
            title.text = displayTitle

            val parts = buildList {
                if (item.companyName.isNotBlank()) add(item.companyName)
                if (item.effectiveDate.isNotBlank()) add("Effective: ${item.effectiveDate}")
            }
            subtitle.text = parts.joinToString(" • ")

            val badgeText = buildList {
                if (item.status.isNotBlank()) add(item.status)
                add("${item.items.size} items")
            }.joinToString(" • ")
            badge.text = badgeText

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_price_list, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
