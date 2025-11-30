package ru.routinely.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.routinely.app.data.HabitDao
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion

class FakeHabitDao : HabitDao {
    private val habits = mutableListOf<Habit>()
    private val completions = mutableListOf<HabitCompletion>()

    private val habitsFlow = MutableStateFlow<List<Habit>>(emptyList())
    private val completionsFlow = MutableStateFlow<List<HabitCompletion>>(emptyList())

    fun latestHabits(): List<Habit> = habits.toList()
    fun latestCompletions(): List<HabitCompletion> = completions.toList()

    override suspend fun insertHabit(habit: Habit): Long {
        val nextId = if (habit.id == 0) (habits.maxOfOrNull { it.id } ?: 0) + 1 else habit.id
        val stored = habit.copy(id = nextId)
        habits.removeAll { it.id == stored.id }
        habits.add(stored)
        emitHabits()
        return nextId.toLong()
    }

    override suspend fun updateHabit(habit: Habit) {
        habits.replaceAll { if (it.id == habit.id) habit else it }
        emitHabits()
    }

    override suspend fun deleteHabit(habit: Habit) {
        habits.removeIf { it.id == habit.id }
        emitHabits()
    }

    override fun getAllHabits(): Flow<List<Habit>> = habitsFlow.map { list ->
        list.sortedByDescending { it.creationDate }
    }

    override fun getHabitById(habitId: Int): Flow<Habit?> = habitsFlow.map { list ->
        list.firstOrNull { it.id == habitId }
    }

    override fun getAllHabitsSortedByNameASC(): Flow<List<Habit>> = habitsFlow.map { list ->
        list.sortedBy { it.name }
    }

    override fun getAllHabitsSortedByNameDESC(): Flow<List<Habit>> = habitsFlow.map { list ->
        list.sortedByDescending { it.name }
    }

    override fun getAllHabitsSortedByStreak(): Flow<List<Habit>> = habitsFlow.map { list ->
        list.sortedByDescending { it.currentStreak }
    }

    override suspend fun insertCompletion(completion: HabitCompletion) {
        completions.removeAll { it.habitId == completion.habitId && it.completionDay == completion.completionDay }
        val nextId = if (completion.id == 0) (completions.maxOfOrNull { it.id } ?: 0) + 1 else completion.id
        completions.add(completion.copy(id = nextId))
        emitCompletions()
    }

    override suspend fun deleteCompletionForDay(habitId: Int, completionDay: Long) {
        completions.removeIf { it.habitId == habitId && it.completionDay == completionDay }
        emitCompletions()
    }

    override fun getAllCompletions(): Flow<List<HabitCompletion>> = completionsFlow

    override suspend fun getCompletionsForHabit(habitId: Int): List<HabitCompletion> =
        completions.filter { it.habitId == habitId }

    override suspend fun updateStreak(
        habitId: Int,
        lastCompletedDate: Long?,
        currentStreak: Int,
        bestStreak: Int
    ) {
        val current = habits.firstOrNull { it.id == habitId } ?: return
        updateHabit(
            current.copy(
                lastCompletedDate = lastCompletedDate,
                currentStreak = currentStreak,
                bestStreak = bestStreak
            )
        )
    }

    override suspend fun updateCurrentValue(habitId: Int, newValue: Int) {
        val current = habits.firstOrNull { it.id == habitId } ?: return
        updateHabit(current.copy(currentValue = newValue))
    }

    override fun getBestStreakOverall(): Flow<Int?> = habitsFlow.map { list ->
        list.maxOfOrNull { it.bestStreak }
    }

    override fun getTotalHabitsCount(): Flow<Int> = habitsFlow.map { it.size }

    override suspend fun clearAllHabits() {
        habits.clear()
        emitHabits()
    }

    override suspend fun clearAllCompletions() {
        completions.clear()
        emitCompletions()
    }

    private fun emitHabits() {
        habitsFlow.value = habits.toList()
    }

    private fun emitCompletions() {
        completionsFlow.value = completions.toList()
    }
}
