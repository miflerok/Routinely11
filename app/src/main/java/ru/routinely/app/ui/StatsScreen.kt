package ru.routinely.app.ui

import android.graphics.Color as AndroidColor // Алиас для избежания конфликта имен
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import ru.routinely.app.model.Habit
import ru.routinely.app.viewmodel.CalendarDayState
import ru.routinely.app.viewmodel.DayCompletion
import ru.routinely.app.viewmodel.HabitViewModel
import ru.routinely.app.viewmodel.StatsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    habitViewModel: HabitViewModel,
    onNavigateBack: () -> Unit
) {
    val statsUiState by habitViewModel.statsUiState.collectAsState()
    Scaffold(
        topBar = { StatsTopBar(onNavigateBack) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatsHeaderSection(
                state = statsUiState,
                onDateSelected = habitViewModel::onStatsDateSelected
            )
            DailyHabitCompletionList(
                selectedDate = statsUiState.selectedDate,
                habits = statsUiState.selectedDateHabits
            )
            Spacer(Modifier.height(24.dp))
            StatProgressChart(
                weeklyTrend = statsUiState.weeklyTrend,
                weeklyPercentage = statsUiState.rollingWeeklyCompletionPercentage
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = "Статистика", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
fun StatsHeaderSection(
    state: StatsUiState,
    onDateSelected: (LocalDate) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val summaryCards = remember(
        state.totalHabitsCount,
        state.bestStreakOverall,
        state.weeklyCompletionPercentage,
        state.monthlyCompletionPercentage,
        primaryColor,
        tertiaryColor,
        secondaryColor
    ) {
        listOf(
            Triple("Всего привычек", state.totalHabitsCount.toString(), primaryColor),
            Triple("Лучшая серия", state.bestStreakOverall.toString(), tertiaryColor),
            Triple("Неделя", "${state.weeklyCompletionPercentage}%", secondaryColor),
            Triple("Месяц", "${state.monthlyCompletionPercentage}%", primaryColor.copy(alpha = 0.7f))
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.weekRangeLabel,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.calendarDays.forEach { dayState ->
                    DayBadge(
                        dayState = dayState,
                        onClick = { onDateSelected(dayState.date) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summaryCards.forEach { (title, value, color) ->
                    SummaryStatCard(
                        title = title,
                        value = value,
                        accentColor = color,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DayBadge(dayState: CalendarDayState, onClick: () -> Unit) {
    val completedColor = Color(0xFFC8A2C8) // Фиолетовый из дизайна
    val backgroundColor = when {
        dayState.isSelected -> MaterialTheme.colorScheme.primary
        dayState.isCompleted -> completedColor
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val textColor = if (dayState.isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        val dayOfWeek = dayState.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            .replace('.', ' ').trim()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayOfWeek.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() },
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = dayState.date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun DailyHabitCompletionList(selectedDate: LocalDate, habits: List<Habit>) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE dd.MM", Locale("ru"))
    }
    val formattedDate = selectedDate.format(dateFormatter).replaceFirstChar { it.uppercase(Locale("ru")) }

    Text(
        text = formattedDate,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (habits.isEmpty()) {
                Text(
                    text = "Нет выполненных привычек",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                habits.forEachIndexed { index, habit ->
                    StatsHabitListItem(habit)
                    if (index < habits.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsHabitListItem(habit: Habit) {
    val isCompleted = habit.lastCompletedDate != null
    val streakValue = if (habit.currentStreak > 0) "${habit.currentStreak} дн." else "0 дн."

    val completionTime = habit.lastCompletedDate?.let {
        DateTimeFormatter.ofPattern("HH:mm").format(
            java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
        )
    } ?: "--:--"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Иконка
        Icon(
            imageVector = getIconByName(habit.icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(AndroidColor.parseColor(habit.color ?: "#B88EFA")).copy(alpha = 0.85f))
                .padding(8.dp)
        )

        Spacer(Modifier.width(12.dp))

        // 2. Название и Прогресс
        Column(modifier = Modifier.weight(1f)) {
            Text(text = habit.name, fontWeight = FontWeight.Medium)
            Text(
                text = if (habit.targetValue > 1) "${habit.currentValue}/${habit.targetValue} стр." else "1 раз",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3. Статус выполнения
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = if (isCompleted) "Выполнено" else "Не выполнено",
            tint = if (isCompleted) Color(0xFFC8A2C8) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(12.dp))

        // 4. Стрик
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Text(text = streakValue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(text = "ст.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.width(12.dp))

        // 5. Время
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = "Время выполнения",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(text = completionTime, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SummaryStatCard(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
fun StatProgressChart(weeklyTrend: List<DayCompletion>, weeklyPercentage: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Прогресс за 7 дней",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Среднее выполнение: $weeklyPercentage%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            WeeklyTrendChart(weeklyTrend = weeklyTrend)
        }
    }
}

@Composable
fun WeeklyTrendChart(weeklyTrend: List<DayCompletion>) {
    val maxHeight = 120.dp
    if (weeklyTrend.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Недостаточно данных",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyTrend.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeight * day.completionRatio)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).replace('.', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}