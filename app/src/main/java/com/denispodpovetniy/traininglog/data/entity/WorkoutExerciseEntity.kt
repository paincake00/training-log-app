package com.denispodpovetniy.traininglog.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Упражнение в тренировке: ссылка на пресет и фактическая прогрессия на эту дату.
 *
 * [position] — 0-based порядок внутри тренировки без дыр.
 */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workoutId", "exerciseId"], unique = true),
        Index(value = ["workoutId", "position"]),
        Index(value = ["exerciseId"]),
    ],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    @ColumnInfo(defaultValue = "''")
    val progress: String = "",
    val updatedAt: Long,
)
