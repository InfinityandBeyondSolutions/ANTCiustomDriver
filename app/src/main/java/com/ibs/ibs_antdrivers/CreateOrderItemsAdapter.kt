package com.ibs.ibs_antdrivers

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    // Track currently selected item for highlighting
    private var selectedPosition = -1
    private val handler = Handler(Looper.getMainLooper())

    object Diff : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean =
            oldItem == newItem && oldItem.quantity == newItem.quantity
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.itemCard)
        val colItemNo: TextView = itemView.findViewById(R.id.colItemNo)
        val colDescription: TextView = itemView.findViewById(R.id.colDescription)
        val colBrand: TextView = itemView.findViewById(R.id.colBrand)
        val colSize: TextView = itemView.findViewById(R.id.colSize)
        val colUnitBarcode: TextView = itemView.findViewById(R.id.colUnitBarcode)
        val colOuterBarcode: TextView = itemView.findViewById(R.id.colOuterBarcode)
        val colUnitPrice: TextView = itemView.findViewById(R.id.colUnitPrice)
        val colCasePrice: TextView = itemView.findViewById(R.id.colCasePrice)
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val btnDecrease: View = itemView.findViewById(R.id.btnDecrease)
        val btnIncrease: View = itemView.findViewById(R.id.btnIncrease)
        val colLineTotal: TextView = itemView.findViewById(R.id.colLineTotal)

        fun bind(item: OrderItem) {
            // For display we show productCode if available, else productId
            colItemNo.text = item.productCode.ifBlank { item.productId }.ifBlank { item.id }
            colDescription.text = item.productName
            colBrand.text = item.brand
            colSize.text = item.size

            // Store barcodes for data retention (they're hidden in layout)
            colUnitBarcode.text = item.unitBarcode
            colOuterBarcode.text = item.outerBarcode

            // Display unit price
            colUnitPrice.text = if (item.unitPriceExVat > 0) currencyFormat.format(item.unitPriceExVat) else "-"

            // Display case price
            colCasePrice.text = if (item.casePriceExVat > 0) currencyFormat.format(item.casePriceExVat) else "-"

            val quantity = quantityMap[item.id] ?: 0
            tvQuantity.text = quantity.toString()

            val lineTotal = item.casePriceExVat * quantity
            colLineTotal.text = if (lineTotal > 0) currencyFormat.format(lineTotal) else "-"

            // Get colors from context
            val whiteColor = itemView.context.getColor(android.R.color.white)
            val backgroundLight = itemView.context.getColor(R.color.background)
            val goldColor = itemView.context.getColor(R.color.webgold)
            val highlightColor = itemView.context.getColor(R.color.weblightgold)

            // Determine if this row is currently selected
            val isSelected = bindingAdapterPosition == selectedPosition

            // Visual highlighting for items with quantity OR selected state
            when {
                isSelected -> {
                    // Selected state - prominent highlight
                    itemCard.elevation = 8f
                    itemCard.strokeWidth = 3
                    itemCard.strokeColor = goldColor
                    itemCard.setCardBackgroundColor(highlightColor)
                }
                quantity > 0 -> {
                    // Has quantity - subtle highlight
                    itemCard.elevation = 4f
                    itemCard.strokeWidth = 2
                    itemCard.strokeColor = goldColor
                    itemCard.setCardBackgroundColor(backgroundLight)
                }
                else -> {
                    // Default state
                    itemCard.elevation = 0f
                    itemCard.strokeWidth = 0
                    itemCard.setCardBackgroundColor(whiteColor)
                }
            }

            // Make the entire row clickable for better UX
            itemCard.setOnClickListener {
                highlightRow(bindingAdapterPosition)
            }

            // Improved decrease button with visual feedback
            btnDecrease.setOnClickListener {
                val currentQty = quantityMap[item.id] ?: 0
                if (currentQty > 0) {
                    // Highlight the row
                    highlightRow(bindingAdapterPosition)

                    // Haptic feedback
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)

                    quantityMap[item.id] = currentQty - 1
                    notifyItemChanged(bindingAdapterPosition)
                    onQuantityChanged()
                }
            }

            // Disable decrease button if quantity is 0
            btnDecrease.isEnabled = quantity > 0
            btnDecrease.alpha = if (quantity > 0) 1f else 0.5f

            // Improved increase button with visual feedback
            btnIncrease.setOnClickListener {
                // Highlight the row
                highlightRow(bindingAdapterPosition)

                // Haptic feedback
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)

                val currentQty = quantityMap[item.id] ?: 0
                quantityMap[item.id] = currentQty + 1
                notifyItemChanged(bindingAdapterPosition)
                onQuantityChanged()
            }
        }
    }

    // Helper function to highlight a row temporarily
    private fun highlightRow(position: Int) {
        if (position == RecyclerView.NO_POSITION) return

        // Clear any pending unhighlight actions
        handler.removeCallbacksAndMessages(null)

        // Store old selected position
        val oldPosition = selectedPosition

        // Set new selected position
        selectedPosition = position

        // Refresh both positions
        if (oldPosition != -1 && oldPosition != position) {
            notifyItemChanged(oldPosition)
        }
        notifyItemChanged(position)

        // Auto-clear highlight after 2 seconds
        handler.postDelayed({
            if (selectedPosition == position) {
                selectedPosition = -1
                notifyItemChanged(position)
            }
        }, 2000)
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
