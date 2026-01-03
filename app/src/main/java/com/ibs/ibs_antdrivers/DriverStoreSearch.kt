package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class DriverStoreSearch : Fragment() {

    companion object {
        const val ARG_PREFILL_STORE_QUERY = "prefillStoreQuery"
    }

    private lateinit var btnBack: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchStores: SearchView
    private lateinit var spinnerFranchise: Spinner
    private lateinit var spinnerRegion: Spinner
    private lateinit var storeAdapter: StoreAdapter
    private lateinit var database: DatabaseReference

    private val storeList = ArrayList<StoreData>()

    private var pendingPrefillQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPrefillQuery = arguments?.getString(ARG_PREFILL_STORE_QUERY)?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_driver_store_search, container, false)

        btnBack = view.findViewById(R.id.ivBackButton)
        recyclerView = view.findViewById(R.id.recyclerViewStores)
        searchStores = view.findViewById(R.id.svStoreSearch)
        spinnerFranchise = view.findViewById(R.id.spinnerFranchise)
        spinnerRegion = view.findViewById(R.id.spinnerRegion)

        btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.navHomeDriver, false)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        storeAdapter = StoreAdapter(
            storeList,
            onCameraCapture = { store ->
                val bundle = Bundle().apply {
                    putString("storeId", store.StoreID)
                    putString("driverName", currentUserID ?: "Test Driver")
                    putString("storeName", store.StoreName)
                }
                findNavController().navigate(
                    R.id.action_driverStoreSearch_to_driverCaptureProductImages,
                    bundle
                )
            },
            onItemClick = { store -> Toast.makeText(requireContext(), "Store: ${store.StoreName}", Toast.LENGTH_SHORT).show() }
        )

        recyclerView.adapter = storeAdapter

        fetchStoresFromFirebase()
        setupSpinners()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Delay SearchView setup until spinners are initialized
        setupSearchView()

        // If we were opened from Call Cycle, prefill and trigger a filter.
        pendingPrefillQuery?.let { q ->
            // Clear filters to ensure the store is visible.
            if (spinnerFranchise.adapter != null) spinnerFranchise.setSelection(0)
            if (spinnerRegion.adapter != null) spinnerRegion.setSelection(0)

            // setQuery triggers the listener and calls filterStores().
            searchStores.setQuery(q, false)
            searchStores.isIconified = false
        }
    }

    private fun fetchStoresFromFirebase() {
        database = FirebaseDatabase.getInstance().getReference("Stores")
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return  // Prevents crash

                val franchises = mutableSetOf<String>()
                val regions = mutableSetOf<String>()

                storeList.clear()
                for (storeSnap in snapshot.children) {
                    val store = storeSnap.getValue(StoreData::class.java)
                    if (store != null) {
                        storeList.add(store)
                        franchises.add(store.StoreFranchise)
                        regions.add(store.StoreRegion)
                    }
                }

                storeAdapter.updateList(storeList)
                setupSpinnerData(spinnerFranchise, franchises)
                setupSpinnerData(spinnerRegion, regions)

                // Re-apply the deep-link query once data is loaded.
                pendingPrefillQuery?.let {
                    filterStores()
                    // Scroll to top so the match is immediately visible.
                    recyclerView.scrollToPosition(0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to fetch stores", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupSpinnerData(spinner: Spinner, data: Set<String>) {
        context?.let {
            val items = listOf("All") + data.sorted()
            val adapter = ArrayAdapter(it, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }
    }

    private fun setupSpinners() {
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                filterStores()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Set default selection to "All" if nothing selected
                parent.setSelection(0)
            }
        }

        spinnerFranchise.onItemSelectedListener = listener
        spinnerRegion.onItemSelectedListener = listener
    }

    private fun setupSearchView() {
        searchStores.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStores()
                return true
            }
        })
    }

    private fun filterStores() {
        val query = searchStores.query?.toString()?.lowercase() ?: ""
        val selectedFranchise = spinnerFranchise.selectedItem?.toString() ?: "All"
        val selectedRegion = spinnerRegion.selectedItem?.toString() ?: "All"

        val filtered = storeList.filter { store ->
            val idMatch = store.StoreID.lowercase().contains(query)
            val nameMatch = store.StoreName.lowercase().contains(query)
            val franchiseMatch = selectedFranchise == "All" || store.StoreFranchise == selectedFranchise
            val regionMatch = selectedRegion == "All" || store.StoreRegion == selectedRegion
            (idMatch || nameMatch) && franchiseMatch && regionMatch
        }

        storeAdapter.updateList(filtered)
    }
}