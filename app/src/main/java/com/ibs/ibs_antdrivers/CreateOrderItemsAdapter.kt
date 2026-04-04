package com.ibs.ibs_antdrivers

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
    // Track unit quantities separately
    private val unitQuantityMap = mutableMapOf<String, Int>()

    // Full unfiltered list so filtering never loses items or quantities
    private val masterList = mutableListOf<OrderItem>()

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
        // Cases quantity controls
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val btnDecrease: View = itemView.findViewById(R.id.btnDecrease)
        val btnIncrease: View = itemView.findViewById(R.id.btnIncrease)
        // Unit quantity controls
        val tvUnitQuantity: TextView = itemView.findViewById(R.id.tvUnitQuantity)
        val btnUnitDecrease: View = itemView.findViewById(R.id.btnUnitDecrease)
        val btnUnitIncrease: View = itemView.findViewById(R.id.btnUnitIncrease)
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

            val unitQuantity = unitQuantityMap[item.id] ?: 0
            tvUnitQuantity.text = unitQuantity.toString()

            val lineTotal = (item.casePriceExVat * quantity) + (item.unitPriceExVat * unitQuantity)
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
                quantity > 0 || unitQuantity > 0 -> {
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

            // Units decrease
            btnUnitDecrease.setOnClickListener {
                val currentUnitQty = unitQuantityMap[item.id] ?: 0
                if (currentUnitQty > 0) {
                    highlightRow(bindingAdapterPosition)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    unitQuantityMap[item.id] = currentUnitQty - 1
                    notifyItemChanged(bindingAdapterPosition)
                    onQuantityChanged()
                }
            }
            btnUnitDecrease.isEnabled = unitQuantity > 0
            btnUnitDecrease.alpha = if (unitQuantity > 0) 1f else 0.5f

            // Units increase
            btnUnitIncrease.setOnClickListener {
                highlightRow(bindingAdapterPosition)
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                val currentUnitQty = unitQuantityMap[item.id] ?: 0
                unitQuantityMap[item.id] = currentUnitQty + 1
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
        // Reset quantity maps and master list when a completely new price-list is loaded
        quantityMap.clear()
        unitQuantityMap.clear()
        masterList.clear()
        list?.forEach { item ->
            quantityMap[item.id] = item.quantity
            unitQuantityMap[item.id] = item.unitQuantity
            masterList.add(item)
        }
        super.submitList(list)
    }

    /** Filter the displayed rows by [query] without losing quantity state. */
    fun filter(query: String) {
        val trimmed = query.trim().lowercase()
        val filtered = if (trimmed.isBlank()) {
            masterList.toList()
        } else {
            masterList.filter { item ->
                item.productCode.lowercase().contains(trimmed) ||
                item.productName.lowercase().contains(trimmed) ||
                item.brand.lowercase().contains(trimmed) ||
                item.size.lowercase().contains(trimmed)
            }
        }
        // Use DiffUtil-aware submitList but do NOT clear quantityMap
        super.submitList(filtered)
    }

    fun getGrandTotal(): Double {
        var total = 0.0
        // Always calculate from master list so filtered-out items with qty still count
        masterList.forEach { item ->
            val quantity = quantityMap[item.id] ?: 0
            val unitQuantity = unitQuantityMap[item.id] ?: 0
            total += (item.casePriceExVat * quantity) + (item.unitPriceExVat * unitQuantity)
        }
        return total
    }

    fun getItemsWithQuantity(): List<OrderItem> {
        return masterList.mapNotNull { item ->
            val quantity = quantityMap[item.id] ?: 0
            val unitQuantity = unitQuantityMap[item.id] ?: 0
            if (quantity > 0 || unitQuantity > 0) {
                item.copy(
                    quantity = quantity,
                    unitQuantity = unitQuantity,
                    totalPrice = (item.casePriceExVat * quantity) + (item.unitPriceExVat * unitQuantity)
                )
            } else null
        }
    }
}
