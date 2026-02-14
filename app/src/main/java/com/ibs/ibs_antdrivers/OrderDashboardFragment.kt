package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.data.OrdersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderDashboardFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var btnCreateOrder: MaterialButton

    private val repo = OrdersRepository()
    private lateinit var adapter: OrdersAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_order_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.ordersRecycler)
        progress = view.findViewById(R.id.ordersProgress)
        emptyText = view.findViewById(R.id.ordersEmpty)
        btnCreateOrder = view.findViewById(R.id.btnCreateOrder)

        adapter = OrdersAdapter(
            onViewClick = { order ->
                if (order.id.isBlank()) {
                    Snackbar.make(view, "Missing order id", Snackbar.LENGTH_SHORT).show()
                    return@OrdersAdapter
                }
                val b = Bundle().apply { putString("orderId", order.id) }
                findNavController().navigate(R.id.action_orderDashboard_to_orderDetail, b)
            }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        btnCreateOrder.setOnClickListener {
            findNavController().navigate(R.id.action_orderDashboard_to_createOrder)
        }

        loadOrders()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
    }

    private fun loadOrders() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Please sign in to view orders"
            adapter.submitList(emptyList())
            progress.visibility = View.GONE
            return
        }

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    repo.getOrdersByDriver(currentUser.uid)
                }
                progress.visibility = View.GONE

                if (data.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No orders yet.\nTap the button below to create your first order."
                    adapter.submitList(emptyList())
                } else {
                    emptyText.visibility = View.GONE
                    adapter.submitList(data)
                }
            } catch (t: Throwable) {
                progress.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Failed to load orders"
                Snackbar.make(requireView(), t.message ?: "Failed to load orders", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
