package com.denispodpovetniy.traininglog.data.repository

/** Результат ручной синхронизации фактической прогрессии в план и пресет. */
sealed interface SyncResult {

    /** Обновлены и строка плана нужного дня, и `lastProgress` пресета. */
    data object UpdatedPlanAndPreset : SyncResult

    /** Строки плана на этот день нет — обновлён только `lastProgress`. */
    data object UpdatedPresetOnly : SyncResult

    /** Тренировка или упражнение тренировки не найдены. */
    data object NotFound : SyncResult
}

/** Результат добавления существующего пресета в план или тренировку. */
sealed interface AddResult {

    data class Added(val rowId: Long) : AddResult

    data object Duplicate : AddResult

    data object ExerciseNotFound : AddResult

    data object WorkoutNotFound : AddResult
}

/** Результат создания нового пресета сразу со строкой плана или тренировки. */
sealed interface CreateExerciseResult {

    data class Created(val exerciseId: Long, val rowId: Long) : CreateExerciseResult

    data object InvalidName : CreateExerciseResult

    data object WorkoutNotFound : CreateExerciseResult
}
