package com.denispodpovetniy.traininglog.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity

/** Тренировка вместе со своими упражнениями. */
data class WorkoutWithExercises(
    @Embedded
    val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val exercises: List<WorkoutExerciseWithExercise>,
)
