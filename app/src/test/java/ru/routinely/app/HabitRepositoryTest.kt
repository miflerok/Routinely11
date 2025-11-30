package ru.routinely.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.routinely.app.data.HabitRepository
import ru.routinely.app.model.Habit

class HabitRepositoryTest {

    private lateinit var habitDao: FakeHabitDao
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        habitDao = FakeHabitDao()
        repository = HabitRepository(habitDao)
    }

    /**
     * При запросе списка на сегодня должны возвращаться ежедневные привычки и те, что назначены на
     * текущий день недели.
     */
    @Test
    fun `habitsForToday returns daily and scheduled habits only`() = runTest {
        val todayIndex = getTodayIndex()

        val daily = Habit(id = 1, name = "Daily", type = "daily")
        val scheduledToday = Habit(id = 2, name = "Scheduled", type = todayIndex.toString())
        val otherDay = Habit(id = 3, name = "Other", type = ((todayIndex % 7) + 1).toString())

        habitDao.insertHabit(daily)
        habitDao.insertHabit(scheduledToday)
        habitDao.insertHabit(otherDay)

        val result = repository.habitsForToday.first()

        assertEquals(listOf(daily, scheduledToday).toSet(), result.toSet())
    }

    /**
     * Сортировка по имени по возрастанию должна упорядочить привычки в алфавитном порядке от А до Я.
     */
    @Test
    fun `getAllHabitsSortedByNameASC sorts alphabetically`() = runTest {
        val habitA = Habit(id = 1, name = "Alpha", type = "daily")
        val habitB = Habit(id = 2, name = "Beta", type = "daily")
        val habitC = Habit(id = 3, name = "Gamma", type = "daily")

        habitDao.insertHabit(habitC)
        habitDao.insertHabit(habitA)
        habitDao.insertHabit(habitB)

        val result = repository.getAllHabitsSortedByNameASC().first()

        assertEquals(listOf(habitA, habitB, habitC), result)
    }

    /**
     * Сортировка по имени по убыванию должна располагать привычки в обратном алфавитном порядке.
     */
    @Test
    fun `getAllHabitsSortedByNameDESC sorts reverse alphabetically`() = runTest {
        val habitA = Habit(id = 1, name = "Alpha", type = "daily")
        val habitB = Habit(id = 2, name = "Beta", type = "daily")
        val habitC = Habit(id = 3, name = "Gamma", type = "daily")

        habitDao.insertHabit(habitA)
        habitDao.insertHabit(habitB)
        habitDao.insertHabit(habitC)

        val result = repository.getAllHabitsSortedByNameDESC().first()

        assertEquals(listOf(habitC, habitB, habitA), result)
    }

    /**
     * Сортировка по серии должна ставить привычку с самой длинной текущей серией первой.
     */
    @Test
    fun `getAllHabitsSortedByStreak orders by longest streak first`() = runTest {
        val slow = Habit(id = 1, name = "Slow", type = "daily", currentStreak = 1)
        val medium = Habit(id = 2, name = "Medium", type = "daily", currentStreak = 3)
        val fast = Habit(id = 3, name = "Fast", type = "daily", currentStreak = 5)

        habitDao.insertHabit(medium)
        habitDao.insertHabit(slow)
        habitDao.insertHabit(fast)

        val result = repository.getAllHabitsSortedByStreak().first()

        assertEquals(listOf(fast, medium, slow), result)
    }

    /**
     * Очистка всех привычек должна одновременно удалять все выполнения для согласованности данных.
     */
    @Test
    fun `clearAllHabits removes habits and completions together`() = runTest {
        val habit = Habit(id = 10, name = "Track", type = "daily")
        habitDao.insertHabit(habit)
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(
                habitId = habit.id,
                completionDay = 1L,
                completedAt = 2L
            )
        )

        repository.clearAllHabits()

        assertTrue(repository.allHabits.first().isEmpty())
        assertTrue(repository.allCompletions.first().isEmpty())
    }

    /**
     * Удаление выполнения за конкретный день не должно затрагивать остальные записи о выполнениях.
     */
    @Test
    fun `removeCompletionForDay deletes only matching record`() = runTest {
        val habit = Habit(id = 15, name = "Hydrate", type = "daily")
        habitDao.insertHabit(habit)
        val today = 100L
        val yesterday = 50L
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(habitId = habit.id, completionDay = today, completedAt = 101L)
        )
        repository.addCompletion(
            ru.routinely.app.model.HabitCompletion(habitId = habit.id, completionDay = yesterday, completedAt = 51L)
        )

        repository.removeCompletionForDay(habit.id, today)

        val remaining = repository.getCompletionsForHabit(habit.id)
        assertEquals(1, remaining.size)
        assertEquals(yesterday, remaining.first().completionDay)
    }

    private fun getTodayIndex(): Int {
        val calendarDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        return if (calendarDay == java.util.Calendar.SUNDAY) 7 else calendarDay - 1
    }
}
