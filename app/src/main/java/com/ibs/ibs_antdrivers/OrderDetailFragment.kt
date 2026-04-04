package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.data.Order
import com.ibs.ibs_antdrivers.data.OrdersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderDetailFragment : Fragment() {

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView

    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var recycler: RecyclerView
    private lateinit var tvGrandTotal: TextView

    private val repo = OrdersRepository()
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var adapter: OrderDetailItemsAdapter

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_order_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = view.findViewById(R.id.tvHeaderSubtitle)
        progress = view.findViewById(R.id.orderDetailProgress)
        emptyText = view.findViewById(R.id.orderDetailEmpty)
        recycler = view.findViewById(R.id.orderItemsRecycler)
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal)

        adapter = OrderDetailItemsAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val orderId = arguments?.getString("orderId").orEmpty()
        if (orderId.isBlank()) {
            showError("Missing order id")
            return
        }

        loadOrder(orderId)
    }

    private fun loadOrder(orderId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("Please sign in to view this order")
            return
        }

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeOrderWithItems(requireContext().applicationContext, orderId)
                .collect { order ->
                    progress.visibility = View.GONE

                    if (order == null) {
                        showError("Order not found")
                        return@collect
                    }

                    // Safety: driver can only view their own orders
                    if (order.driverId.isNotBlank() && order.driverId != currentUser.uid) {
                        showError("You don't have permission to view this order")
                        return@collect
                    }

                    bindHeader(order)

                    if (order.items.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No items found for this order"
                        adapter.submitList(emptyList())
                        tvGrandTotal.text = "Grand Total: ${currencyFormat.format(0)}"
                    } else {
                        emptyText.visibility = View.GONE

                        val rows = order.items.map { item ->
                            val lineTotal = (item.quantity * item.casePriceExVat) +
                                           (item.unitQuantity * item.unitPriceExVat)
                            OrderDetailItemRow(item, lineTotal)
                        }
                        adapter.submitList(rows)

                        val grandTotal = rows.sumOf { it.lineTotal }
                        tvGrandTotal.text = "Grand Total: ${currencyFormat.format(grandTotal)}"
                    }
                }
        }
    }

    private fun bindHeader(order: Order) {
        val storeLine = buildString {
            val name = order.storeName.ifBlank { "Unknown Store" }
            val id = order.storeId
            append(name)
            if (id.isNotBlank()) append(" (").append(id).append(")")
        }

        tvHeaderTitle.text = "Order #${order.orderNumber.ifBlank { order.id }}"
        tvHeaderSubtitle.text = "$storeLine • ${if (order.createdAt > 0) dateFormat.format(Date(order.createdAt)) else "Date not available"}"
    }

    private fun showError(message: String) {
        progress.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = message
        adapter.submitList(emptyList())
        tvGrandTotal.text = currencyFormat.format(0)
    }
}

