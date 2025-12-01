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
import ru.routinely.app.data.UserPreferences
import ru.routinely.app.data.UserPreferencesRepository
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion
import ru.routinely.app.utils.HabitFilter
import ru.routinely.app.utils.SortOrder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

// --- Вспомогательные классы и события ---

/**
 * Класс для событий, которые должны быть обработаны только один раз (LiveEvent).
 * Используется для навигации или показа уведомлений/Snackbar.
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
 * События, связанные с уведомлениями, отправляемые в UI (MainActivity).
 */
sealed class NotificationEvent {
    data class Schedule(val habit: Habit) : NotificationEvent()
    data class Cancel(val habitId: Int) : NotificationEvent()
}

/**
 * Состояние главного экрана (список привычек).
 */
data class HabitUiState(
    val habits: List<Habit> = emptyList(),
    val categories: List<String> = emptyList(),
    val sortOrder: SortOrder = SortOrder.BY_DATE,
    val habitFilter: HabitFilter = HabitFilter.TODAY,
    val categoryFilter: String? = null,
    val isNameSortAsc: Boolean = true
)

/**
 * Состояние дня в календаре статистики.
 */
data class CalendarDayState(
    val date: LocalDate,
    val isCompleted: Boolean,
    val isSelected: Boolean
)

/**
 * Модель для графика выполнения.
 */
data class DayCompletion(
    val date: LocalDate,
    val completionRatio: Float
)

/**
 * Полное состояние экрана статистики.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val totalHabitsCount: Int = 0,
    val bestStreakOverall: Int = 0,
    val weeklyCompletionPercentage: Int = 0,
    val rollingWeeklyCompletionPercentage: Int = 0,
    val monthlyCompletionPercentage: Int = 0,
    val calendarDays: List<CalendarDayState> = emptyList(),
    val weekRangeLabel: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateHabits: List<Habit> = emptyList(),
    val weeklyTrend: List<DayCompletion> = emptyList()
)

// --- ViewModel ---

/**
 * ViewModel для управления данными привычек и бизнес-логикой приложения.
 */
