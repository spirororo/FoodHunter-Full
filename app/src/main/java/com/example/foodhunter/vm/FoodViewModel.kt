package com.example.foodhunter.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodhunter.db.HistoryStorage
import com.example.foodhunter.model.Dish
import com.example.foodhunter.model.DishDetails
import com.example.foodhunter.repo.DishRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val dishRepo: DishRepo,
    private val historyStorage: HistoryStorage
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DEFAULT)
    private val _manualSearchVersion = MutableStateFlow(0L)

    private val _searchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _detailState = MutableStateFlow<DetailState>(DetailState.Idle)
    val detailState: StateFlow<DetailState> = _detailState.asStateFlow()

    val history: StateFlow<List<Dish>> = historyStorage.watchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private sealed interface SearchIntent {
        val query: String
        data class Auto(override val query: String) : SearchIntent
        data class Manual(override val query: String) : SearchIntent
    }

    private data class SearchDraft(
        val query: String,
        val manualSearchVersion: Long
    )

    private val rawSearchState: Flow<SearchState> = merge(
        _query
            .map { SearchDraft(it.trim(), _manualSearchVersion.value) }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .filter { draft -> draft.manualSearchVersion == _manualSearchVersion.value }
            .map { draft -> SearchIntent.Auto(draft.query) },
        _searchTrigger
            .map { SearchIntent.Manual(_query.value.trim()) }
    ).flatMapLatest { intent ->
        buildSearchFlow(intent)
    }

    val homeScreenState: StateFlow<HomeScreenState> = combine(
        rawSearchState.onStart { emit(SearchState.Empty()) },
        _sortOrder,
        history
    ) { search, order, viewedDishes ->
        HomeScreenState(
            searchState = applySorting(search, order),
            sortOrder = order,
            viewedDishIds = viewedDishes.map { it.id }.toSet()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeScreenState())

    private var currentDishId: String? = null
    private var detailJob: Job? = null
    private var detailRequestVersion = 0L

    fun changeQuery(text: String) {
        _query.value = text
    }

    fun changeSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun doSearch() {
        _manualSearchVersion.value += 1
        _searchTrigger.tryEmit(Unit)
    }

    fun openDishById(id: String) {
        currentDishId = id
        loadDish(id)
    }

    fun retryOpenDish() {
        currentDishId?.let(::loadDish)
    }

    fun showInvalidDishRequest() {
        currentDishId = null
        detailJob?.cancel()
        _detailState.value = DetailState.Failure("Не удалось открыть карточку блюда")
    }

    fun removeFromHistory(id: String) {
        viewModelScope.launch {
            historyStorage.deleteOne(id)
        }
    }

    fun wipeHistory() {
        viewModelScope.launch {
            historyStorage.clearAll()
        }
    }

    private fun buildSearchFlow(intent: SearchIntent): Flow<SearchState> = flow {
        if (intent.query.isBlank()) {
            emit(SearchState.Empty(if (intent is SearchIntent.Manual) "Введите название блюда" else DEFAULT_EMPTY_SEARCH_MESSAGE))
            return@flow
        }
        emit(SearchState.Loading)
        val result = try {
            val dishes = dishRepo.search(intent.query)
            if (dishes.isEmpty()) SearchState.Empty("По запросу \"${intent.query}\" ничего не найдено")
            else SearchState.Found(dishes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Failure("Не удалось выполнить поиск. Проверьте подключение и повторите попытку.")
        }
        emit(result)
    }

    private fun applySorting(state: SearchState, order: SortOrder): SearchState {
        if (state !is SearchState.Found || order == SortOrder.DEFAULT) return state
        val sorted = when (order) {
            SortOrder.DEFAULT -> state.dishes
            SortOrder.NAME_ASC -> state.dishes.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> state.dishes.sortedByDescending { it.name.lowercase() }
        }
        return SearchState.Found(sorted)
    }

    private fun loadDish(id: String) {
        detailJob?.cancel()
        val requestVersion = ++detailRequestVersion
        _detailState.value = DetailState.Loading
        detailJob = viewModelScope.launch {
            try {
                val details = dishRepo.loadDetails(id)
                if (requestVersion != detailRequestVersion || id != currentDishId) return@launch
                if (details != null) {
                    historyStorage.recordView(details.toDish())
                    _detailState.value = DetailState.Ready(details)
                } else {
                    _detailState.value = DetailState.Failure("Не удалось найти выбранное блюдо")
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                if (requestVersion != detailRequestVersion || id != currentDishId) return@launch
                _detailState.value = DetailState.Failure("Не удалось загрузить данные блюда. Повторите попытку позже.")
            }
        }
    }

    private fun DishDetails.toDish(): Dish =
        Dish(id = id, name = name, thumb = thumb, category = category)

    companion object {
        const val DEBOUNCE_MS = 500L
    }
}
