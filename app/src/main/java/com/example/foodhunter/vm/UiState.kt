package com.example.foodhunter.vm

import com.example.foodhunter.model.Dish
import com.example.foodhunter.model.DishDetails

const val DEFAULT_EMPTY_SEARCH_MESSAGE = "Введите название блюда,\nчтобы начать поиск"

sealed interface SearchState {
    data class Empty(val msg: String = DEFAULT_EMPTY_SEARCH_MESSAGE) : SearchState
    data object Loading : SearchState
    data class Failure(val msg: String) : SearchState
    data class Found(val dishes: List<Dish>) : SearchState
}

sealed interface DetailState {
    data object Idle : DetailState
    data object Loading : DetailState
    data class Failure(val msg: String) : DetailState
    data class Ready(val dish: DishDetails) : DetailState
}

enum class SortOrder(val label: String) {
    DEFAULT("По умолчанию"),
    NAME_ASC("А → Я"),
    NAME_DESC("Я → А")
}

data class HomeScreenState(
    val searchState: SearchState = SearchState.Empty(),
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val viewedDishIds: Set<String> = emptySet()
)
