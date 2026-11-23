package com.denispodpovetniy.traininglog.ui.workout

import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import com.denispodpovetniy.traininglog.util.isoWeekday
import java.time.LocalDate

/** Полное состояние экрана тренировки. */
data class WorkoutUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val workoutId: Long = 0L,
    val date: LocalDate? = null,
    val status: WorkoutStatus? = null,
    /** Упражнения в порядке позиций. */
    val exercises: List<WorkoutExerciseUi> = emptyList(),
    val pickerVisible: Boolean = false,
    val pickerQuery: String = "",
    val pickerResults: List<ExerciseEntity> = emptyList(),
    val dialog: WorkoutDialog? = null,
    val message: WorkoutMessage? = null,
    /** Одноразовое событие выхода с экрана. */
    val exit: WorkoutExit? = null,
)

/** Строка тренировки, готовая к отображению. */
data class WorkoutExerciseUi(
    val rowId: Long,
    val exerciseId: Long,
    val name: String,
    val description: String,
    /** Плановая прогрессия на день недели тренировки; пустая — подсказку не показываем. */
    val planProgress: String,
    /** Фактическая прогрессия с учётом ещё не сохранённого ввода. */
    val progress: String,
    val position: Int,
)

/** Открытый диалог экрана тренировки. */
sealed interface WorkoutDialog {

    data object Create : WorkoutDialog

    data class EditIdentity(
        val rowId: Long,
        val exerciseId: Long,
        val name: String,
        val description: String,
    ) : WorkoutDialog

    data class ConfirmDeleteExercise(
        val rowId: Long,
        val exerciseName: String,
    ) : WorkoutDialog

    data object ConfirmDeleteWorkout : WorkoutDialog
}

/** Разовое сообщение для snackbar. Тексты подставляются в Compose. */
enum class WorkoutMessage {
    DuplicateExercise,
    ExerciseNotFound,
    SaveFailed,
    SyncedPlanAndPreset,
    SyncedPresetOnly,
    NotInProgress,
}

/** Одноразовое событие выхода с экрана тренировки. */
enum class WorkoutExit {
    /** Системная кнопка «Назад» или стрелка: черновик остаётся в логе. */
    Back,

    /** Тренировка завершена, возвращаемся в лог. */
    Finished,

    /** Тренировка удалена, возвращаемся в лог. */
    Deleted,
}

/**
 * Собирает состояние экрана из тренировки, строк плана и несохранённого ввода.
 *
 * Подсказка «По плану» берётся из плана дня недели даты тренировки и только
 * для непустой плановой прогрессии.
 */
fun buildWorkoutUiState(
    requestedId: Long,
    workout: WorkoutWithExercises?,
    planRows: List<PlanExerciseWithExercise>,
    progressOverrides: Map<Long, String>,
): WorkoutUiState {
    if (workout == null) {
        return WorkoutUiState(
            loading = false,
            notFound = true,
            workoutId = requestedId,
        )
    }

    val date = LocalDate.ofEpochDay(workout.workout.dateEpochDay)
    val weekday = isoWeekday(date)
    val planProgressByExerciseId = planRows
        .asSequence()
        .filter { it.planExercise.weekday == weekday && it.planExercise.progress.isNotBlank() }
        .associate { it.exercise.id to it.planExercise.progress }

    val exercises = workout.exercises
        .sortedBy { it.workoutExercise.position }
        .map { row ->
            WorkoutExerciseUi(
                rowId = row.workoutExercise.id,
                exerciseId = row.exercise.id,
                name = row.exercise.name,
                description = row.exercise.description,
                planProgress = planProgressByExerciseId[row.exercise.id].orEmpty(),
                progress = progressOverrides[row.workoutExercise.id] ?: row.workoutExercise.progress,
                position = row.workoutExercise.position,
            )
        }

    return WorkoutUiState(
        loading = false,
        workoutId = workout.workout.id,
        date = date,
        status = workout.workout.status,
        exercises = exercises,
    )
}

/**
 * Возвращает новый порядок строк тренировки после сдвига [rowId] на [delta].
 *
 * `null` — если строка не найдена или сдвиг выходит за границы списка.
 */
fun reorderedWorkoutIds(
    exercises: List<WorkoutExerciseUi>,
    rowId: Long,
    delta: Int,
): List<Long>? {
    if (delta == 0) return null

    val index = exercises.indexOfFirst { it.rowId == rowId }
    if (index < 0) return null

    val target = index + delta
    if (target !in exercises.indices) return null

    val ids = exercises.map { it.rowId }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(target, moved)
    return ids
}
