package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CatalogueCategoriesFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var containerLayout: LinearLayout
    private lateinit var noText: TextView
    private lateinit var recyclerView: RecyclerView
    private val categories = mutableListOf<CatalogueCategory>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_catalogue_categories, container, false)

        containerLayout = view.findViewById(R.id.categoriesContainer)
        noText = view.findViewById(R.id.noCategoriesText)

        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
        containerLayout.addView(recyclerView)

        fetchCategories()

        return view
    }

    private fun fetchCategories() {
        database = FirebaseDatabase.getInstance().getReference("catalogueCategories")

        database.get().addOnSuccessListener { snapshot ->
            categories.clear()

            Log.d("CatalogueCategories", "Snapshot exists: ${snapshot.exists()}")

            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    Log.d("CatalogueCategories", "Child key: ${child.key}, value: ${child.value}")

                    val category = child.getValue(CatalogueCategory::class.java)

                    // Only add if IsActive is true
                    if (category != null && category.IsActive) {
                        Log.d("CatalogueCategories", "Adding active category: ${category.Id} - ${category.Name}")
                        categories.add(category)
                    } else if (category != null) {
                        Log.d("CatalogueCategories", "Skipping inactive category: ${category.Id}")
                    }
                }

                if (categories.isNotEmpty()) {
                    noText.visibility = View.GONE
                    recyclerView.adapter = CatalogueCategoriesAdapter(categories) { category ->
                        onCategoryClicked(category)
                    }
                } else {
                    Log.d("CatalogueCategories", "No active catalogues found")
                    noText.visibility = View.VISIBLE
                }
            } else {
                Log.d("CatalogueCategories", "Snapshot does not exist")
                noText.visibility = View.VISIBLE
            }
        }.addOnFailureListener { error ->
            Log.e("CatalogueCategories", "Failed to load catalogues", error)
            Toast.makeText(requireContext(), "Failed to load catalogues: ${error.message}", Toast.LENGTH_SHORT).show()
            noText.visibility = View.VISIBLE
        }
    }

    private fun onCategoryClicked(category: CatalogueCategory) {
        // Check if PDF is attached
        if (category.Catalogue == null || category.Catalogue.FileUrl.isNullOrEmpty()) {
            showNoPdfWarning(category)
        } else {
            showCatalogueModal(category)
        }
    }

    private fun showNoPdfWarning(category: CatalogueCategory) {
        val categoryName = category.Name ?: "Catalogue ${category.Id}"

        val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "No PDF Available"
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = "The $categoryName catalogue does not have a PDF attached yet. Please check back later."
        dialogView.findViewById<View>(R.id.dialogNegativeButton).visibility = View.GONE

        val positiveButton = dialogView.findViewById<View>(R.id.dialogPositiveButton) as? android.widget.Button
        positiveButton?.text = "OK"
        positiveButton?.setOnClickListener {
            dialog.dismiss()
            // Dialog dismisses, user stays on landing page
        }

        dialog.show()
    }

    private fun showCatalogueModal(category: CatalogueCategory) {
        val categoryName = category.Name ?: "Catalogue ${category.Id}"

        val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "Open $categoryName"
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = "You are about to view the $categoryName catalogue."

        dialogView.findViewById<View>(R.id.dialogPositiveButton).setOnClickListener {
            dialog.dismiss()
            navigateToCatalogue(category)
        }

        dialogView.findViewById<View>(R.id.dialogNegativeButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateToCatalogue(category: CatalogueCategory) {
        val categoryName = category.Name ?: "Catalogue ${category.Id}"

        // Navigate to the existing CatalogueFragment with the category info
        val bundle = Bundle().apply {
            putInt("categoryId", category.Id)
            putString("categoryName", categoryName)
            putString("fileUrl", category.Catalogue?.FileUrl)
        }
        findNavController().navigate(R.id.action_catalogueCategories_to_catalogue, bundle)
    }
}

