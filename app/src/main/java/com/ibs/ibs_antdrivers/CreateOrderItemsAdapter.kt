package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.data.OrderItem
import java.text.NumberFormat
import java.util.Locale

class CreateOrderItemsAdapter(
    private val onQuantityChanged: () -> Unit
) : ListAdapter<OrderItem, CreateOrderItemsAdapter.VH>(Diff) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    // We need to track quantities separately since the list is immutable
    private val quantityMap = mutableMapOf<String, Int>()

    object Diff : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean =
            oldItem == newItem && oldItem.quantity == newItem.quantity
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colItemNo: TextView = itemView.findViewById(R.id.colItemNo)
        val colDescription: TextView = itemView.findViewById(R.id.colDescription)
        val colBrand: TextView = itemView.findViewById(R.id.colBrand)
        val colSize: TextView = itemView.findViewById(R.id.colSize)
        val colUnitBarcode: TextView = itemView.findViewById(R.id.colUnitBarcode)
        val colOuterBarcode: TextView = itemView.findViewById(R.id.colOuterBarcode)
        val colCasePrice: TextView = itemView.findViewById(R.id.colCasePrice)
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val btnDecrease: ImageButton = itemView.findViewById(R.id.btnDecrease)
        val btnIncrease: ImageButton = itemView.findViewById(R.id.btnIncrease)
        val colLineTotal: TextView = itemView.findViewById(R.id.colLineTotal)

        fun bind(item: OrderItem) {
            // For display we show productCode if available, else productId
            colItemNo.text = item.productCode.ifBlank { item.productId }.ifBlank { item.id }
            colDescription.text = item.productName
            colBrand.text = item.brand
            colSize.text = item.size
            colUnitBarcode.text = item.unitBarcode
            colOuterBarcode.text = item.outerBarcode

            colCasePrice.text = if (item.casePriceExVat > 0) currencyFormat.format(item.casePriceExVat) else "-"

            val quantity = quantityMap[item.id] ?: 0
            tvQuantity.text = quantity.toString()

            val lineTotal = item.casePriceExVat * quantity
            colLineTotal.text = if (lineTotal > 0) currencyFormat.format(lineTotal) else "-"

            btnDecrease.setOnClickListener {
                val currentQty = quantityMap[item.id] ?: 0
                if (currentQty > 0) {
                    quantityMap[item.id] = currentQty - 1
                    notifyItemChanged(bindingAdapterPosition)
                    onQuantityChanged()
                }
            }

            btnIncrease.setOnClickListener {
                val currentQty = quantityMap[item.id] ?: 0
                quantityMap[item.id] = currentQty + 1
                notifyItemChanged(bindingAdapterPosition)
                onQuantityChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_create_order_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<OrderItem>?) {
        // Reset quantity map when new list is submitted
        quantityMap.clear()
        list?.forEach { item ->
            quantityMap[item.id] = item.quantity
        }
        super.submitList(list)
    }

    fun getGrandTotal(): Double {
        var total = 0.0
        currentList.forEach { item ->
            val quantity = quantityMap[item.id] ?: 0
            total += item.casePriceExVat * quantity
        }
        return total
    }

    fun getItemsWithQuantity(): List<OrderItem> {
        return currentList.mapNotNull { item ->
            val quantity = quantityMap[item.id] ?: 0
            if (quantity > 0) {
                item.copy(
                    quantity = quantity,
                    totalPrice = item.casePriceExVat * quantity
                )
            } else null
        }
    }
}
