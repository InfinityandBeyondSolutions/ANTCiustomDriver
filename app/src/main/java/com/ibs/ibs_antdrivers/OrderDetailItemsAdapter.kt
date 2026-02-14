package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

data class OrderDetailItemRow(
    val item: com.ibs.ibs_antdrivers.data.OrderItem,
    val lineTotal: Double,
)

class OrderDetailItemsAdapter : ListAdapter<OrderDetailItemRow, OrderDetailItemsAdapter.VH>(Diff) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    object Diff : DiffUtil.ItemCallback<OrderDetailItemRow>() {
        override fun areItemsTheSame(oldItem: OrderDetailItemRow, newItem: OrderDetailItemRow): Boolean {
            return oldItem.item.id == newItem.item.id
        }

        override fun areContentsTheSame(oldItem: OrderDetailItemRow, newItem: OrderDetailItemRow): Boolean {
            return oldItem == newItem
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProduct: TextView = itemView.findViewById(R.id.tvProduct)
        private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQty)
        private val tvCasePrice: TextView = itemView.findViewById(R.id.tvCasePrice)
        private val tvLineTotal: TextView = itemView.findViewById(R.id.tvLineTotal)

        fun bind(row: OrderDetailItemRow) {
            val item = row.item
            val title = buildString {
                val code = item.productCode.trim()
                val name = item.productName.trim()

                if (code.isNotBlank()) {
                    append(code)
                    if (name.isNotBlank()) append(" - ")
                }
                append(if (name.isNotBlank()) name else "Item")
            }

            tvProduct.text = title
            tvSize.text = if (item.size.isNotBlank()) item.size else "-"
            tvQty.text = item.quantity.toString()
            tvCasePrice.text = currencyFormat.format(item.casePriceExVat)
            tvLineTotal.text = currencyFormat.format(row.lineTotal)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_order_detail_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}

