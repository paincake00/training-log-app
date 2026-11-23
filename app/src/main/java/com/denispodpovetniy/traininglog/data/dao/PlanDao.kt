package com.denispodpovetniy.traininglog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Transaction
    @Query("SELECT * FROM plan_exercises ORDER BY weekday ASC, position ASC")
    fun observePlan(): Flow<List<PlanExerciseWithExercise>>

    @Query("SELECT * FROM plan_exercises WHERE weekday = :weekday ORDER BY position ASC")
    suspend fun getByWeekday(weekday: Int): List<PlanExerciseEntity>

    @Query("SELECT * FROM plan_exercises WHERE id = :id")
    suspend fun getById(id: Long): PlanExerciseEntity?

    @Query("SELECT * FROM plan_exercises WHERE weekday = :weekday AND exerciseId = :exerciseId")
    suspend fun findByWeekdayAndExercise(weekday: Int, exerciseId: Long): PlanExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM plan_exercises WHERE weekday = :weekday")
    suspend fun maxPosition(weekday: Int): Int

    @Insert
    suspend fun insert(row: PlanExerciseEntity): Long

    @Query("UPDATE plan_exercises SET progress = :progress, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: String, now: Long): Int

    @Query("UPDATE plan_exercises SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM plan_exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
