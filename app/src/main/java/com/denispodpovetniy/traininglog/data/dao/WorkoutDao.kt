package com.denispodpovetniy.traininglog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC")
    fun observeLog(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Long): WorkoutEntity?

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("UPDATE workouts SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: WorkoutStatus, completedAt: Long?)

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position ASC")
    suspend fun getExercises(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun findExercise(workoutId: Long, exerciseId: Long): WorkoutExerciseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxPosition(workoutId: Long): Int

    @Insert
    suspend fun insertExercise(row: WorkoutExerciseEntity): Long

    @Query("UPDATE workout_exercises SET progress = :progress, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: String, now: Long): Int

    @Query("UPDATE workout_exercises SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteExerciseById(id: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
