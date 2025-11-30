package ru.routinely.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test
    fun habitsForToday_includesDailyAndScheduledHabits() = runTest {
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

    private fun getTodayIndex(): Int {
        val calendarDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        return if (calendarDay == java.util.Calendar.SUNDAY) 7 else calendarDay - 1
    }
}
