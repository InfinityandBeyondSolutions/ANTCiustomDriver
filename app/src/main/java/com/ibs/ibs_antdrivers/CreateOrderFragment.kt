package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ibs.ibs_antdrivers.data.OrdersRepository
import com.ibs.ibs_antdrivers.data.PriceList
import com.ibs.ibs_antdrivers.data.PriceListsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CreateOrderFragment : Fragment() {

    // Header UI
    private lateinit var actvStore: MaterialAutoCompleteTextView
    private lateinit var actvPriceList: MaterialAutoCompleteTextView
    private lateinit var headerCard: MaterialCardView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvCustomerAccount: TextView
    private lateinit var tvPriceListName: TextView

    // Order Details UI
    private lateinit var tilOrderNumber: TextInputLayout
    private lateinit var etOrderNumber: TextInputEditText
    private lateinit var tilOrderNotes: TextInputLayout
    private lateinit var etOrderNotes: TextInputEditText

    // Price List Table UI
    private lateinit var tableProgress: ProgressBar
    private lateinit var tableEmpty: TextView
    private lateinit var orderItemsRecycler: RecyclerView

    // Grand Total UI
    private lateinit var tvGrandTotal: TextView
    private lateinit var btnSubmitOrder: MaterialButton
    private lateinit var submitProgress: ProgressBar

    // Data
    private val storeList = mutableListOf<StoreData>()
    private val priceListList = mutableListOf<PriceList>()

    // Display lists (kept in sync with adapters)
    private val storeDisplayList = mutableListOf<String>()
    private val priceListDisplayList = mutableListOf<String>()

    private var selectedStore: StoreData? = null
    private var selectedPriceList: PriceList? = null

    private var storeAdapter: ArrayAdapter<String>? = null
    private var priceListAdapter: ArrayAdapter<String>? = null

    // Keep an unfiltered store display list for search
    private val storeDisplayListAll = mutableListOf<String>()

    private val priceListsRepo = PriceListsRepository()
    private val ordersRepo = OrdersRepository()
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var orderItemsAdapter: CreateOrderItemsAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_create_order, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapter()
        loadStores()
        loadPriceLists()
        setupListeners()

        // Check for prefilled store from arguments (from Call Cycle)
        val prefillStoreId = arguments?.getString("prefillStoreId")
        if (!prefillStoreId.isNullOrBlank()) {
            // Wait for stores to load, then auto-select
            view.postDelayed({
                autoSelectStore(prefillStoreId)
            }, 500)
        } else {
            // Auto-focus on the first required field to guide the driver
            view.post {
                actvStore.requestFocus()
            }
        }
    }

    private fun initViews(view: View) {
        // Exposed dropdowns
        actvStore = view.findViewById(R.id.actvStore)
        actvPriceList = view.findViewById(R.id.actvPriceList)

        // Selection Header Card
        headerCard = view.findViewById(R.id.selectionHeaderCard)
        tvCustomerName = view.findViewById(R.id.tvCustomerName)
        tvCustomerAccount = view.findViewById(R.id.tvCustomerAccount)
        tvPriceListName = view.findViewById(R.id.tvPriceListName)

        // Order Details
        tilOrderNumber = view.findViewById(R.id.tilOrderNumber)
        etOrderNumber = view.findViewById(R.id.etOrderNumber)
        tilOrderNotes = view.findViewById(R.id.tilOrderNotes)
        etOrderNotes = view.findViewById(R.id.etOrderNotes)

        // Table
        tableProgress = view.findViewById(R.id.tableProgress)
        tableEmpty = view.findViewById(R.id.tableEmpty)
        orderItemsRecycler = view.findViewById(R.id.orderItemsRecycler)

        // Grand Total & Submit
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal)
        btnSubmitOrder = view.findViewById(R.id.btnSubmitOrder)
        submitProgress = view.findViewById(R.id.submitProgress)

        // Initially hide the selection header card
        headerCard.visibility = View.GONE

        // Make the dropdowns filter immediately
        actvStore.threshold = 0
        actvPriceList.threshold = 0

        // Show dropdown on focus/click so it behaves like a searchable picker
        actvStore.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvStore.showDropDown() }
        actvStore.setOnClickListener { actvStore.showDropDown() }
        actvPriceList.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvPriceList.showDropDown() }
        actvPriceList.setOnClickListener { actvPriceList.showDropDown() }
    }

    private fun setupAdapter() {
        orderItemsAdapter = CreateOrderItemsAdapter {
            updateGrandTotal()
        }
        orderItemsRecycler.layoutManager = LinearLayoutManager(requireContext())
        orderItemsRecycler.adapter = orderItemsAdapter
    }

    private fun setupListeners() {
        // Store selection
        actvStore.setOnItemClickListener { _, _, position, _ ->
            val selectedText = storeAdapter?.getItem(position)
            selectedStore = storeList.firstOrNull { displayStore(it) == selectedText }
            updateSelectionHeader()
        }

        // If user types something that doesn't match, clear selection
        actvStore.doAfterTextChanged {
            val t = it?.toString()?.trim().orEmpty()
            if (t.isBlank()) {
                selectedStore = null
                updateSelectionHeader()
                return@doAfterTextChanged
            }

            // Keep dropdown open while typing to emphasize search
            actvStore.post { actvStore.showDropDown() }

            // if text isn't exactly one of the list items, don't keep a stale selectedStore
            val exact = storeDisplayListAll.any { d -> d.equals(t, ignoreCase = true) }
            if (!exact) {
                selectedStore = null
                updateSelectionHeader()
            }
        }

        // Price list selection
        actvPriceList.setOnItemClickListener { _, _, position, _ ->
            val selectedText = priceListAdapter?.getItem(position)
            selectedPriceList = priceListList.firstOrNull { displayPriceList(it) == selectedText }
            if (selectedPriceList != null) {
                loadPriceListItems()
            } else {
                orderItemsAdapter.submitList(emptyList())
                updateGrandTotal()
            }
            updateSelectionHeader()
        }

        actvPriceList.doAfterTextChanged {
            val t = it?.toString()?.trim().orEmpty()
            if (t.isBlank()) {
                selectedPriceList = null
                orderItemsAdapter.submitList(emptyList())
                updateGrandTotal()
                updateSelectionHeader()
                return@doAfterTextChanged
            }

            actvPriceList.post { actvPriceList.showDropDown() }

            val exact = priceListDisplayList.any { d -> d.equals(t, ignoreCase = true) }
            if (!exact) {
                selectedPriceList = null
                orderItemsAdapter.submitList(emptyList())
                updateGrandTotal()
                updateSelectionHeader()
            }
        }

        // Order number validation
        etOrderNumber.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Validate when losing focus
                val orderNumber = etOrderNumber.text?.toString()?.trim() ?: ""
                if (orderNumber.isBlank()) {
                    tilOrderNumber.error = "Order number is required"
                } else {
                    tilOrderNumber.error = null
                }
            }
        }

        // Clear error when typing
        etOrderNumber.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                tilOrderNumber.error = null
            }
        }

        btnSubmitOrder.setOnClickListener {
            submitOrder()
        }
    }

    private fun loadStores() {
        val database = FirebaseDatabase.getInstance().getReference("Stores")
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                storeList.clear()
                for (storeSnap in snapshot.children) {
                    val store = storeSnap.getValue(StoreData::class.java)
                    if (store != null) {
                        store.StoreID = storeSnap.key ?: ""
                        storeList.add(store)
                    }
                }

                storeList.sortWith(compareBy({ it.StoreName.lowercase() }, { it.StoreID.lowercase() }))

                storeDisplayList.clear()
                storeDisplayList.addAll(storeList.map { displayStore(it) })

                storeDisplayListAll.clear()
                storeDisplayListAll.addAll(storeDisplayList)

                // Custom adapter so search matches Store Name OR Store ID regardless of display formatting
                storeAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, storeDisplayList) {
                    override fun getFilter(): Filter {
                        return object : Filter() {
                            override fun performFiltering(constraint: CharSequence?): FilterResults {
                                val query = constraint?.toString()?.trim()?.lowercase().orEmpty()
                                val results = FilterResults()

                                val filtered = if (query.isBlank()) {
                                    storeDisplayListAll
                                } else {
                                    storeList
                                        .filter { s ->
                                            s.StoreName.lowercase().contains(query) || s.StoreID.lowercase().contains(query)
                                        }
                                        .map { displayStore(it) }
                                }

                                results.values = filtered
                                results.count = filtered.size
                                return results
                            }

                            @Suppress("UNCHECKED_CAST")
                            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                val filtered = results?.values as? List<String> ?: emptyList()
                                clear()
                                addAll(filtered)
                                notifyDataSetChanged()
                            }

                            override fun convertResultToString(resultValue: Any?): CharSequence {
                                return resultValue?.toString().orEmpty()
                            }
                        }
                    }
                }

                actvStore.setAdapter(storeAdapter)
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    Snackbar.make(requireView(), "Failed to load stores", Snackbar.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun loadPriceLists() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { priceListsRepo.getAllPriceLists() }
                priceListList.clear()
                priceListList.addAll(data)

                priceListList.sortBy { it.title.ifBlank { it.name }.lowercase() }

                priceListDisplayList.clear()
                priceListDisplayList.addAll(priceListList.map { displayPriceList(it) })

                priceListAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, priceListDisplayList)
                actvPriceList.setAdapter(priceListAdapter)
            } catch (t: Throwable) {
                Snackbar.make(requireView(), "Failed to load price lists", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPriceListItems() {
        val priceList = selectedPriceList ?: return

        tableProgress.visibility = View.VISIBLE
        tableEmpty.visibility = View.GONE

        if (priceList.items.isEmpty()) {
            tableProgress.visibility = View.GONE
            tableEmpty.visibility = View.VISIBLE
            tableEmpty.text = "No items in this price list"
            orderItemsAdapter.submitList(emptyList())
        } else {
            tableProgress.visibility = View.GONE
            tableEmpty.visibility = View.GONE

            fun parseMoney(s: String): Double {
                // handles: "R 12.34", "12,34", "12.34"
                val cleaned = s
                    .replace("R", "", ignoreCase = true)
                    .replace("$", "")
                    .replace(" ", "")
                    .replace(",", "")
                    .trim()
                return cleaned.toDoubleOrNull() ?: 0.0
            }

            // Convert PriceListItems to OrderItems with quantity = 0
            val orderItems = priceList.items.map { item ->
                com.ibs.ibs_antdrivers.data.OrderItem(
                    id = item.id,
                    productId = item.id,
                    productCode = item.itemNo,
                    productName = item.description,
                    brand = item.brand,
                    size = item.size,
                    unitBarcode = item.unitBarcode,
                    outerBarcode = item.outerBarcode,
                    unitPriceExVat = parseMoney(item.unitPrice),
                    casePriceExVat = parseMoney(item.casePrice),
                    quantity = 0,
                    totalPrice = 0.0,
                )
            }
            orderItemsAdapter.submitList(orderItems)
        }

        updateGrandTotal()
    }

    private suspend fun loadMyProfileForOrder(): UserProfile {
        val uid = auth.currentUser?.uid ?: return UserProfile()
        return try {
            val snap = withContext(Dispatchers.IO) {
                FirebaseDatabase.getInstance().reference.child("users").child(uid).get().await()
            }
            snap.getValue(UserProfile::class.java) ?: UserProfile()
        } catch (_: Throwable) {
            UserProfile()
        }
    }

    private fun submitOrder() {
        // Validate inputs with auto-focus
        val store = selectedStore
        if (store == null) {
            Snackbar.make(requireView(), "Please select a store", Snackbar.LENGTH_SHORT).show()
            focusAndScrollTo(actvStore)
            return
        }

        val priceList = selectedPriceList
        if (priceList == null) {
            Snackbar.make(requireView(), "Please select a price list", Snackbar.LENGTH_SHORT).show()
            focusAndScrollTo(actvPriceList)
            return
        }

        val orderNumber = etOrderNumber.text?.toString()?.trim() ?: ""
        if (orderNumber.isBlank()) {
            tilOrderNumber.error = "Order number is required"
            focusAndScrollTo(etOrderNumber)
            return
        }
        tilOrderNumber.error = null

        val itemsWithQuantity = orderItemsAdapter.getItemsWithQuantity()
        if (itemsWithQuantity.isEmpty()) {
            Snackbar.make(requireView(), "Please add at least one item to the order", Snackbar.LENGTH_SHORT).show()
            // Scroll to the items table
            view?.findViewById<View>(R.id.orderItemsRecycler)?.let { recycler ->
                scrollToView(recycler)
            }
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Snackbar.make(requireView(), "Please sign in to submit orders", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        btnSubmitOrder.visibility = View.INVISIBLE
        submitProgress.visibility = View.VISIBLE

        val notes = etOrderNotes.text?.toString()?.trim().orEmpty()
        val totalAmount = orderItemsAdapter.getGrandTotal()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = loadMyProfileForOrder()
                val createdByFirstName = profile.firstName?.trim().orEmpty()
                val createdByLastName = profile.lastName?.trim().orEmpty()
                val createdByUserName = listOf(createdByFirstName, createdByLastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank {
                        currentUser.displayName ?: currentUser.email ?: ""
                    }

                val order = com.ibs.ibs_antdrivers.data.Order(
                    orderNumber = orderNumber,
                    notes = notes,
                    storeId = store.StoreID,
                    storeName = store.StoreName,
                    priceListId = priceList.id,
                    priceListName = priceList.title.ifBlank { priceList.name },
                    driverId = currentUser.uid,
                    driverName = currentUser.displayName ?: currentUser.email ?: "Driver",

                    createdByUserId = currentUser.uid,
                    createdByUserName = createdByUserName,
                    createdByFirstName = createdByFirstName,
                    createdByLastName = createdByLastName,

                    // Not completed yet at creation time
                    completedByUserId = "",
                    completedByUserName = "",
                    completedByFirstName = "",
                    completedByLastName = "",

                    priority = "normal",
                    status = "New",
                    totalAmount = totalAmount,
                    items = itemsWithQuantity,
                )

                withContext(Dispatchers.IO) {
                    ordersRepo.createOrder(order)
                }

                Snackbar.make(requireView(), "Order submitted successfully!", Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (t: Throwable) {
                btnSubmitOrder.visibility = View.VISIBLE
                submitProgress.visibility = View.GONE
                Snackbar.make(requireView(), t.message ?: "Failed to submit order", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSelectionHeader() {
        val store = selectedStore
        val priceList = selectedPriceList

        if (store != null && priceList != null) {
            headerCard.visibility = View.VISIBLE
            tvCustomerName.text = store.StoreName
            tvCustomerAccount.text = "Account: ${store.StoreID}"
            tvPriceListName.text = priceList.title.ifBlank { priceList.name }.ifBlank { "Price List" }
        } else {
            headerCard.visibility = View.GONE
        }
    }

    private fun updateGrandTotal() {
        val total = orderItemsAdapter.getGrandTotal()
        tvGrandTotal.text = "Grand Total: ${currencyFormat.format(total)}"
    }

    private fun displayStore(store: StoreData): String {
        val name = store.StoreName.trim().ifBlank { "Unknown" }
        val id = store.StoreID.trim()
        return if (id.isNotBlank()) "$name ($id)" else name
    }

    private fun displayPriceList(pl: PriceList): String {
        return pl.title.ifBlank { pl.name }.ifBlank { "Price List" }
    }

    /**
     * Focus on a view and scroll to it smoothly
     */
    private fun focusAndScrollTo(view: View) {
        view.post {
            // Request focus
            view.requestFocus()

            // For AutoCompleteTextView, show the dropdown
            if (view is MaterialAutoCompleteTextView) {
                view.showDropDown()
            }

            // Scroll to the view
            scrollToView(view)
        }
    }

    /**
     * Smooth scroll to make a view visible
     */
    private fun scrollToView(view: View) {
        view.post {
            // Find the NestedScrollView
            val scrollView = this.view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)

            if (scrollView != null) {
                // Calculate the view's position
                val location = IntArray(2)
                view.getLocationInWindow(location)

                val scrollViewLocation = IntArray(2)
                scrollView.getLocationInWindow(scrollViewLocation)

                // Calculate scroll position (with some padding at top)
                val scrollY = location[1] - scrollViewLocation[1] - 100

                // Smooth scroll to position
                scrollView.smoothScrollTo(0, scrollView.scrollY + scrollY)
            }
        }
    }

    /**
     * Auto-select a store based on storeId from arguments
     */
    private fun autoSelectStore(storeId: String) {
        val store = storeList.firstOrNull { it.StoreID.equals(storeId, ignoreCase = true) }
        if (store != null) {
            val displayText = displayStore(store)
            actvStore.setText(displayText, false)
            selectedStore = store
            updateSelectionHeader()

            // Show a toast to confirm the store was selected
            Toast.makeText(requireContext(), "Store ${store.StoreName} selected", Toast.LENGTH_SHORT).show()

            // Focus on price list field next
            actvPriceList.requestFocus()
        } else {
            Toast.makeText(requireContext(), "Store $storeId not found", Toast.LENGTH_SHORT).show()
        }
    }
}

