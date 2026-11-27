package com.denispodpovetniy.traininglog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    /**
     * Все пресеты для поиска в памяти. SQLite `LIKE` не умеет регистронезависимый
     * поиск для кириллицы, поэтому фильтрация выполняется в репозитории.
     * Порядок: последние изменённые сверху, затем по названию.
     */
    @Query("SELECT * FROM exercises ORDER BY updatedAt DESC, name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query(
        """
        UPDATE exercises
        SET name = :name, description = :description, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateIdentity(id: Long, name: String, description: String, now: Long): Int

    @Query(
        """
        UPDATE exercises
        SET lastProgress = :progress, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateLastProgress(id: Long, progress: String, now: Long): Int

    @Query("UPDATE exercises SET units = :units, updatedAt = :now WHERE id = :id")
    suspend fun updateUnits(id: Long, units: String, now: Long): Int
}
