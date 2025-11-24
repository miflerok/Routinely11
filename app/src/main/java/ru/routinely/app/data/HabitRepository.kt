package ru.routinely.app.data

import kotlinx.coroutines.flow.Flow
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion
import kotlinx.coroutines.flow.map // Обязательный импорт для фильтрации
import java.util.Calendar // Обязательный импорт для определения дня недели

/**
 * Репозиторий для управления сущностями Habit.
 * Абстрагирует ViewModel от деталей реализации источников данных (в данном случае, Room DAO).
 * Весь доступ к данным должен осуществляться через этот класс.
 */

class HabitRepository(private val habitDao: HabitDao) {

    /**
     * Предоставляет наблюдаемый Flow со списком всех привычек.
     * ViewModel подписывается на этот поток для получения обновлений в реальном времени.
     */
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()

    /**
     * Предоставляет Flow со списком привычек, которые должны быть выполнены СЕГОДНЯ.
     * Использует логику расписания (поле type) для фильтрации.
     */
    val habitsForToday: Flow<List<Habit>> = allHabits.map { habits ->

        val todayCalendarDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val todayIndex = getRoutinelyDayOfWeek(todayCalendarDay) // 1=Пн, 7=Вс

        habits.filter { habit ->
            val type = habit.type

            when {
                // 1. Ежедневная привычка
                type == "daily" -> true

                // 2. Привычка по дням недели (один или несколько дней)
                type.isNotEmpty() && type != "daily" -> {
                    val selectedDays = type
                        .split(',') // Разделяем по запятой
                        .mapNotNull {
                            it.trim().toIntOrNull()
                        } // Убираем пробелы, преобразуем в Int
                        .toSet()

                    // Проверяем, есть ли текущий день в наборе
                    todayIndex in selectedDays
                }

                // 3. Если тип пустой или неверный, не показываем
                else -> false
            }
        }
    }
    /**
     * Получает привычки отсортированные от А до Я.
     */
    fun getAllHabitsSortedByNameASC(): Flow<List<Habit>> = habitDao.getAllHabitsSortedByNameASC()

    /**
     * Получает привычки отсортированные от Я до А.
     */
    fun getAllHabitsSortedByNameDESC(): Flow<List<Habit>> = habitDao.getAllHabitsSortedByNameDESC()

    /**
     * Получает привычки отсортированные по длинне серии.
     */
    fun getAllHabitsSortedByStreak(): Flow<List<Habit>> = habitDao.getAllHabitsSortedByStreak()

    /**
     * Функция-конвертер, которая преобразует системный индекс дня недели
     * (где воскресенье = 1) во внутренний стандарт приложения (где понедельник = 1).
     * Это нужно для правильной фильтрации ежедневных задач.
     * */
    private fun getRoutinelyDayOfWeek(calendarDay: Int): Int {
        // Calendar: 1=Вс, 2=Пн, 3=Вт, 4=Ср, 5=Чт, 6=Пт, 7=Сб
        // Routinely: 1=Пн, 2=Вт, ..., 6=Сб, 7=Вс
        return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    }

    // --- Методы для модификации данных ---

    /**
     * Вставляет или обновляет привычку в источнике данных.
     * @param habit Объект привычки для сохранения.
     */
    suspend fun insert(habit: Habit) {
        habitDao.insertHabit(habit)
    }

    /**
     * Обновляет существующую привычку.
     * @param habit Объект привычки для обновления.
     */
    suspend fun update(habit: Habit) {
        habitDao.updateHabit(habit)
    }

    /**
     * Удаляет привычку из источника данных.
     * @param habit Объект привычки для удаления.
     */
    suspend fun delete(habit: Habit) {
        habitDao.deleteHabit(habit)
    }


    // --- Методы для получения статистических данных ---

    /**
     * Предоставляет Flow с общим количеством созданных привычек.
     */
    val totalHabitsCount: Flow<Int> = habitDao.getTotalHabitsCount()

    /**
     * Предоставляет Flow с лучшей серией выполнения среди всех привычек.
     */
    val bestStreakOverall: Flow<Int?> = habitDao.getBestStreakOverall()

    /**
     * Предоставляет Flow со списком дат выполнения всех привычек.
     */
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    // --- Методы для специфичных операций ---

    /**
     * Очищает все данные о привычках.
     */
    suspend fun clearAllHabits() {
        habitDao.clearAllHabits()
        habitDao.clearAllCompletions()
    }

    suspend fun addCompletion(completion: HabitCompletion) {
        habitDao.insertCompletion(completion)
    }

    suspend fun removeCompletionForDay(habitId: Int, completionDay: Long) {
        habitDao.deleteCompletionForDay(habitId, completionDay)
    }

    suspend fun getCompletionsForHabit(habitId: Int): List<HabitCompletion> {
        return habitDao.getCompletionsForHabit(habitId)
    }
}