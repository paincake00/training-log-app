package com.denispodpovetniy.traininglog.data.model

/** Статус тренировки. Хранится в БД строкой в колонке `workouts.status`. */
enum class WorkoutStatus {
    IN_PROGRESS,
    COMPLETED,
}
