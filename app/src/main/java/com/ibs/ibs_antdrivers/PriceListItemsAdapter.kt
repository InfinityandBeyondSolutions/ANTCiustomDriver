package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.ibs.ibs_antdrivers.data.PriceListItem

class PriceListItemsAdapter : ListAdapter<PriceListItem, PriceListItemsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<PriceListItem>() {
        override fun areItemsTheSame(oldItem: PriceListItem, newItem: PriceListItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PriceListItem, newItem: PriceListItem): Boolean = oldItem == newItem
    }

    private var selectedItemId: String? = null

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.rowCard)
        val rowContent: LinearLayout = itemView.findViewById(R.id.rowContent)
        val colItem: TextView = itemView.findViewById(R.id.colItemNo)
        val colDesc: TextView = itemView.findViewById(R.id.colDescription)
        val colBrand: TextView = itemView.findViewById(R.id.colBrand)
        val colSize: TextView = itemView.findViewById(R.id.colSize)
        val colUnitBarcode: TextView = itemView.findViewById(R.id.colUnitBarcode)
        val colOuterBarcode: TextView = itemView.findViewById(R.id.colOuterBarcode)
        val colUnit: TextView = itemView.findViewById(R.id.colUnitPrice)
        val colCase: TextView = itemView.findViewById(R.id.colCasePrice)
        val colId: TextView = itemView.findViewById(R.id.colId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_pricelist_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.colItem.text = item.itemNo.ifBlank { item.id }
        holder.colDesc.text = item.description
        holder.colBrand.text = item.brand
        holder.colSize.text = item.size
        holder.colUnitBarcode.text = item.unitBarcode
        holder.colOuterBarcode.text = item.outerBarcode
        holder.colUnit.text = item.unitPrice
        holder.colCase.text = item.casePrice
        holder.colId.text = item.id

        val isSelected = selectedItemId != null && selectedItemId == item.id

        // Apply the filled background to the inner content, not the card
        holder.rowContent.setBackgroundResource(if (isSelected) R.drawable.bg_pricelist_row_selected else R.color.white)

        holder.itemView.setOnClickListener {
            val previous = selectedItemId
            selectedItemId = if (selectedItemId == item.id) null else item.id

            // refresh old + new rows only
            if (previous != null) {
                val oldIdx = currentList.indexOfFirst { it.id == previous }
                if (oldIdx >= 0) notifyItemChanged(oldIdx)
            }
            notifyItemChanged(holder.bindingAdapterPosition)
        }
    }
}
