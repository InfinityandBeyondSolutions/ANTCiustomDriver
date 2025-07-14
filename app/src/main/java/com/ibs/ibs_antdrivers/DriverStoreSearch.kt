/*package com.ibs.ibs_antdrivers

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

class DriverStoreSearch : Fragment() {

    private lateinit var btnBack: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var database: DatabaseReference
    private lateinit var storeList: ArrayList<StoreData>
    private lateinit var searchStores: SearchView
    private lateinit var usersReference: DatabaseReference

    private val franchises = listOf(
        "All", "Pick n' Pay", "Corporate Pick n' Pay", "Spar", "Super Spar",
        "Engen", "BP Pick 'n Pay", "President Hyper", "Makro", "Other"
    )

    private val provinces = listOf(
        "All", "Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal",
        "Limpopo", "Mpumalanga", "North West", "Northern Cape", "Western Cape"
    )

    private val gautengRegions = listOf(
        "All", "Alexandra", "Braamfontein", "Centurion", "Diepsloot", "Doornfontein",
        "Edenvale", "Florida", "Fourways", "Germiston", "Houghton", "Hyde Park",
        "Inner City (Johannesburg CBD)", "Kempton Park", "Krugersdorp", "Midrand",
        "Newtown", "Northcliff", "Ormonde", "Parktown", "Randburg", "Roodepoort",
        "Rosebank", "Sandton", "Soweto", "Springs", "Vereeniging", "Westbury", "Wynberg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle fragment arguments if needed
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout into a variable
        val view = inflater.inflate(R.layout.fragment_driver_store_search, container, false)

        val franchiseSpinner: Spinner = view.findViewById(R.id.spinnerFranchise)
        val provinceSpinner: Spinner = view.findViewById(R.id.spinnerProvince)

        val franchiseAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, franchises)
        franchiseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        franchiseSpinner.adapter = franchiseAdapter

        val provinceAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, provinces)
        provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        provinceSpinner.adapter = provinceAdapter

        //use this code for the filtering logic later when store data is available
        val selectedFranchise = franchiseSpinner.selectedItem.toString()
        val selectedRegion = provinceSpinner.selectedItem.toString()


        searchStores = view.findViewById(R.id.svStoreSearch)
        recyclerView = view.findViewById(R.id.recyclerViewStores)



        // Set click listener for the "Back" button
        btnBack.setOnClickListener {
            replaceFragment(DriverStoreSearch())
        }

        storeList = ArrayList()
        storeAdapter = StoreAdapter(storeList) { selectedStore ->
            openCustomerDetailsFragment(selectedStore)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = StoreAdapter


        usersReference = FirebaseDatabase.getInstance().getReference("Users")

        searchStores.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                fetchStores(newText)
                return false
            }
        })

        // Initial fetch to load all customers
        fetchStores(null)

        return view
    }



    private fun fetchStores(searchQuery: String?) {
        val adminId = FirebaseAuth.getInstance().currentUser?.uid
        if (adminId != null) {
            usersReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(usersSnapshot: DataSnapshot) {
                    var businessID: String? = null
                    for (businessSnapshot in usersSnapshot.children) {
                        val employeeSnapshot = businessSnapshot.child("Employees").child(adminId)
                        if (employeeSnapshot.exists()) {
                            businessID = employeeSnapshot.child("businessID").getValue(String::class.java)
                                ?: employeeSnapshot.child("businessId").getValue(String::class.java)
                            break
                        }
                    }

                    if (businessID != null) {
                        val storeReference = usersReference.child(businessID).child("Customers")
                        storeReference.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                storeList.clear()

                                for (storeSnapshot in snapshot.children) {
                                    val store = storeSnapshot.getValue(StoreData::class.java)
                                    if (store != null) {
                                        // If searchQuery is not null, filter by the query
                                        if (searchQuery.isNullOrBlank() ||
                                            store.StoreName.contains(searchQuery, true)) {
                                            storeList.add(store)
                                        }
                                    }
                                }
                                StoreAdapter.notifyDataSetChanged()
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Toast.makeText(requireContext(), "Error fetching customers.", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        Toast.makeText(requireContext(), "Unable to find associated business.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error fetching business information.", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }





    // Replaces the current fragment with the specified fragment
    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(com.google.firebase.database.R.id.frame_container, fragment)
            .addToBackStack(null)
            .commit()
    }


}*/






