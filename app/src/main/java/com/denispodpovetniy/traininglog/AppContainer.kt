package com.denispodpovetniy.traininglog

import android.content.Context
import androidx.room.Room
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository

/**
 * Ручной DI-контейнер приложения.
 *
 * Создаёт единственную базу данных и репозиторий для всего процесса.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME,
    ).build()

    val trainingRepository: TrainingRepository = TrainingRepository(database)
}
