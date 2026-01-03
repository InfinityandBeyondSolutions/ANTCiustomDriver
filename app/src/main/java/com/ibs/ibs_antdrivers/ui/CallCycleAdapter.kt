package com.ibs.ibs_antdrivers.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.ibs.ibs_antdrivers.R

sealed class CallCycleRowItem {
    data class Header(val title: String, val subtitle: String? = null) : CallCycleRowItem()
    data class Call(
        val title: String,
        val subtitle: String,
        val badge: String,
        val iconRes: Int = R.drawable.callcycles,
    ) : CallCycleRowItem()

    data class TodayStore(
        val storeId: String,
        val title: String,
        val subtitle: String?,
        val checked: Boolean,
    ) : CallCycleRowItem()

    data class Empty(val message: String) : CallCycleRowItem()
}

class CallCycleAdapter(
    private val onTodayStoreChecked: ((storeId: String, checked: Boolean) -> Unit)? = null,
    private val onTodayStoreViewDetails: ((storeId: String) -> Unit)? = null,
    private val data: MutableList<CallCycleRowItem> = mutableListOf(),
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submit(newItems: List<CallCycleRowItem>) {
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return when (data[position]) {
            is CallCycleRowItem.Header -> 0
            is CallCycleRowItem.Call -> 1
            is CallCycleRowItem.TodayStore -> 2
            is CallCycleRowItem.Empty -> 3
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderVH(inflater.inflate(R.layout.row_call_cycle_header, parent, false))
            2 -> TodayStoreVH(inflater.inflate(R.layout.row_call_cycle_today_store, parent, false), onTodayStoreChecked, onTodayStoreViewDetails)
            3 -> EmptyVH(inflater.inflate(R.layout.row_call_cycle_empty, parent, false))
            else -> CallVH(inflater.inflate(R.layout.row_call_cycle_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = data[position]) {
            is CallCycleRowItem.Header -> (holder as HeaderVH).bind(item)
            is CallCycleRowItem.Empty -> (holder as EmptyVH).bind(item)
            is CallCycleRowItem.Call -> (holder as CallVH).bind(item)
            is CallCycleRowItem.TodayStore -> (holder as TodayStoreVH).bind(item)
        }
    }

    private class CallVH(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView? = v.findViewById(R.id.icon)
        private val title: TextView = v.findViewById(R.id.title)
        private val subtitle: TextView = v.findViewById(R.id.subtitle)
        private val badge: TextView? = v.findViewById(R.id.badge)

        fun bind(item: CallCycleRowItem.Call) {
            icon?.setImageResource(item.iconRes)
            title.text = item.title
            subtitle.text = item.subtitle
            badge?.text = item.badge
        }
    }

    private class TodayStoreVH(
        v: View,
        private val onChecked: ((storeId: String, checked: Boolean) -> Unit)?,
        private val onEye: ((storeId: String) -> Unit)?,
    ) : RecyclerView.ViewHolder(v) {
        private val check: MaterialCheckBox = v.findViewById(R.id.check)
        private val title: TextView = v.findViewById(R.id.title)
        private val subtitle: TextView = v.findViewById(R.id.subtitle)
        private val eye: ImageView = v.findViewById(R.id.eye)

        fun bind(item: CallCycleRowItem.TodayStore) {
            title.text = item.title
            val sub = item.subtitle
            if (sub.isNullOrBlank()) {
                subtitle.visibility = View.GONE
            } else {
                subtitle.visibility = View.VISIBLE
                subtitle.text = sub
            }

            // Avoid triggering listeners while binding.
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.checked
            check.setOnCheckedChangeListener { _, isChecked ->
                onChecked?.invoke(item.storeId, isChecked)
            }

            eye.setOnClickListener { onEye?.invoke(item.storeId) }
        }
    }

    private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.headerTitle)
        private val subtitle: TextView = v.findViewById(R.id.headerSubtitle)

        fun bind(item: CallCycleRowItem.Header) {
            title.text = item.title
            val sub = item.subtitle
            if (sub.isNullOrBlank()) {
                subtitle.visibility = View.GONE
            } else {
                subtitle.visibility = View.VISIBLE
                subtitle.text = sub
            }
        }
    }

    private class EmptyVH(v: View) : RecyclerView.ViewHolder(v) {
        private val text: TextView = v.findViewById(R.id.emptyText)

        fun bind(item: CallCycleRowItem.Empty) {
            text.text = item.message
        }
    }
}
