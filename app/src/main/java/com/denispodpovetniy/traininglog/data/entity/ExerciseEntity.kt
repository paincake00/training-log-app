package com.denispodpovetniy.traininglog.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Глобальный пресет упражнения.
 *
 * Название, описание и [lastProgress] общие для плана и всех тренировок.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''")
    val description: String = "",
    @ColumnInfo(defaultValue = "''")
    val lastProgress: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
