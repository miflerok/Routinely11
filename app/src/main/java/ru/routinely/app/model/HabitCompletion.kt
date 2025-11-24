package ru.routinely.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Отдельная сущность для хранения истории выполнения привычек.
 * Один ряд соответствует одному завершению привычки в конкретный день и время.
 * `completionDay` хранит метку начала суток (00:00:00) для упрощения выборок по дню,
 * а `completedAt` содержит точное время выполнения.
 */
@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["habit_id", "completion_day"], unique = true)]
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "habit_id")
    val habitId: Int,

    @ColumnInfo(name = "completion_day")
    val completionDay: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long
)
