package ru.routinely.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion
import ru.routinely.app.utils.HabitFilter
import ru.routinely.app.utils.SortOrder
import ru.routinely.app.viewmodel.HabitViewModel
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(habitViewModel: HabitViewModel) {
    // Состояние для управления видимостью Bottom Sheet
    var isSheetOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Подписываемся на состояние из ViewModel.
    val uiState by habitViewModel.uiState.collectAsState()
    val completions by habitViewModel.completions.collectAsState()

    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val completionsByHabit = completions.groupBy { it.habitId }

    // СОРТИРОВКА: Невыполненные (false) идут перед Выполненными (true)
    // Эта сортировка применяется только для визуального отображения на экране
    val habitsForDisplay = uiState.habits.sortedWith(
        compareBy { habit ->
            completionsByHabit[habit.id]?.any { it.completionDay == todayStart } == true
        }
    )

    // 1. Главный экран (HomeContent)
    HomeContent(
        habits = habitsForDisplay,
        viewModel = habitViewModel,
        completionsByHabit = completionsByHabit,
        todayStart = todayStart,
        onHabitCheckedChange = { habit, isChecked ->
            habitViewModel.onHabitCheckedChanged(habit, isChecked)
        },
        onAddHabitClick = {
            isSheetOpen = true // Открываем Bottom Sheet при нажатии '+'
        }
    )

    // 2. Bottom Sheet (Выезжающее окно)
    if (isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSheetOpen = false },
            sheetState = sheetState
        ) {
            AddHabitScreen(
                viewModel = habitViewModel,
                onNavigateBack = { isSheetOpen = false }
            )
        }
    }
}

/**
 * Stateless-компонент, отвечающий за отображение UI списка
 * привычек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    habits: List<Habit>,
    viewModel: HabitViewModel,
    completionsByHabit: Map<Int, List<HabitCompletion>>,
    todayStart: Long,
    onHabitCheckedChange: (Habit, Boolean) -> Unit,
    onAddHabitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { AppTopBar(viewModel = viewModel) },
        floatingActionButton = { AddHabitButton(onAddHabitClick) },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        // Список привычек
        LazyColumn(
            modifier = modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (habits.isEmpty()) {
                item {
                    Text(
                        text = "Привычек пока нет. Нажмите '+' для добавления.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(habits, key = { it.id }) { habit ->
                    val isCompletedTodayVisually = completionsByHabit[habit.id]?.any { it.completionDay == todayStart } == true

                    HabitItem(
                        habit = habit,
                        // ** ДОБАВЛЯЕМ isCompletedToday, используя вычисленное значение **
                        isCompletedToday = isCompletedTodayVisually,
                        onCheckedChange = { isChecked ->
                            onHabitCheckedChange(habit, isChecked)
                        },
                        onItemClick = { /* ... */ }
                    )
                }
            }
        }
    }
}

// Верхняя панель (Header)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(viewModel: HabitViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    TopAppBar(
        title = { Text("Сегодня") },
        actions = {
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Меню"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // Секция Сортировки
                Text("Сортировка", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                DropdownMenuItem(text = { Text("По дате создания") }, onClick = {
                    viewModel.setSortOrder(SortOrder.BY_DATE)
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("По названию (А-Я / Я-А)") }, onClick = {
                    viewModel.setSortOrder(SortOrder.BY_NAME)
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("По длине серии") }, onClick = {
                    viewModel.setSortOrder(SortOrder.BY_STREAK)
                    showMenu = false
                })
                Divider()

                // Секция Фильтрации
                Text("Фильтрация", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                DropdownMenuItem(text = { Text("Только на сегодня") }, onClick = {
                    viewModel.setFilter(HabitFilter.TODAY)
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("Показать все") }, onClick = {
                    viewModel.setFilter(HabitFilter.ALL)
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("Только невыполненные") }, onClick = {
                    viewModel.setFilter(HabitFilter.UNCOMPLETED)
                    showMenu = false
                })

                // Секция Категорий
                if (uiState.categories.isNotEmpty()) {
                    Divider()
                    Text("Категории", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                    DropdownMenuItem(text = { Text("Все категории") }, onClick = {
                        viewModel.setCategoryFilter(null)
                        showMenu = false
                    })
                    uiState.categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category) }, onClick = {
                            viewModel.setCategoryFilter(category)
                            showMenu = false
                        })
                    }
                }
            }
        }
    )
}

// Кнопка добавления привычки
@Composable
fun AddHabitButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFFB88EFA).copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Добавить привычку",
            tint = Color.White,
        )
    }
}