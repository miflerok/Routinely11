package ru.routinely.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.routinely.app.data.HabitRepository
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion
import ru.routinely.app.utils.HabitFilter
import ru.routinely.app.utils.SortOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

// Вспомогательные классы

/**
 * Вспомогательный класс для событий, которые должны быть
 * обработаны только один раз,
 * например, для показа Snackbar или планирования уведомлений.
 */
class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    override fun observe(owner: androidx.lifecycle.LifecycleOwner, observer: androidx.lifecycle.Observer<in T>) {
        super.observe(owner) { t ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        }
    }

    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }
}

/**
 * Запечатанный класс для представления событий, связанных с
 * уведомлениями.
 * Используется для передачи команд из ViewModel в UI-слой.
 */
sealed class NotificationEvent {
    data class Schedule(val habit: Habit) : NotificationEvent()
    data class Cancel(val habitId: Int) : NotificationEvent()
}

// Модель, представляющая полное состояние UI для главного экрана
data class HabitUiState(
    val habits: List<Habit> = emptyList(),
    val categories: List<String> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_DATE,
    val habitFilter: HabitFilter = HabitFilter.TODAY,
    val categoryFilter: String? = null,
    val isNameSortAsc: Boolean = true
)

data class CalendarDayState(
    val date: LocalDate,
    val isCompleted: Boolean,
    val isSelected: Boolean
)

data class CompletedHabit(
    val habit: Habit,
    val completedAt: Long
)

data class DayCompletion(
    val date: LocalDate,
    val completionRatio: Float
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalHabitsCount: Int = 0,
    val bestStreakOverall: Int = 0,
    val weeklyCompletionPercentage: Int = 0,
    val monthlyCompletionPercentage: Int = 0,
    val calendarDays: List<CalendarDayState> = emptyList(),
    val weekRangeLabel: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateHabits: List<CompletedHabit> = emptyList(),
    val weeklyTrend: List<DayCompletion> = emptyList()
)

// Основной класс ViewModel

/**
 * ViewModel для управления данными привычек.
 */
class HabitViewModel(private val repository: HabitRepository) : ViewModel() {

