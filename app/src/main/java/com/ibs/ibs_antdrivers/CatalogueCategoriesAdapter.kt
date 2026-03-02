package com.ibs.ibs_antdrivers

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CatalogueCategoriesAdapter(
    private val categories: List<CatalogueCategory>,
    private val onCategoryClick: (CatalogueCategory) -> Unit
) : RecyclerView.Adapter<CatalogueCategoriesAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card = itemView as MaterialCardView
        val categoryName = itemView.findViewById<TextView>(R.id.categoryName)
        val categoryIcon = itemView.findViewById<TextView>(R.id.categoryIcon)
        val noPdfBadge = itemView.findViewById<TextView>(R.id.noPdfBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.catalogue_category_item, parent, false)
        // Force a consistent fixed height for all cards (160dp)
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 160f,
            parent.context.resources.displayMetrics
        ).toInt()
        view.layoutParams = view.layoutParams.apply {
            height = heightPx
        }
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]

        // Use name from Firebase
        val name = category.Name ?: "Catalogue ${category.Id}"
        holder.categoryName.text = name

        // Set first letter as icon
        val firstLetter = name.firstOrNull()?.uppercaseChar() ?: '?'
        holder.categoryIcon.text = firstLetter.toString()

        // Show badge if no PDF is attached
        val hasPdf = category.Catalogue != null && !category.Catalogue.FileUrl.isNullOrEmpty()
        holder.noPdfBadge.visibility = if (hasPdf) View.GONE else View.VISIBLE

        holder.card.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}

