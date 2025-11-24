package ru.routinely.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.routinely.app.model.Habit
import ru.routinely.app.model.HabitCompletion

/**
 * Основной класс базы данных приложения, построенный на Room.
 * @property entities Список всех классов-сущностей (таблиц), которые включает база данных.
 * @property version Версия схемы базы данных. Необходимо инкрементировать при каждом изменении схемы.
 * @property exportSchema Определяет, должна ли Room экспортировать схему в JSON-файл в папке проекта.
 */
@Database(entities = [Habit::class, HabitCompletion::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Абстрактный метод для получения экземпляра DAO.
     * Room автоматически сгенерирует реализацию этого метода во время компиляции.
     * @return Экземпляр HabitDao для взаимодействия с таблицей привычек.
     */
    abstract fun habitDao(): HabitDao

    /**
     * Companion object для реализации паттерна Singleton.
     * Это гарантирует, что на все приложение будет существовать только один
     * экземпляр базы данных, что предотвращает гонки состояний и излишнее потребление ресурсов.
     */
    companion object {
        // @Volatile гарантирует, что значение переменной INSTANCE всегда будет актуальным
        // для всех потоков выполнения.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Возвращает синглтон-экземпляр AppDatabase.
         * Если экземпляр еще не создан, он будет создан в потокобезопасном режиме.
         *
         * @param context Контекст приложения, необходимый для создания базы данных.
         * @return Единственный экземпляр AppDatabase.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Если экземпляр уже существует, возвращаем его.
            // Если нет - входим в синхронизированный блок, чтобы избежать
            // создания нескольких экземпляров в многопоточной среде.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Используем applicationContext для предотвращения утечек памяти
                    AppDatabase::class.java,
                    "routinely_database" // Имя файла локальной базы данных
                )
                    // .fallbackToDestructiveMigration() - Стратегия миграции для разработки.
                    // При повышении версии базы данных, Room удалит старую схему и данные
                    // и создаст все заново.
                    // ВАЖНО: Для продакшн-версии необходимо заменить это на реализацию миграций.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                // Возвращаем созданный или уже существующий экземпляр.
                instance
            }
        }
    }
}