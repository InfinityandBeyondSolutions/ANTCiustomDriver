package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.ibs.ibs_antdrivers.data.CallCyclesRepository
import com.ibs.ibs_antdrivers.ui.CallCycleAdapter
import com.ibs.ibs_antdrivers.ui.CallCycleRowItem
import kotlinx.coroutines.launch
import java.time.LocalDate

class CallCycleFragment : Fragment() {

    private lateinit var vm: CallCycleViewModel

    private lateinit var weekDropdown: AutoCompleteTextView
    private lateinit var activeChip: Chip
    private lateinit var tabs: TabLayout
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var weekRange: TextView

    private lateinit var adapter: CallCycleAdapter

    private var suppressWeekSelectionCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this, CallCycleViewModel.Factory())[CallCycleViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_call_cycle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        weekDropdown = view.findViewById(R.id.weekDropdown)
        activeChip = view.findViewById(R.id.activeChip)
        tabs = view.findViewById(R.id.tabs)
        recycler = view.findViewById(R.id.recycler)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        weekRange = view.findViewById(R.id.weekRange)

        adapter = CallCycleAdapter(
            onTodayStoreChecked = { id, _ ->
                if (id.startsWith("sc_")) {
                    vm.toggleTodaySpontaneousChecked(id)
                } else {
                    vm.toggleTodayStoreChecked(id)
                }
            },
            onTodayStoreViewDetails = { id ->
                if (id.startsWith("sc_")) {
                    // id is actually callId in spontaneous list; resolve to storeId
                    val storeId = vm.state.value.todaySpontaneousItems.firstOrNull { it.callId == id }?.storeId
                    if (!storeId.isNullOrBlank()) {
                        vm.openSpontaneousStoreDetails(storeId)
                    }
                } else {
                    vm.openStoreDetails(id)
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Tabs: Planned Week, Today's calls
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText("Planned week"))
        tabs.addTab(tabs.newTab().setText("Today's calls"))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 1) {
                    vm.refreshToday()
                }
                render(vm.state.value)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        weekDropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressWeekSelectionCallback) return@setOnItemClickListener
            val chosen = weekDropdown.adapter.getItem(position) as? String
            if (!chosen.isNullOrBlank()) vm.selectPlannedOption(chosen)
        }

        // Active chip now represents "Weekly Cycle" shortcut
        activeChip.setOnClickListener {
            vm.selectPlannedOption(CallCyclesRepository.WEEKLY_CYCLE_LABEL)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collect { state ->
                render(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.events.collect { e ->
                when (e) {
                    is CallCycleViewModel.UiEvent.OpenStoreDetails -> {
                        // Navigate to the store search screen and auto-search the storeId.
                        val bundle = Bundle().apply {
                            putString("prefillStoreQuery", e.storeId)
                        }
                        findNavController().navigate(R.id.navStore, bundle)
                    }
                }
            }
        }

        vm.load()
    }

    private fun render(state: CallCycleViewModel.UiState) {
        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.error?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
        }

        val selectedTab = tabs.selectedTabPosition.coerceAtLeast(0)
        val isPlannedTab = selectedTab == 0
        val isTodayTab = selectedTab == 1

        // Toggle inputs by tab
        view?.findViewById<View>(R.id.weekInputLayout)?.visibility = if (isPlannedTab) View.VISIBLE else View.GONE
        // Spontaneous date dropdown is no longer used
        view?.findViewById<View>(R.id.dateInputLayout)?.visibility = View.GONE

        // Planned dropdown
        if (isPlannedTab) {
            val opt = state.plannedOptions
            val weekAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opt)
            weekDropdown.setAdapter(weekAdapter)

            suppressWeekSelectionCallback = true
            try {
                val selected = state.selectedPlannedOption
                if (selected.isNotBlank() && weekDropdown.text?.toString() != selected) {
                    weekDropdown.setText(selected, false)
                }
            } finally {
                suppressWeekSelectionCallback = false
            }
        }

        // Weekly Cycle chip as a quick shortcut when not already on Weekly Cycle
        val showWeeklyChip = isPlannedTab && state.selectedPlannedOption != CallCyclesRepository.WEEKLY_CYCLE_LABEL
        activeChip.visibility = if (showWeeklyChip) View.VISIBLE else View.GONE
        if (showWeeklyChip) {
            activeChip.text = "Weekly Cycle"
        }

        // Header range label
        weekRange.text = when {
            isPlannedTab -> state.plannedCycle?.title ?: state.selectedPlannedOption
            else -> "Today"
        }

        val items = when {
            isPlannedTab -> plannedRows(state.plannedCycle)
            else -> todayRows(state)
        }

        adapter.submit(items)
        empty.visibility = if (!state.isLoading && items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun plannedRows(cycle: CallCyclesRepository.DisplayCycle?): List<CallCycleRowItem> {
        if (cycle == null) return listOf(CallCycleRowItem.Empty("No planned calling cycle found"))

        val days = when (cycle.source) {
            "WEEK" -> cycle.week?.days
            "TEMPLATE" -> cycle.template?.days
            else -> null
        }.orEmpty().sortedBy { it.dayOfWeek ?: 99 }

        if (days.isEmpty()) return listOf(CallCycleRowItem.Empty("No planned calls"))

        val out = mutableListOf<CallCycleRowItem>()
        out += CallCycleRowItem.Header(
            title = cycle.title,
            subtitle = if (cycle.source == "TEMPLATE") "Weekly Template" else "Selected Week",
        )

        for (d in days) {
            val dow = d.dayOfWeek ?: continue
            val storeIds = d.storeIds.orEmpty()
            out += CallCycleRowItem.Header(
                title = dayName(dow),
                subtitle = if (storeIds.isEmpty()) "No stores" else "${storeIds.size} stop${if (storeIds.size == 1) "" else "s"}"
            )

            if (storeIds.isEmpty()) {
                out += CallCycleRowItem.Empty("No planned calls")
            } else {
                storeIds.forEach { sid ->
                    out += CallCycleRowItem.Call(
                        title = sid,
                        subtitle = "Planned call",
                        badge = if (cycle.source == "TEMPLATE") "WEEKLY" else "WEEK",
                    )
                }
            }
        }

        return out
    }

    private fun todayRows(state: CallCycleViewModel.UiState): List<CallCycleRowItem> {
        val out = mutableListOf<CallCycleRowItem>()

        // Planned
        out += CallCycleRowItem.Header(
            title = state.todayTitle.ifBlank { "Today" },
            subtitle = "Planned stops",
        )

        if (state.todayStores.isEmpty()) {
            out += CallCycleRowItem.Empty("No planned stops scheduled for today")
        } else {
            state.todayStores.forEach { s ->
                out += CallCycleRowItem.TodayStore(
                    storeId = s.storeId,
                    title = s.storeName ?: s.storeId,
                    subtitle = s.storeAddress,
                    checked = s.checked,
                )
            }
        }

        // Spontaneous as checklist
        out += CallCycleRowItem.Header(
            title = "Spontaneous",
            subtitle = if (state.todaySpontaneousItems.isEmpty()) "None added today" else "${state.todaySpontaneousItems.size} added",
        )

        if (state.todaySpontaneousItems.isEmpty()) {
            out += CallCycleRowItem.Empty("No spontaneous calls for today")
        } else {
            state.todaySpontaneousItems.forEach { c ->
                // Use callId as the row key for checkbox state, but show store info.
                out += CallCycleRowItem.TodayStore(
                    storeId = c.callId, // IMPORTANT: this id is used for checkbox toggling (sc_...)
                    title = c.storeName ?: c.storeId,
                    subtitle = c.storeAddress,
                    checked = c.checked,
                )
            }
        }

        return out
    }

    private fun dayName(dayOfWeek: Int): String {
        // Your data seems to use 1..7. We’ll map 1=Mon .. 7=Sun.
        return when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Day $dayOfWeek"
        }
    }
}
