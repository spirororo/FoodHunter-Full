package com.example.foodhunter.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.foodhunter.components.DishCard
import com.example.foodhunter.model.Dish
import com.example.foodhunter.vm.HomeScreenState
import com.example.foodhunter.vm.SearchState
import com.example.foodhunter.vm.SortOrder

@Composable
fun HomeScreen(
    query: String,
    homeScreenState: HomeScreenState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onDishSelected: (Dish) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите название блюда") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Найти блюдо",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (homeScreenState.searchState is SearchState.Found) {
            SortChips(
                currentOrder = homeScreenState.sortOrder,
                onOrderChange = onSortOrderChange
            )
        }

        when (val state = homeScreenState.searchState) {
            is SearchState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.msg,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is SearchState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }

            is SearchState.Failure -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.msg,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Button(
                            onClick = onSearch,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Повторить поиск")
                        }
                    }
                }
            }

            is SearchState.Found -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.dishes) { dish ->
                        DishCard(
                            dish = dish,
                            isViewed = dish.id in homeScreenState.viewedDishIds,
                            onClick = { onDishSelected(dish) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(
    currentOrder: SortOrder,
    onOrderChange: (SortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortOrder.entries.forEach { order ->
            FilterChip(
                selected = order == currentOrder,
                onClick = { onOrderChange(order) },
                label = { Text(order.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
