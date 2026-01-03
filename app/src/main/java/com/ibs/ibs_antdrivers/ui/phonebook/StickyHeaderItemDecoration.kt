// StickyHeaderItemDecoration.kt
package com.ibs.ibs_antdrivers.ui.phonebook

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.R

class StickyHeaderItemDecoration(
    private val recyclerView: RecyclerView,
    private val adapter: AdminContactsAdapter
) : RecyclerView.ItemDecoration() {

    private var headerView: View? = null
    private var headerHeight = 0

    private fun ensureHeaderView() {
        if (headerView == null) {
            headerView = LayoutInflater.from(recyclerView.context)
                .inflate(R.layout.item_admin_header, recyclerView, false)
            measureAndLayout(headerView!!)
            headerHeight = headerView!!.measuredHeight
        }
    }

    private fun measureAndLayout(header: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        header.measure(widthSpec, heightSpec)
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val lm = parent.layoutManager as? LinearLayoutManager ?: return
        val topPos = lm.findFirstVisibleItemPosition()
        if (topPos == RecyclerView.NO_POSITION) return

        ensureHeaderView()

        // Determine current section title
        val currentTitle = adapter.getSectionForPosition(topPos) ?: return
        val header = headerView!!
        val titleView = header.findViewById<TextView>(R.id.sectionTitle)
        titleView.text = currentTitle

        // Ensure the measured width stays correct when RV width changes
        if (header.measuredWidth != recyclerView.width) {
            measureAndLayout(header)
            headerHeight = header.measuredHeight
        }

        // If next header is coming, push up
        var yOffset = 0
        val nextPos = topPos + 1
        val visibleCount = state.itemCount
        for (i in nextPos until minOf(topPos + 20, visibleCount)) {
            if (adapter.isHeader(i)) {
                val child = parent.findViewHolderForAdapterPosition(i)?.itemView ?: break
                val top = child.top
                if (top in 1..headerHeight) {
                    yOffset = top - headerHeight
                }
                break
            }
        }

        // Align sticky header with list content padding
        val xOffset = parent.paddingLeft.toFloat()

        c.save()
        c.translate(xOffset, yOffset.toFloat())
        header.draw(c)
        c.restore()
    }
}
