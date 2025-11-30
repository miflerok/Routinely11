package ru.routinely.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import ru.routinely.app.data.HabitRepository
import ru.routinely.app.model.Habit
import ru.routinely.app.viewmodel.HabitViewModel
import ru.routinely.app.viewmodel.NotificationEvent
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val habitDao = FakeHabitDao()
    private val repository = HabitRepository(habitDao)
    private val userPreferencesRepository = TestUserPreferencesRepository()

    private fun createViewModel() = HabitViewModel(repository, userPreferencesRepository)

    @Test
    fun saveHabit_emitsScheduleEventWhenNotificationTimeIsPresent() = runTest {
        val viewModel = createViewModel()
        val events = mutableListOf<NotificationEvent>()
        val owner = TestLifecycleOwner()
        viewModel.notificationEvent.observe(owner) { events.add(it) }

        viewModel.saveHabit(
            Habit(
                name = "Drink Water",
                type = "daily",
                notificationTime = "08:00"
            )
        )

        advanceUntilIdle()

        val event = events.single() as NotificationEvent.Schedule
        assertEquals("Drink Water", event.habit.name)
        assertNotNull(event.habit.id)
    }

    @Test
    fun saveHabit_emitsCancelEventWhenNotificationAbsent() = runTest {
        val viewModel = createViewModel()
        val events = mutableListOf<NotificationEvent>()
        val owner = TestLifecycleOwner()
        viewModel.notificationEvent.observe(owner) { events.add(it) }

        viewModel.saveHabit(
            Habit(
                name = "Read",
                type = "daily",
                notificationTime = null
            )
        )

        advanceUntilIdle()

        val event = events.single() as NotificationEvent.Cancel
        assertEquals(1, event.habitId)
    }

    @Test
    fun onHabitCheckedChanged_updatesProgressStreakAndHistory() = runTest {
        val existing = Habit(id = 5, name = "Run", type = "daily", targetValue = 1, currentValue = 0)
        habitDao.insertHabit(existing)
        val viewModel = createViewModel()

        viewModel.onHabitCheckedChanged(existing, isCompleted = true)
        advanceUntilIdle()

        val updated = habitDao.latestHabits().first { it.id == existing.id }
        assertEquals(1, updated.currentValue)
        assertEquals(1, updated.currentStreak)
        assertEquals(1, updated.bestStreak)
        assertNotNull(updated.lastCompletedDate)

        val completions = repository.allCompletions.first()
        assertEquals(1, completions.size)
        assertEquals(existing.id, completions.first().habitId)
    }

    @Test
    fun clearAllData_removesHabitsAndCompletions() = runTest {
        val habit = Habit(id = 7, name = "Stretch", type = "daily", targetValue = 1, currentValue = 1)
        habitDao.insertHabit(habit)
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = habit.id,
                completionDay = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis()
            )
        )
        val viewModel = createViewModel()

        viewModel.clearAllData()
        advanceUntilIdle()

        assertEquals(0, habitDao.latestHabits().size)
        assertEquals(0, habitDao.latestCompletions().size)
    }

    @Test
    fun statsUiState_calculatesWeeklyAndMonthlyPercentages() = runTest {
        val today = LocalDate.now()
        val creationDate = today.minusDays(1)

        val habitA = Habit(
            id = 1,
            name = "Meditate",
            type = "daily",
            creationDate = startOfDayMillis(creationDate),
            bestStreak = 2
        )
        val habitB = Habit(
            id = 2,
            name = "Walk",
            type = "daily",
            creationDate = startOfDayMillis(creationDate)
        )

        habitDao.insertHabit(habitA)
        habitDao.insertHabit(habitB)

        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = habitA.id,
                completionDay = startOfDayMillis(today.minusDays(1)),
                completedAt = System.currentTimeMillis()
            )
        )
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = habitB.id,
                completionDay = startOfDayMillis(today.minusDays(1)),
                completedAt = System.currentTimeMillis()
            )
        )
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = habitA.id,
                completionDay = startOfDayMillis(today),
                completedAt = System.currentTimeMillis()
            )
        )

        val viewModel = createViewModel()

        advanceUntilIdle()
        val state = viewModel.statsUiState.first { !it.isLoading }

        assertEquals(2, state.totalHabitsCount)
        assertEquals(2, state.bestStreakOverall)
        assertEquals(75, state.weeklyCompletionPercentage)
        assertEquals(75, state.monthlyCompletionPercentage)

        val recentTrend = state.weeklyTrend.takeLast(2)
        assertEquals(1f, recentTrend[0].completionRatio, 0.001f)
        assertEquals(0.5f, recentTrend[1].completionRatio, 0.001f)
    }

    @Test
    fun onStatsDateSelected_updatesCalendarSelectionAndHabits() = runTest {
        val today = LocalDate.now()
        val creationDate = today.minusDays(2)
        val targetDate = today.minusDays(1)

        val reading = Habit(
            id = 10,
            name = "Read",
            type = "daily",
            creationDate = startOfDayMillis(creationDate)
        )
        val yoga = Habit(
            id = 11,
            name = "Yoga",
            type = "daily",
            creationDate = startOfDayMillis(creationDate)
        )

        habitDao.insertHabit(reading)
        habitDao.insertHabit(yoga)

        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = reading.id,
                completionDay = startOfDayMillis(targetDate),
                completedAt = System.currentTimeMillis()
            )
        )
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = yoga.id,
                completionDay = startOfDayMillis(today),
                completedAt = System.currentTimeMillis()
            )
        )

        val viewModel = createViewModel()

        viewModel.onStatsDateSelected(targetDate)
        advanceUntilIdle()

        val state = viewModel.statsUiState.first { !it.isLoading && it.selectedDate == targetDate }

        assertEquals(listOf(reading), state.selectedDateHabits)
        val selectedDay = state.calendarDays.first { it.date == targetDate }
        assertEquals(true, selectedDay.isSelected)
        assertEquals(true, selectedDay.isCompleted)
    }

    private fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }
}
