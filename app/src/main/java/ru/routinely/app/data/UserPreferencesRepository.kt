package ru.routinely.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Создаем расширение для Context, чтобы получать доступ к хранилищу
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

open class UserPreferencesRepository(protected val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    private object PreferencesKeys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    // Читаем настройки как поток данных
    open val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isDarkTheme = preferences[PreferencesKeys.IS_DARK_THEME] ?: false
            val notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
            UserPreferences(isDarkTheme, notificationsEnabled)
        }

    // Сохраняем тему
    open suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDark
        }
    }

    // Сохраняем настройку уведомлений
    open suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
}

// Простая модель данных для настроек
data class UserPreferences(
    val isDarkTheme: Boolean,
    val notificationsEnabled: Boolean
)