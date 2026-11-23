package com.denispodpovetniy.traininglog.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity

/** Строка тренировки вместе с глобальным пресетом упражнения. */
data class WorkoutExerciseWithExercise(
    @Embedded
    val workoutExercise: WorkoutExerciseEntity,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
    )
    val exercise: ExerciseEntity,
)