    // Состояния для UI

    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE)
    private val _habitFilter = MutableStateFlow(HabitFilter.TODAY)
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _isNameSortAsc = MutableStateFlow(true)

    // Внутренний класс для группировки всех настроек пользователя
    private data class UserOptions(
        val sortOrder: SortOrder,
        val habitFilter: HabitFilter,
        val categoryFilter: String?,
        val isNameSortAsc: Boolean
    )

    // 1. Создаем единый поток, который будет реагировать на ЛЮБОЕ изменение настроек
    private val userOptionsFlow = combine(
        _sortOrder, _habitFilter, _categoryFilter, _isNameSortAsc
    ) { sort, filter, category, isAsc ->
        UserOptions(sort, filter, category, isAsc)
    }

    private val completionsState: StateFlow<List<HabitCompletion>> = repository.allCompletions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val completions: StateFlow<List<HabitCompletion>> = completionsState

    // 2. Основной поток состояния UI
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HabitUiState> = userOptionsFlow.flatMapLatest { options ->
        val sortedHabitsFlow = when (options.sortOrder) {
            SortOrder.BY_DATE -> repository.allHabits
            SortOrder.BY_STREAK -> repository.getAllHabitsSortedByStreak()
            SortOrder.BY_NAME -> if (options.isNameSortAsc) repository.getAllHabitsSortedByNameASC() else repository.getAllHabitsSortedByNameDESC()
        }

        combine(
            sortedHabitsFlow,
            repository.allHabits.map { it.mapNotNull { habit -> habit.category }.distinct().sorted() },
            completionsState
        ) { habits, allCategories, completions ->
            val today = LocalDate.now()
            val filteredHabits = when (options.habitFilter) {
                HabitFilter.TODAY -> filterForToday(habits)
                HabitFilter.ALL -> habits
                HabitFilter.UNCOMPLETED -> habits.filter { !isCompletedOnDate(it.id, completions, today) }
            }.let { filteredList ->
                if (options.categoryFilter != null) {
                    filteredList.filter { it.category == options.categoryFilter }
                } else {
                    filteredList
                }
            }

            HabitUiState(
                habits = filteredHabits,
                categories = allCategories,
                sortOrder = options.sortOrder,
                habitFilter = options.habitFilter,
                categoryFilter = options.categoryFilter,
                isNameSortAsc = options.isNameSortAsc
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HabitUiState()
    )


    // Состояния для экрана "Статистика"
    private val _selectedStatsDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val statsUiState: StateFlow<StatsUiState> = combine(
        repository.allHabits,
        completionsState,
        _selectedStatsDate
    ) { habits, completions, selectedDate ->
        val completionHistory = buildCompletionHistory(habits, completions)
        val completionDatesSet = completionHistory.keys
        val now = LocalDate.now()
        val startOfCurrentWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeklyPercentage = calculateCompletionPercentage(
            habits = habits,
            completionHistory = completionHistory,
            startDate = startOfCurrentWeek,
            endDate = now
        )
        val currentMonthStart = now.withDayOfMonth(1)
        val monthlyPercentage = calculateCompletionPercentage(
            habits = habits,
            completionHistory = completionHistory,
            startDate = currentMonthStart,
            endDate = currentMonthStart.withDayOfMonth(YearMonth.from(now).lengthOfMonth())
        )

        val calendarDays = buildWeekCalendar(completionDatesSet, selectedDate)
        val weekRangeLabel = formatWeekRange(selectedDate)
        val selectedHabits = completions
            .filter { it.completionDay == selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .mapNotNull { completion ->
                habits.firstOrNull { it.id == completion.habitId }?.let {
                    CompletedHabit(habit = it, completedAt = completion.completedAt)
                }
            }
        val weeklyTrend = buildWeeklyTrend(habits, completionHistory, now)
        val bestCurrentStreak = habits.maxOfOrNull { it.currentStreak } ?: 0

        StatsUiState(
            isLoading = false,
            totalHabitsCount = habits.size,
            bestStreakOverall = bestCurrentStreak,
            weeklyCompletionPercentage = weeklyPercentage,
            monthlyCompletionPercentage = monthlyPercentage,
            calendarDays = calendarDays,
            weekRangeLabel = weekRangeLabel,
            selectedDate = selectedDate,
            selectedDateHabits = selectedHabits,
            weeklyTrend = weeklyTrend
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StatsUiState()
    )

    // События для UI
    private val _notificationEvent = SingleLiveEvent<NotificationEvent>()
    val notificationEvent: LiveData<NotificationEvent> = _notificationEvent


    // Методы для вызова из UI

    fun setSortOrder(sortOrder: SortOrder) {
        if (sortOrder == SortOrder.BY_NAME && _sortOrder.value == SortOrder.BY_NAME) {
            _isNameSortAsc.value = !_isNameSortAsc.value
        }
        _sortOrder.value = sortOrder
    }

    fun setFilter(filter: HabitFilter) {
        _habitFilter.value = filter
        _categoryFilter.value = null
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
        if (category != null) {
            _habitFilter.value = HabitFilter.ALL
        }
    }

    // Остальные методы ViewModel
    fun saveHabit(habit: Habit) = viewModelScope.launch {
        repository.insert(habit)
        if (habit.notificationTime != null) {
            _notificationEvent.value = NotificationEvent.Schedule(habit)
        } else {
            _notificationEvent.value = NotificationEvent.Cancel(habit.id)
        }
    }

    fun deleteHabit(habit: Habit) = viewModelScope.launch {
        repository.delete(habit)
        _notificationEvent.value = NotificationEvent.Cancel(habit.id)
    }

    fun onHabitCheckedChanged(habit: Habit, isCompleted: Boolean) = viewModelScope.launch {
        val today = LocalDate.now()
        val completionDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        if (isCompleted) {
            repository.addCompletion(
                HabitCompletion(
                    habitId = habit.id,
                    completionDay = completionDay,
                    completedAt = now
                )
            )
        } else {
            repository.removeCompletionForDay(habit.id, completionDay)
        }

        val completions = repository.getCompletionsForHabit(habit.id)
        val updatedHabit = recalculateHabitFromCompletions(habit, completions)
        repository.update(updatedHabit)
    }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAllHabits()
    }

    fun onStatsDateSelected(date: LocalDate) {
        _selectedStatsDate.value = date
    }

    // Private Business Logic
    private fun filterForToday(habits: List<Habit>): List<Habit> {
        val todayCalendarDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayIndex = getRoutinelyDayOfWeek(todayCalendarDay)
        return habits.filter { habit ->
            when {
                habit.type == "daily" -> true
                habit.type.isNotEmpty() && habit.type != "daily" -> {
                    val selectedDays = habit.type.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
                    todayIndex in selectedDays
                }
                else -> false
            }
        }
    }

    private fun getRoutinelyDayOfWeek(calendarDay: Int): Int {
        return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    }

    private fun recalculateHabitFromCompletions(habit: Habit, completions: List<HabitCompletion>): Habit {
        if (completions.isEmpty()) {
            return habit.copy(
                lastCompletedDate = null,
                currentStreak = 0,
                bestStreak = 0,
                currentValue = 0
            )
        }

        val completionsByDay = completions.groupBy { it.completionDay }
        val todayStart = startOfDayMillis(LocalDate.now())
        val completionsToday = completionsByDay[todayStart]?.size ?: 0
        val uniqueDates = completionsByDay.keys.map { it.toLocalDateFromDay() }.sortedDescending()

        val currentStreak = calculateCurrentStreak(uniqueDates)
        val bestStreak = calculateBestStreak(uniqueDates)
        val lastCompletion = completions.maxByOrNull { it.completedAt }!!

        return habit.copy(
            lastCompletedDate = lastCompletion.completedAt,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            currentValue = completionsToday.coerceAtMost(habit.targetValue)
        )
    }

    private fun calculateCurrentStreak(sortedDates: List<LocalDate>): Int {
        if (sortedDates.isEmpty()) return 0
        var streak = 1
        for (i in 1 until sortedDates.size) {
            if (sortedDates[i - 1].minusDays(1) == sortedDates[i]) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    private fun calculateBestStreak(sortedDates: List<LocalDate>): Int {
        var best = 0
        var current = 0
        var previousDate: LocalDate? = null
        sortedDates.forEach { date ->
            current = if (previousDate != null && previousDate!!.minusDays(1) == date) {
                current + 1
            } else {
                1
            }
            best = max(best, current)
            previousDate = date
        }
        return best
    }

    private fun isCompletedOnDate(habitId: Int, completions: List<HabitCompletion>, date: LocalDate): Boolean {
        val dayStart = startOfDayMillis(date)
        return completions.any { it.habitId == habitId && it.completionDay == dayStart }
    }

    private fun startOfDayMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun Long.toLocalDateFromDay(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun calculateCompletionPercentage(
        habits: List<Habit>,
        completionHistory: Map<LocalDate, List<Habit>>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        if (startDate > endDate) return 0
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        if (totalDays <= 0) return 0

        var totalScheduled = 0
        var totalCompleted = 0

        (0 until totalDays).forEach { offset ->
            val day = startDate.plusDays(offset.toLong())
            val scheduledForDay = habits.count { it.isScheduledFor(day) }
            val completedForDay = completionHistory[day]?.size ?: 0
            totalScheduled += scheduledForDay
            totalCompleted += completedForDay.coerceAtMost(scheduledForDay)
        }

        if (totalScheduled == 0) return 0

        val percentage = (totalCompleted.toFloat() / totalScheduled.toFloat()) * 100
        return percentage.roundToInt().coerceIn(0, 100)
    }

    private fun buildWeekCalendar(
        completionDates: Set<LocalDate>,
        selectedDate: LocalDate
    ): List<CalendarDayState> {
        val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until 7).map { offset ->
            val day = startOfWeek.plusDays(offset.toLong())
            CalendarDayState(
                date = day,
                isCompleted = day in completionDates,
                isSelected = day == selectedDate
            )
        }
    }

    private fun formatWeekRange(selectedDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM")
        val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return "${startOfWeek.format(formatter)}–${endOfWeek.format(formatter)}"
    }

    private fun buildWeeklyTrend(
        habits: List<Habit>,
        completionHistory: Map<LocalDate, List<Habit>>,
        referenceDate: LocalDate
    ): List<DayCompletion> {
        val startDate = referenceDate.minusDays(6)
        return (0 until 7).map { offset ->
            val day = startDate.plusDays(offset.toLong())
            val scheduledCount = habits.count { it.isScheduledFor(day) }
            val completedCount = completionHistory[day]?.size ?: 0
            val ratio = if (scheduledCount == 0) 0f else completedCount.coerceAtMost(scheduledCount).toFloat() / scheduledCount.toFloat()
            DayCompletion(date = day, completionRatio = ratio.coerceIn(0f, 1f))
        }
    }

    private fun buildCompletionHistory(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): Map<LocalDate, List<Habit>> {
        val habitsById = habits.associateBy { it.id }
        return completions.mapNotNull { completion ->
            val habit = habitsById[completion.habitId] ?: return@mapNotNull null
            completion.completionDay.toLocalDateFromDay() to habit
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
    }

    private fun Habit.isScheduledFor(date: LocalDate): Boolean {
        val typeValue = type.lowercase(Locale.getDefault())
        return when {
            typeValue == "daily" -> true
            typeValue.isNotBlank() -> {
                val selectedDays = type.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                date.dayOfWeek.value in selectedDays
            }
            else -> false
        }
    }
}
