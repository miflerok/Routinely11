package ru.routinely.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.routinely.app.data.UserPreferencesRepository

class UserPreferencesRepositoryTest {

    private val repository: UserPreferencesRepository = TestUserPreferencesRepository()

    @Test
    fun `userPreferencesFlow emits defaults when store is empty`() = runTest {
        val preferences = repository.userPreferencesFlow.first()

        assertFalse(preferences.isDarkTheme)
        assertTrue(preferences.notificationsEnabled)
    }

    @Test
    fun `setDarkTheme persists provided value`() = runTest {
        repository.setDarkTheme(true)

        val preferences = repository.userPreferencesFlow.first()
        assertTrue(preferences.isDarkTheme)
    }

    @Test
    fun `setNotificationsEnabled persists provided value`() = runTest {
        repository.setNotificationsEnabled(false)

        val preferences = repository.userPreferencesFlow.first()
        assertFalse(preferences.notificationsEnabled)
    }
}
