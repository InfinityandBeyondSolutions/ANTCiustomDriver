package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ibs.ibs_antdrivers.data.StoreSearchPage
import com.ibs.ibs_antdrivers.data.StoreSearchQuery
import com.ibs.ibs_antdrivers.data.StoreSearchQuery.Companion.STORE_PAGE_SIZE
import com.ibs.ibs_antdrivers.data.StoresRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DriverStoreSearch : Fragment(R.layout.fragment_driver_store_search) {
    private var views: StoreViews? = null
    private val repository = StoresRepository()
    private val search = MutableStateFlow(StoreSearchQuery())
    private var currentPage = StoreSearchPage(emptyList(), 0)
    private var nextPagePending = false
    private var scrollToTop = true
    private var refreshJob: Job? = null
    private var refreshing = false
    private var refreshFailed = false
    private var returnTo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = savedInstanceState?.getString("storeQuery")
            ?: arguments?.getString(ARG_PREFILL_STORE_QUERY).orEmpty()
        search.value = StoreSearchQuery(
            query = query.trim(),
            franchise = savedInstanceState?.getString("storeFranchise"),
            region = savedInstanceState?.getString("storeRegion"),
        )
        returnTo = arguments?.getString(ARG_RETURN_TO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = StoreViews(view)
        views = ui
        nextPagePending = false
        scrollToTop = true
        currentPage = StoreSearchPage(emptyList(), 0)
        val appContext = requireContext().applicationContext
        ui.back.setOnClickListener {
            if (returnTo == RETURN_TO_CALL_CYCLE) {
                if (!findNavController().popBackStack(R.id.navCallCycle, false)) {
                    findNavController().navigate(R.id.navCallCycle)
                }
            } else {
                findNavController().popBackStack(R.id.navHomeDriver, false)
            }
        }
        ui.refresh.setOnClickListener { refreshStores(force = true) }

        val adapter = StoreAdapter(emptyList(), onCameraCapture = { store ->
            val bundle = Bundle().apply {
                putString("storeId", store.StoreID)
                putString("driverName", currentUserID ?: "Test Driver")
                putString("storeName", store.StoreName)
            }
            findNavController().navigate(R.id.action_driverStoreSearch_to_driverCaptureProductImages, bundle)
        }, onItemClick = { store ->
            Toast.makeText(requireContext(), "Store: ${store.StoreName}", Toast.LENGTH_SHORT).show()
        })
        val manager = LinearLayoutManager(requireContext())
        ui.list.layoutManager = manager
        ui.list.adapter = adapter
        ui.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || nextPagePending || adapter.itemCount >= currentPage.totalCount) return
                if (manager.findLastVisibleItemPosition() < adapter.itemCount - 3) return
                nextPagePending = true
                val requestedSearch = search.value
                recyclerView.post {
                    if (views === ui && requestedSearch == search.value) {
                        search.value = requestedSearch.copy(limit = requestedSearch.limit + STORE_PAGE_SIZE)
                    }
                }
            }
        })

        ui.search.setIconifiedByDefault(false)
        ui.search.setQuery(search.value.query, false)
        ui.search.clearFocus()
        ui.search.isSubmitButtonEnabled = false
        ui.search.maxWidth = Int.MAX_VALUE
        ui.search.queryHint = getString(R.string.stores_search_hint)
        ui.search.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
            setTextColor(resources.getColor(R.color.webnavy, null))
            setHintTextColor(0xFF6B7280.toInt())
            textSize = 14f
        }
        ui.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                updateSearch(search.value.copy(query = newText.orEmpty().trim()))
                return true
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    search.flatMapLatest { repository.observeStorePage(appContext, it) }
                        .collectLatest { page ->
                            currentPage = page
                            val requestedSearch = search.value
                            adapter.updateList(page.stores) {
                                if (views === ui && requestedSearch == search.value) {
                                    nextPagePending = false
                                    if (scrollToTop) {
                                        ui.list.scrollToPosition(0)
                                        scrollToTop = false
                                    }
                                }
                            }
                            ui.count.text = if (page.stores.size < page.totalCount) {
                                getString(R.string.stores_visible_count, page.stores.size, page.totalCount)
                            } else page.totalCount.toString()
                            updateStatus()
                        }
                }
                launch {
                    repository.observeStoreFilters(appContext).collectLatest { options ->
                        setSpinnerOptions(ui.franchise, options.franchises, search.value.franchise)
                        setSpinnerOptions(ui.region, options.regions, search.value.region)
                        ui.franchise.onItemSelectedListener = filterListener {
                            updateSearch(search.value.copy(franchise = selectedFilter(ui.franchise)))
                        }
                        ui.region.onItemSelectedListener = filterListener {
                            updateSearch(search.value.copy(region = selectedFilter(ui.region)))
                        }
                    }
                }
            }
        }
        refreshStores(force = false)
    }

    private fun updateSearch(updated: StoreSearchQuery) {
        val previous = search.value
        if (previous.query == updated.query && previous.franchise == updated.franchise && previous.region == updated.region) return
        nextPagePending = false
        scrollToTop = true
        search.value = updated.copy(limit = STORE_PAGE_SIZE)
    }

    private fun setSpinnerOptions(spinner: Spinner, options: List<String>, selected: String?) {
        val items = listOf(getString(R.string.stores_filter_all)) + options
        val existing = spinner.adapter
        if (existing != null && existing.count == items.size && items.indices.all { existing.getItem(it) == items[it] }) return
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_white, items).apply {
            setDropDownViewResource(R.layout.item_dropdown_white)
        }
        spinner.setSelection(items.indexOf(selected).coerceAtLeast(0))
    }

    private fun selectedFilter(spinner: Spinner): String? =
        spinner.selectedItem?.toString()?.takeIf { spinner.selectedItemPosition > 0 }

    private fun filterListener(onSelected: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = onSelected()
        override fun onNothingSelected(parent: AdapterView<*>) = Unit
    }

    private fun refreshStores(force: Boolean) {
        if (refreshJob?.isActive == true) return
        val ui = views ?: return
        val appContext = requireContext().applicationContext
        refreshing = true
        refreshFailed = false
        ui.refresh.isEnabled = false
        updateStatus()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.refreshStoresIfNeeded(appContext, force)
                if (force) Toast.makeText(appContext, R.string.stores_updated, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshFailed = true
            } finally {
                if (views === ui) {
                    refreshing = false
                    ui.refresh.isEnabled = true
                    updateStatus()
                }
            }
        }
    }

    private fun updateStatus() {
        val ui = views ?: return
        val message = when {
            refreshFailed && currentPage.stores.isNotEmpty() -> R.string.stores_cached_refresh_failed
            refreshFailed -> R.string.stores_refresh_failed
            refreshing && currentPage.stores.isEmpty() -> R.string.stores_loading
            currentPage.totalCount == 0 -> R.string.stores_no_matches
            else -> null
        }
        ui.status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let { ui.status.setText(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("storeQuery", search.value.query)
        outState.putString("storeFranchise", search.value.franchise)
        outState.putString("storeRegion", search.value.region)
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        refreshJob = null
        views?.apply {
            list.clearOnScrollListeners()
            list.adapter = null
            search.setOnQueryTextListener(null)
            franchise.onItemSelectedListener = null
            region.onItemSelectedListener = null
        }
        views = null
        super.onDestroyView()
    }

    private class StoreViews(root: View) {
        val back: View = root.findViewById(R.id.ivBackButton)
        val list: RecyclerView = root.findViewById(R.id.recyclerViewStores)
        val search: SearchView = root.findViewById(R.id.svStoreSearch)
        val franchise: Spinner = root.findViewById(R.id.spinnerFranchise)
        val region: Spinner = root.findViewById(R.id.spinnerRegion)
        val count: TextView = root.findViewById(R.id.storeCount)
        val refresh: View = root.findViewById(R.id.refreshStores)
        val status: TextView = root.findViewById(R.id.storesStatus)
    }

    companion object {
        const val ARG_PREFILL_STORE_QUERY = "prefillStoreQuery"
        const val ARG_RETURN_TO = "returnTo"
        const val RETURN_TO_CALL_CYCLE = "callCycle"
    }
}
