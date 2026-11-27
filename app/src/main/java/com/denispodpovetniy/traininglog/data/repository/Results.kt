package com.denispodpovetniy.traininglog.data.repository

import com.denispodpovetniy.traininglog.util.PlanFileException

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

/** Сводка импорта плана: сколько пресетов добавлено, обновлено и сколько строк плана. */
data class PlanImportSummary(
    val addedExercises: Int,
    val updatedExercises: Int,
    val planRows: Int,
)

/** Результат импорта файла плана (см. `PLAN.md` §3.10). */
sealed interface ImportPlanResult {

    data class Imported(val summary: PlanImportSummary) : ImportPlanResult

    data class Rejected(val reason: PlanFileException.Reason) : ImportPlanResult
}