class HabitViewModel(
    private val repository: HabitRepository,
    val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Состояния фильтрации и сортировки
    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE)
    private val _habitFilter = MutableStateFlow(HabitFilter.TODAY)
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _isNameSortAsc = MutableStateFlow(true)

    // Выбранная дата на экране статистики
    private val _selectedStatsDate = MutableStateFlow(LocalDate.now())

    // Внутренний класс для группировки настроек отображения
    private data class UserOptions(
        val sortOrder: SortOrder,
        val habitFilter: HabitFilter,
        val categoryFilter: String?,
        val isNameSortAsc: Boolean
    )

    private val userOptionsFlow = combine(
        _sortOrder, _habitFilter, _categoryFilter, _isNameSortAsc
    ) { sort, filter, category, isAsc ->
        UserOptions(sort, filter, category, isAsc)
    }

    // --- 1. Основной поток состояния UI (Список привычек) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HabitUiState> = userOptionsFlow.flatMapLatest { options ->
        // Выбираем поток данных в зависимости от сортировки
        val sortedHabitsFlow = when (options.sortOrder) {
            SortOrder.BY_DATE -> repository.allHabits
            SortOrder.BY_STREAK -> repository.getAllHabitsSortedByStreak()
            SortOrder.BY_NAME -> if (options.isNameSortAsc)
                repository.getAllHabitsSortedByNameASC() else repository.getAllHabitsSortedByNameDESC()
        }

        // Комбинируем список привычек с категориями
        combine(
            sortedHabitsFlow,
            repository.allHabits.map { list -> list.mapNotNull { it.category }.distinct().sorted() }
        ) { habits, allCategories ->

            // Применяем фильтры в памяти
            val filteredHabits = when (options.habitFilter) {
                HabitFilter.TODAY -> filterForToday(habits)
                HabitFilter.ALL -> habits
                HabitFilter.UNCOMPLETED -> habits.filter { !isCompletedToday(it) }
            }.let { list ->
                // Дополнительный фильтр по категории
                if (options.categoryFilter != null) {
                    list.filter { it.category == options.categoryFilter }
                } else list
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
        started = SharingStarted.Eagerly,
        initialValue = HabitUiState()
    )

    // Поток всех выполнений (используется для истории)
    val completions: StateFlow<List<HabitCompletion>> = repository.allCompletions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Поток настроек пользователя (тема, уведомления)
    val userPreferences = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserPreferences(isDarkTheme = false, notificationsEnabled = true)
        )

    // --- 2. Состояние экрана статистики ---
    val statsUiState: StateFlow<StatsUiState> = combine(
        repository.totalHabitsCount,
        repository.bestStreakOverall,
        repository.allCompletions,
        repository.allHabits,
        _selectedStatsDate
    ) { totalCount, bestStreak, allCompletions, habits, selectedDate ->

        val now = LocalDate.now()

        // Расчет процентов за неделю (понедельник–воскресенье текущей недели)
        val startOfCalendarWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfCalendarWeek = minOf(now, startOfCalendarWeek.plusDays(6))
        val weeklyPercentage = calculateAccuratePercentage(
            habits = habits,
            completions = allCompletions,
            startDate = startOfCalendarWeek,
            endDate = endOfCalendarWeek
        )

        // Расчет процентов за последние 7 дней (сквозная неделя)
        val rollingWeeklyPercentage = calculateAccuratePercentage(
            habits = habits,
            completions = allCompletions,
            startDate = now.minusDays(6),
            endDate = now
        )

        // Расчет процентов за текущий месяц
        val currentMonthStart = now.withDayOfMonth(1)
        val monthlyPercentage = calculateAccuratePercentage(
            habits = habits,
            completions = allCompletions,
            startDate = currentMonthStart,
            endDate = now
        )

        // Формирование данных для календаря
        val completionDatesSet = allCompletions.map {
            Instant.ofEpochMilli(it.completionDay)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSet()

        val calendarDays = buildWeekCalendar(completionDatesSet, selectedDate)
        val weekRangeLabel = formatWeekRange(selectedDate)

        // Список выполненных привычек для выбранной даты
        val habitsCompletedOnDate = allCompletions
            .filter {
                Instant.ofEpochMilli(it.completionDay)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate() == selectedDate
            }
            .mapNotNull { completion -> habits.find { it.id == completion.habitId } }

        // График тренда
        val weeklyTrend = buildAccurateWeeklyTrend(habits, allCompletions, now)

        StatsUiState(
            isLoading = false,
            totalHabitsCount = totalCount,
            bestStreakOverall = bestStreak ?: 0,
            weeklyCompletionPercentage = weeklyPercentage,
            rollingWeeklyCompletionPercentage = rollingWeeklyPercentage,
            monthlyCompletionPercentage = monthlyPercentage,
            calendarDays = calendarDays,
            weekRangeLabel = weekRangeLabel,
            selectedDate = selectedDate,
            selectedDateHabits = habitsCompletedOnDate,
            weeklyTrend = weeklyTrend
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StatsUiState()
    )

    // События уведомлений
    private val _notificationEvent = SingleLiveEvent<NotificationEvent>()
    val notificationEvent: LiveData<NotificationEvent> = _notificationEvent

    // --- Методы управления UI (Actions) ---

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
        if (category != null) _habitFilter.value = HabitFilter.ALL
    }

    fun onStatsDateSelected(date: LocalDate) {
        _selectedStatsDate.value = date
    }

    /**
     * Сохраняет новую или обновляет существующую привычку.
     * Также планирует или отменяет уведомление.
     */
    fun saveHabit(habit: Habit) = viewModelScope.launch {
        // 1. Вставляем в БД. DAO должен возвращать Long (новый ID)
        val returnedId = repository.insert(habit)

        // 2. Если ID привычки был 0, используем полученный ID
        val realId = if (habit.id == 0) returnedId.toInt() else habit.id
        val savedHabit = habit.copy(id = realId)

        // 3. Управление уведомлением
        if (savedHabit.notificationTime != null) {
            _notificationEvent.value = NotificationEvent.Schedule(savedHabit)
        } else {
            _notificationEvent.value = NotificationEvent.Cancel(savedHabit.id)
        }
    }

    fun deleteHabit(habit: Habit) = viewModelScope.launch {
        repository.delete(habit)
        _notificationEvent.value = NotificationEvent.Cancel(habit.id)
    }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAllHabits()
    }

    /**
     * Обновляет прогресс привычки (например, со слайдера).
     */
    fun updateHabitProgress(habit: Habit, newValue: Int) = viewModelScope.launch {
        val isCompleted = newValue >= habit.targetValue
        val updatedHabitValue = habit.copy(currentValue = newValue)
        val finalHabit = calculateNewStreakState(updatedHabitValue, isCompleted)

        repository.update(finalHabit)
        handleCompletionHistory(finalHabit, isCompleted)
    }

    /**
     * Обрабатывает клик по чекбоксу или свайп (быстрое выполнение).
     */
    fun onHabitCheckedChanged(habit: Habit, isCompleted: Boolean) = viewModelScope.launch {
        val newValue = if (isCompleted) {
            (habit.currentValue + 1).coerceAtMost(habit.targetValue)
        } else {
            (habit.currentValue - 1).coerceAtLeast(0)
        }

        val actuallyCompleted = newValue >= habit.targetValue
        val habitWithNewValue = habit.copy(currentValue = newValue)
        val finalHabit = calculateNewStreakState(habitWithNewValue, actuallyCompleted)

        repository.update(finalHabit)
        handleCompletionHistory(finalHabit, actuallyCompleted)
    }

    // Добавляет или удаляет запись в таблице истории выполнений
    private suspend fun handleCompletionHistory(habit: Habit, isCompleted: Boolean) {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (isCompleted) {
            val completion = HabitCompletion(
                habitId = habit.id,
                completionDay = todayStart,
                completedAt = System.currentTimeMillis()
            )
            repository.addCompletion(completion)
        } else {
            repository.removeCompletionForDay(habit.id, todayStart)
        }
    }

    fun toggleTheme(isDark: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setDarkTheme(isDark)
    }

    fun toggleNotifications(isEnabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setNotificationsEnabled(isEnabled)
    }

    // --- Логика расчета статистики ---

    /**
     * Считает точный процент выполнения: (Реально выполнено / Запланировано) * 100.
     * Учитывает дату создания привычки и расписание (type).
     */
    private fun calculateAccuratePercentage(
        habits: List<Habit>,
        completions: List<HabitCompletion>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        if (habits.isEmpty() || startDate > endDate) return 0

        var totalExpected = 0
        var totalActual = 0
        val daysCount = ChronoUnit.DAYS.between(startDate, endDate) + 1

        for (i in 0 until daysCount) {
            val date = startDate.plusDays(i)

            // 1. Какие привычки должны были выполняться в этот день?
            val scheduledHabits = habits.filter { habit ->
                val createdDate = Instant.ofEpochMilli(habit.creationDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate()

                // Проверяем: привычка создана НЕ позже текущей даты проверки И день подходит по расписанию
                (date.isAfter(createdDate) || date.isEqual(createdDate)) && isHabitScheduledForDate(habit, date)
            }

            totalExpected += scheduledHabits.size

            // 2. Какие из запланированных были реально выполнены?
            val actualForDay = completions.count { completion ->
                val completionDate = Instant.ofEpochMilli(completion.completionDay)
                    .atZone(ZoneId.systemDefault()).toLocalDate()

                completionDate.isEqual(date) && scheduledHabits.any { it.id == completion.habitId }
            }

            totalActual += actualForDay
        }

        if (totalExpected == 0) return 0
        return ((totalActual.toFloat() / totalExpected.toFloat()) * 100).roundToInt()
    }

    /**
     * Строит данные для недельного графика тренда.
     */
    private fun buildAccurateWeeklyTrend(
        habits: List<Habit>,
        completions: List<HabitCompletion>,
        referenceDate: LocalDate
    ): List<DayCompletion> {
        val startDate = referenceDate.minusDays(6)

        return (0 until 7).map { offset ->
            val date = startDate.plusDays(offset.toLong())

            val scheduledHabits = habits.filter { habit ->
                val createdDate = Instant.ofEpochMilli(habit.creationDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                (date.isAfter(createdDate) || date.isEqual(createdDate)) && isHabitScheduledForDate(habit, date)
            }

            val expected = scheduledHabits.size
            val actual = completions.count { completion ->
                val completionDate = Instant.ofEpochMilli(completion.completionDay)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                completionDate.isEqual(date) && scheduledHabits.any { it.id == completion.habitId }
            }

            val ratio = if (expected == 0) 0f else actual.toFloat() / expected.toFloat()
            DayCompletion(date = date, completionRatio = ratio.coerceIn(0f, 1f))
        }
    }

    /**
     * Проверяет, стоит ли привычка в расписании на указанную дату.
     */
    private fun isHabitScheduledForDate(habit: Habit, date: LocalDate): Boolean {
        if (habit.type == "daily") return true

        // В java.time: Monday=1, Sunday=7.
        val dayOfWeek = date.dayOfWeek.value

        return try {
            val scheduledDays = habit.type.split(",").mapNotNull { it.trim().toIntOrNull() }
            dayOfWeek in scheduledDays
        } catch (e: Exception) {
            false
        }
    }

    // --- Вспомогательные методы бизнес-логики ---

    private fun filterForToday(habits: List<Habit>): List<Habit> {
        val todayCalendar = Calendar.getInstance()
        val todayIndex = getRoutinelyDayOfWeek(todayCalendar.get(Calendar.DAY_OF_WEEK))

        return habits.filter { habit ->
            when {
                habit.type == "daily" -> true
                habit.type.isNotEmpty() && habit.type != "daily" -> {
                    val selectedDays = habit.type.split(',').mapNotNull { it.trim().toIntOrNull() }
                    todayIndex in selectedDays
                }
                else -> false
            }
        }
    }

    private fun getRoutinelyDayOfWeek(calendarDay: Int): Int {
        // Конвертация: Calendar.SUNDAY(1) -> 7, Monday(2) -> 1
        return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    }

    private fun isCompletedToday(habit: Habit): Boolean {
        return habit.currentValue >= habit.targetValue
    }

    private fun calculateNewStreakState(habit: Habit, isCompleted: Boolean): Habit {
        var newCurrentStreak = habit.currentStreak
        var newBestStreak = habit.bestStreak

        if (isCompleted && habit.currentValue >= habit.targetValue) {
            if (!isCompletedToday(habit, LocalDate.now())) {
                newCurrentStreak += 1
                newBestStreak = max(newCurrentStreak, newBestStreak)
            }
        } else if (!isCompleted && habit.currentStreak > 0) {
            newCurrentStreak = (newCurrentStreak - 1).coerceAtLeast(0)
        }

        val lastDate = if (isCompleted) System.currentTimeMillis() else null

        return habit.copy(
            currentStreak = newCurrentStreak,
            bestStreak = newBestStreak,
            lastCompletedDate = lastDate
        )
    }

    private fun isCompletedToday(habit: Habit, today: LocalDate): Boolean {
        val lastDate = habit.lastCompletedDate ?: return false
        val lastLocalDate = Instant.ofEpochMilli(lastDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return lastLocalDate.isEqual(today)
    }

    // --- Методы календаря ---

    private fun buildWeekCalendar(completionDates: Set<LocalDate>, selectedDate: LocalDate): List<CalendarDayState> {
        val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until 7).map { offset ->
            val day = startOfWeek.plusDays(offset.toLong())
            CalendarDayState(
                date = day,
                isCompleted = day in completionDates,
                isSelected = day.isEqual(selectedDate)
            )
        }
    }

    private fun formatWeekRange(selectedDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM")
        val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return "${startOfWeek.format(formatter)} – ${endOfWeek.format(formatter)}"
    }
}