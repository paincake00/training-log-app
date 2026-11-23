package com.denispodpovetniy.traininglog.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus

/**
 * Тренировка на конкретную дату. Одна тренировка на дату.
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["dateEpochDay"], unique = true),
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateEpochDay: Long,
    val status: WorkoutStatus,
    val createdAt: Long,
    val completedAt: Long? = null,
)
