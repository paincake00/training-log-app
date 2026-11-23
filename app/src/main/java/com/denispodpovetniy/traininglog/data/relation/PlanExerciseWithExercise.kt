package com.denispodpovetniy.traininglog.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity

/** Строка плана вместе с глобальным пресетом упражнения. */
data class PlanExerciseWithExercise(
    @Embedded
    val planExercise: PlanExerciseEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
    )
    val exercise: ExerciseEntity,
)
