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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrderDashboardFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var btnCreateOrder: MaterialButton

    private val repo = OrdersRepository()
    private lateinit var adapter: OrdersAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }

    private var ordersJob: Job? = null

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

    override fun onDestroyView() {
        ordersJob?.cancel()
        ordersJob = null
        super.onDestroyView()
    }

    private fun loadOrders() {
        ordersJob?.cancel()

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

        ordersJob = viewLifecycleOwner.lifecycleScope.launch {
            repo.observeOrdersByDriver(requireContext().applicationContext, currentUser.uid)
                .collect { data ->
                    progress.visibility = View.GONE

                    if (data.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No orders yet.\nTap the button below to create your first order."
                        adapter.submitList(emptyList())
                    } else {
                        emptyText.visibility = View.GONE
                        adapter.submitList(data)
                    }
                }
        }
    }
}
