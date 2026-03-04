package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ibs.ibs_antdrivers.data.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrdersAdapter(
    private val onViewClick: (Order) -> Unit,
) : ListAdapter<Order, OrdersAdapter.VH>(Diff) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    object Diff : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem == newItem
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val orderNumber: TextView = itemView.findViewById(R.id.orderNumber)
        private val storeName: TextView = itemView.findViewById(R.id.orderStoreName)
        private val storeId: TextView = itemView.findViewById(R.id.orderStoreId)
        private val orderDate: TextView = itemView.findViewById(R.id.orderDate)
        private val orderTotal: TextView = itemView.findViewById(R.id.orderTotal)
        private val orderStatus: TextView = itemView.findViewById(R.id.orderStatus)
        private val btnView: MaterialButton = itemView.findViewById(R.id.btnViewOrder)

        fun bind(item: Order) {
            orderNumber.text = "Order #${item.orderNumber}"
            storeName.text = item.storeName.ifBlank { "Unknown Store" }
            storeId.text = if (item.storeId.isNotBlank()) "Store ID: ${item.storeId}" else ""

            orderDate.text = if (item.createdAt > 0) {
                dateFormat.format(Date(item.createdAt))
            } else {
                "Date not available"
            }
            orderTotal.text = currencyFormat.format(item.totalAmount)

            val normalizedStatus = item.status.trim().ifBlank { "pending" }
            val isCompleted = normalizedStatus.equals("completed", ignoreCase = true) || item.completedByUserId.isNotBlank()

            orderStatus.text = if (isCompleted) {
                "Completed"
            } else {
                normalizedStatus.replaceFirstChar { it.uppercase() }
            }

            // Set status badge background based on status
            val statusBg = when {
                isCompleted -> R.drawable.bg_chip_status_online
                normalizedStatus.equals("pending", true) || normalizedStatus.equals("new", true) -> R.drawable.bg_chip_status_neutral
                normalizedStatus.equals("submitted", true) -> R.drawable.bg_chip_status_online
                normalizedStatus.equals("processed", true) -> R.drawable.bg_greeting_badge
                else -> R.drawable.bg_chip_status_neutral
            }
            orderStatus.setBackgroundResource(statusBg)

            btnView.setOnClickListener { onViewClick(item) }
            itemView.setOnClickListener { onViewClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_order_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
