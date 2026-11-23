package com.denispodpovetniy.traininglog.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Строка недельного плана: упражнение в конкретном дне недели.
 *
 * [weekday] — ISO 1..7, [position] — 0-based порядок внутри дня без дыр.
 */
@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["weekday", "exerciseId"], unique = true),
        Index(value = ["weekday", "position"]),
        Index(value = ["exerciseId"]),
    ],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekday: Int,
    val exerciseId: Long,
    val position: Int,
    @ColumnInfo(defaultValue = "''")
    val progress: String = "",
    val updatedAt: Long,
)
