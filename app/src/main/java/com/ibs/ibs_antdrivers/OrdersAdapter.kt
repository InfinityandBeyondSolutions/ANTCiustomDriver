package com.ibs.ibs_antdrivers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.data.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrdersAdapter(
    private val onItemClick: (Order) -> Unit,
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
        private val orderDate: TextView = itemView.findViewById(R.id.orderDate)
        private val orderTotal: TextView = itemView.findViewById(R.id.orderTotal)
        private val orderStatus: TextView = itemView.findViewById(R.id.orderStatus)

        fun bind(item: Order) {
            orderNumber.text = "Order #${item.orderNumber}"
            storeName.text = item.storeName.ifBlank { "Unknown Store" }
            orderDate.text = if (item.createdAt > 0) {
                dateFormat.format(Date(item.createdAt))
            } else {
                "Date not available"
            }
            orderTotal.text = currencyFormat.format(item.totalAmount)

            orderStatus.text = item.status.replaceFirstChar { it.uppercase() }

            // Set status badge background based on status
            val statusBg = when (item.status.lowercase()) {
                "pending" -> R.drawable.bg_chip_status_neutral
                "submitted" -> R.drawable.bg_chip_status_online
                "processed" -> R.drawable.bg_greeting_badge
                "completed" -> R.drawable.bg_chip_status_online
                else -> R.drawable.bg_chip_status_neutral
            }
            orderStatus.setBackgroundResource(statusBg)

            itemView.setOnClickListener { onItemClick(item) }
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
