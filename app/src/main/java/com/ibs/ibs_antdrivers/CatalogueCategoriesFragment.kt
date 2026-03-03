package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.data.CatalogueRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CatalogueCategoriesFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var noText: TextView
    private lateinit var recyclerView: RecyclerView
    private val categories = mutableListOf<CatalogueCategory>()

    private val repo = CatalogueRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_catalogue_categories, container, false)

        noText = view.findViewById(R.id.noCategoriesText)
        recyclerView = view.findViewById(R.id.categoriesRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        observeCategories()

        return view
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeActiveCategories(requireContext().applicationContext)
                .collectLatest { list ->
                    categories.clear()
                    categories.addAll(list)

                    if (categories.isNotEmpty()) {
                        noText.visibility = View.GONE
                        recyclerView.adapter = CatalogueCategoriesAdapter(categories) { category ->
                            onCategoryClicked(category)
                        }
                    } else {
                        noText.visibility = View.VISIBLE
                        // Best-effort: try network fetch once (won't crash if offline).
                        fetchCategoriesFromNetworkOnce()
                    }
                }
        }
    }

    private fun fetchCategoriesFromNetworkOnce() {
        database = FirebaseDatabase.getInstance().getReference("catalogueCategories")

        database.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val list = snapshot.children.mapNotNull { it.getValue(CatalogueCategory::class.java) }
                .filter { it.IsActive }

            if (list.isNotEmpty()) {
                // Immediately show, and worker will persist to Room on next refresh.
                categories.clear()
                categories.addAll(list)
                noText.visibility = View.GONE
                recyclerView.adapter = CatalogueCategoriesAdapter(categories) { category ->
                    onCategoryClicked(category)
                }
            }
        }.addOnFailureListener {
            // Offline: ignore, Room observer will keep UI stable.
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
