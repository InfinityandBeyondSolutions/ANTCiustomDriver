package com.ibs.ibs_antdrivers

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SearchView
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ibs.ibs_antdrivers.R
import android.widget.*
import com.google.firebase.database.*

class DriverStoreSearch : Fragment() {

    private lateinit var btnBack: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var database: DatabaseReference
    private lateinit var storeList: ArrayList<StoreData>
    private lateinit var storeAdapter: StoreAdapter
    private lateinit var searchStores: SearchView
    private lateinit var spinnerFranchise: Spinner
    private lateinit var spinnerRegion: Spinner

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driver_store_search, container, false)

        btnBack = view.findViewById(R.id.ivBackButton)
        recyclerView = view.findViewById(R.id.recyclerViewStores)
        searchStores = view.findViewById(R.id.svStoreSearch)
        spinnerFranchise = view.findViewById(R.id.spinnerFranchise)
        spinnerRegion = view.findViewById(R.id.spinnerRegion)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        storeList = ArrayList()
        storeAdapter = StoreAdapter(storeList) {}
        recyclerView.adapter = storeAdapter

        fetchStoresFromFirebase()
        setupSearchView()
        setupSpinners()

        return view
    }

    private fun fetchStoresFromFirebase() {
        database = FirebaseDatabase.getInstance().getReference("Stores")
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                storeList.clear()
                val franchiseSet = mutableSetOf<String>()
                val regionSet = mutableSetOf<String>()

                for (storeSnap in snapshot.children) {
                    val store = storeSnap.getValue(StoreData::class.java)
                    if (store != null) {
                        storeList.add(store)
                        franchiseSet.add(store.StoreFranchise)
                        regionSet.add(store.StoreRegion)
                    }
                }

                storeAdapter = StoreAdapter(storeList) {}
                recyclerView.adapter = storeAdapter
                storeAdapter.notifyDataSetChanged()

                // Set spinner values
                setupSpinnerData(spinnerFranchise, franchiseSet)
                setupSpinnerData(spinnerRegion, regionSet)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to fetch stores", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupSpinnerData(spinner: Spinner, data: Set<String>) {
        val list = ArrayList<String>()
        list.add("All")
        list.addAll(data.sorted())
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, list)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setupSearchView() {
        searchStores.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStores()
                return true
            }
        })
    }

    private fun setupSpinners() {
        spinnerFranchise.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterStores()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterStores()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun filterStores() {
        val query = searchStores.query.toString().lowercase()
        val selectedFranchise = spinnerFranchise.selectedItem.toString()
        val selectedRegion = spinnerRegion.selectedItem.toString()

        val filteredList = storeList.filter { store ->
            val matchName = store.StoreName.lowercase().contains(query)
            val matchFranchise = selectedFranchise == "All" || store.StoreFranchise == selectedFranchise
            val matchRegion = selectedRegion == "All" || store.StoreRegion == selectedRegion
            matchName && matchFranchise && matchRegion
        }

        storeAdapter = StoreAdapter(filteredList) {}
        recyclerView.adapter = storeAdapter
    }
}






