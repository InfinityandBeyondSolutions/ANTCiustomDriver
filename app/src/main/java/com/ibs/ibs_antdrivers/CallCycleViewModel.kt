package com.ibs.ibs_antdrivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ibs.ibs_antdrivers.data.CallCyclesRepository
import com.ibs.ibs_antdrivers.data.CompletedCallsRepository
import com.ibs.ibs_antdrivers.data.StoresRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CallCycleViewModel(
    private val repo: CallCyclesRepository = CallCyclesRepository(),
    private val storesRepo: StoresRepository = StoresRepository(),
    private val completedCallsRepo: CompletedCallsRepository = CompletedCallsRepository(),
) : ViewModel() {

    // Simple in-memory store cache to avoid re-fetching the same store repeatedly.
    private val storeCache: MutableMap<String, StoreData> = ConcurrentHashMap()

    sealed class UiEvent {
        data class OpenStoreDetails(val storeId: String, val storeName: String?) : UiEvent()
        data class ConfirmStartCall(val storeId: String, val storeName: String?) : UiEvent()
        data class ConfirmEndCall(val storeId: String, val storeName: String?) : UiEvent()
        data class NavigateToCreateOrder(val storeId: String, val storeName: String?) : UiEvent()
        data class ShowError(val message: String) : UiEvent()
        data class ShowSuccess(val message: String) : UiEvent()
    }

    data class TodayStore(
        val storeId: String,
        val storeName: String? = null,
        val storeAddress: String? = null,
        val checked: Boolean = false,
        val isCallActive: Boolean = false,
    )

    data class TodaySpontaneousItem(
        val callId: String,
        val storeId: String,
        val storeName: String? = null,
        val storeAddress: String? = null,
        val checked: Boolean = false,
        val createdAt: Long? = null,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val driverUid: String? = null,

        // Planned dropdown
        val plannedOptions: List<String> = emptyList(), // includes "Weekly Cycle" + week keys
        val selectedPlannedOption: String = CallCyclesRepository.WEEKLY_CYCLE_LABEL,

        // Resolved planned cycle for selected option
        val plannedCycle: CallCyclesRepository.DisplayCycle? = null,

        // Weekly plan store name cache (keyed by storeId)
        val plannedStoresById: Map<String, StoreData> = emptyMap(),

        // Today tab
        val todayTitle: String = "",
        val todayStores: List<TodayStore> = emptyList(),
        val checkedTodayStoreIds: Set<String> = emptySet(),
        val todaySpontaneous: List<CallCyclesRepository.SpontaneousCall> = emptyList(),
        val todaySpontaneousItems: List<TodaySpontaneousItem> = emptyList(),
        val checkedTodaySpontaneousCallIds: Set<String> = emptySet(),

        // Spontaneous tab
        val spontaneousDate: String = LocalDate.now().toString(), // yyyy-MM-dd
        val spontaneous: List<CallCyclesRepository.SpontaneousCall> = emptyList(),

        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    fun load(initialPlannedSelection: String? = null) {
        viewModelScope.launch {
            val uid = repo.uid()
            if (uid.isNullOrBlank()) {
                _state.value = UiState(isLoading = false, error = "Not signed in")
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, driverUid = uid, error = null)

            try {
                val weekKeys = repo.getAvailableWeeks(uid)
                val plannedOptions = listOf(CallCyclesRepository.WEEKLY_CYCLE_LABEL) + weekKeys

                val selection = when {
                    !initialPlannedSelection.isNullOrBlank() && plannedOptions.contains(initialPlannedSelection) -> initialPlannedSelection
                    else -> _state.value.selectedPlannedOption
                }

                val plannedCycle = repo.resolvePlannedCycle(uid, selection)
                val plannedStoresById = loadStoresForPlannedCycle(plannedCycle)

                val today = LocalDate.now()
                val todayTitle = buildTodayTitle(today)
                val todayCycle = repo.resolvePlannedCycle(uid, currentWeekKey(today))
                val todayStores = buildTodayStores(todayCycle, _state.value.checkedTodayStoreIds)
                val todaySpontaneous = repo.getSpontaneousCallsForDate(uid, today.toString())
                val todaySpontaneousItems = buildTodaySpontaneousItems(todaySpontaneous, _state.value.checkedTodaySpontaneousCallIds)

                val date = _state.value.spontaneousDate.ifBlank { LocalDate.now().toString() }
                val spontaneous = repo.getSpontaneousCallsForDate(uid, date)

                _state.value = _state.value.copy(
                    isLoading = false,
                    driverUid = uid,
                    plannedOptions = plannedOptions,
                    selectedPlannedOption = selection,
                    plannedCycle = plannedCycle,
                    plannedStoresById = plannedStoresById,
                    todayTitle = todayTitle,
                    todayStores = todayStores,
                    todaySpontaneous = todaySpontaneous,
                    todaySpontaneousItems = todaySpontaneousItems,
                    spontaneousDate = date,
                    spontaneous = spontaneous,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load call cycle",
                )
            }
        }
    }

    fun selectPlannedOption(option: String) {
        val uid = _state.value.driverUid ?: return
        if (option == _state.value.selectedPlannedOption) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, selectedPlannedOption = option, error = null)
            try {
                val plannedCycle = repo.resolvePlannedCycle(uid, option)
                val plannedStoresById = loadStoresForPlannedCycle(plannedCycle)
                _state.value = _state.value.copy(
                    isLoading = false,
                    plannedCycle = plannedCycle,
                    plannedStoresById = plannedStoresById,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load planned calls")
            }
        }
    }

    private suspend fun loadStoresForPlannedCycle(
        plannedCycle: CallCyclesRepository.DisplayCycle?,
    ): Map<String, StoreData> {
        if (plannedCycle == null) return emptyMap()

        val days = when (plannedCycle.source) {
            "WEEK" -> plannedCycle.week?.days
            "TEMPLATE" -> plannedCycle.template?.days
            else -> null
        }.orEmpty()

        val ids = days.flatMap { it.storeIds.orEmpty() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (ids.isEmpty()) return emptyMap()

        // Fill from cache first.
        val out = LinkedHashMap<String, StoreData>(ids.size)
        val missing = ArrayList<String>()
        for (id in ids) {
            val cached = storeCache[id]
            if (cached != null) out[id] = cached else missing.add(id)
        }

        if (missing.isEmpty()) return out

        // Fetch missing concurrently.
        coroutineScope {
            val fetched = missing.map { id ->
                async {
                    id to runCatching { storesRepo.getStoreById(id) }.getOrNull()
                }
            }.awaitAll()

            for ((id, store) in fetched) {
                if (store != null) {
                    storeCache[id] = store
                    out[id] = store
                }
            }
        }

        return out
    }

    fun refreshToday() {
        val uid = _state.value.driverUid ?: return
        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val todayTitle = buildTodayTitle(today)
                val todayCycle = repo.resolvePlannedCycle(uid, currentWeekKey(today))
                val todayStores = buildTodayStores(todayCycle, _state.value.checkedTodayStoreIds)
                val todaySpontaneous = repo.getSpontaneousCallsForDate(uid, today.toString())
                val todaySpontaneousItems = buildTodaySpontaneousItems(todaySpontaneous, _state.value.checkedTodaySpontaneousCallIds)
                _state.value = _state.value.copy(
                    todayTitle = todayTitle,
                    todayStores = todayStores,
                    todaySpontaneous = todaySpontaneous,
                    todaySpontaneousItems = todaySpontaneousItems,
                )
            } catch (_: Exception) {
                // Silent refresh; errors are shown by primary load paths.
            }
        }
    }

    fun selectSpontaneousDate(dateKey: String) {
        val uid = _state.value.driverUid ?: return
        if (dateKey == _state.value.spontaneousDate) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, spontaneousDate = dateKey, error = null)
            try {
                val calls = repo.getSpontaneousCallsForDate(uid, dateKey)
                _state.value = _state.value.copy(isLoading = false, spontaneous = calls, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load spontaneous calls")
            }
        }
    }

    fun toggleTodayStoreChecked(storeId: String) {
        val current = _state.value
        val newChecked = current.checkedTodayStoreIds.toMutableSet().apply {
            if (contains(storeId)) remove(storeId) else add(storeId)
        }.toSet()

        val newStores = current.todayStores.map {
            if (it.storeId == storeId) it.copy(checked = newChecked.contains(storeId)) else it
        }

        _state.value = current.copy(checkedTodayStoreIds = newChecked, todayStores = newStores)
    }

    fun toggleTodaySpontaneousChecked(callId: String) {
        val current = _state.value
        val newChecked = current.checkedTodaySpontaneousCallIds.toMutableSet().apply {
            if (contains(callId)) remove(callId) else add(callId)
        }.toSet()

        val newItems = current.todaySpontaneousItems.map {
            if (it.callId == callId) it.copy(checked = newChecked.contains(callId)) else it
        }

        _state.value = current.copy(
            checkedTodaySpontaneousCallIds = newChecked,
            todaySpontaneousItems = newItems,
        )
    }

    fun openStoreDetails(storeId: String) {
        viewModelScope.launch {
            val name = _state.value.todayStores.firstOrNull { it.storeId == storeId }?.storeName
            _events.emit(UiEvent.OpenStoreDetails(storeId = storeId, storeName = name))
        }
    }

    fun openSpontaneousStoreDetails(storeId: String) {
        viewModelScope.launch {
            val name = _state.value.todaySpontaneousItems.firstOrNull { it.storeId == storeId }?.storeName
            _events.emit(UiEvent.OpenStoreDetails(storeId = storeId, storeName = name))
        }
    }

    fun requestStartCall(storeId: String) {
        viewModelScope.launch {
            val storeName = _state.value.todayStores.firstOrNull { it.storeId == storeId }?.storeName
            _events.emit(UiEvent.ConfirmStartCall(storeId = storeId, storeName = storeName))
        }
    }

    fun confirmStartCall(storeId: String) {
        viewModelScope.launch {
            try {
                val storeName = _state.value.todayStores.firstOrNull { it.storeId == storeId }?.storeName
                completedCallsRepo.startCall(storeId, storeName, "planned")
                _events.emit(UiEvent.ShowSuccess("Call started for $storeName"))

                // Refresh the today list to update the button state
                refreshToday()
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowError(e.message ?: "Failed to start call"))
            }
        }
    }

    fun requestEndCall(storeId: String) {
        viewModelScope.launch {
            val storeName = _state.value.todayStores.firstOrNull { it.storeId == storeId }?.storeName
            _events.emit(UiEvent.ConfirmEndCall(storeId = storeId, storeName = storeName))
        }
    }

    fun confirmEndCall(storeId: String) {
        viewModelScope.launch {
            try {
                val activeCall = completedCallsRepo.getActiveCallForStore(storeId)
                if (activeCall != null) {
                    completedCallsRepo.endCall(activeCall.callId, activeCall.date)
                    _events.emit(UiEvent.ShowSuccess("Call ended for ${activeCall.storeName}"))

                    // Refresh the today list to update the button state
                    refreshToday()
                } else {
                    _events.emit(UiEvent.ShowError("No active call found for this store"))
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowError(e.message ?: "Failed to end call"))
            }
        }
    }

    fun navigateToMakeOrder(storeId: String) {
        viewModelScope.launch {
            val storeName = _state.value.todayStores.firstOrNull { it.storeId == storeId }?.storeName
                ?: _state.value.todaySpontaneousItems.firstOrNull { it.storeId == storeId }?.storeName
            _events.emit(UiEvent.NavigateToCreateOrder(storeId = storeId, storeName = storeName))
        }
    }

    private suspend fun buildTodayStores(
        cycle: CallCyclesRepository.DisplayCycle?,
        checkedIds: Set<String>,
    ): List<TodayStore> {
        val sourceDays = when (cycle?.source) {
            "WEEK" -> cycle.week?.days
            "TEMPLATE" -> cycle.template?.days
            else -> null
        }.orEmpty()

        val todayDow = LocalDate.now().dayOfWeek.value // 1=Mon..7=Sun
        val storeIds = sourceDays.firstOrNull { it.dayOfWeek == todayDow }?.storeIds.orEmpty()
        if (storeIds.isEmpty()) return emptyList()

        // Preload store details concurrently (and cache them), then build the rows.
        val storesById = coroutineScope {
            storeIds.distinct().map { id ->
                async {
                    val cached = storeCache[id]
                    if (cached != null) return@async id to cached
                    val store = runCatching { storesRepo.getStoreById(id) }.getOrNull()
                    if (store != null) storeCache[id] = store
                    id to store
                }
            }.awaitAll().toMap()
        }

        // Active call checks can be slower; we do them concurrently too.
        val activeById = coroutineScope {
            storeIds.distinct().map { id ->
                async {
                    id to (runCatching { completedCallsRepo.isCallActiveForStore(id) }.getOrNull() ?: false)
                }
            }.awaitAll().toMap()
        }

        return storeIds.map { id ->
            val store = storesById[id]
            TodayStore(
                storeId = id,
                storeName = store?.StoreName?.takeIf { it.isNotBlank() } ?: id,
                storeAddress = store?.StoreAddress?.takeIf { it.isNotBlank() },
                checked = checkedIds.contains(id),
                isCallActive = activeById[id] ?: false,
            )
        }
    }

    private suspend fun buildTodaySpontaneousItems(
        calls: List<CallCyclesRepository.SpontaneousCall>,
        checkedCallIds: Set<String>,
    ): List<TodaySpontaneousItem> {
        if (calls.isEmpty()) return emptyList()

        val ordered = calls.sortedByDescending { it.createdAt ?: 0L }

        // Preload store details concurrently for all storeIds in the list.
        val storeIds = ordered.mapNotNull { it.storeId }.distinct()
        val storesById = coroutineScope {
            storeIds.map { id ->
                async {
                    val cached = storeCache[id]
                    if (cached != null) return@async id to cached
                    val store = runCatching { storesRepo.getStoreById(id) }.getOrNull()
                    if (store != null) storeCache[id] = store
                    id to store
                }
            }.awaitAll().toMap()
        }

        return ordered.mapNotNull { c ->
            val callId = c.callId ?: return@mapNotNull null
            val storeId = c.storeId ?: return@mapNotNull null
            val store = storesById[storeId]

            TodaySpontaneousItem(
                callId = callId,
                storeId = storeId,
                storeName = store?.StoreName?.takeIf { it.isNotBlank() } ?: storeId,
                storeAddress = store?.StoreAddress?.takeIf { it.isNotBlank() },
                checked = checkedCallIds.contains(callId),
                createdAt = c.createdAt,
            )
        }
    }

    private fun buildTodayTitle(date: LocalDate): String {
        val fmt = DateTimeFormatter.ofPattern("EEEE MMMM d yyyy", Locale.ENGLISH)
        return date.format(fmt)
    }

    private fun currentWeekKey(date: LocalDate): String {
        // Matches your backend keys like 2026-W02.
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return String.format(Locale.US, "%d-W%02d", year, week)
    }

    class Factory(
        private val repo: CallCyclesRepository = CallCyclesRepository(),
        private val storesRepo: StoresRepository = StoresRepository(),
        private val completedCallsRepo: CompletedCallsRepository = CompletedCallsRepository(),
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallCycleViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CallCycleViewModel(repo, storesRepo, completedCallsRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
