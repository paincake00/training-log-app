package com.denispodpovetniy.traininglog.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Глобальный пресет упражнения.
 *
 * Название, описание, единицы измерения и [lastProgress] общие для плана и всех тренировок.
 * [units] хранится как список названий, разделённых переводом строки (см. `ProgressCodec`).
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
    @ColumnInfo(defaultValue = "''")
    val units: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
