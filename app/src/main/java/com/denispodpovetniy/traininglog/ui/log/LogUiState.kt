package com.denispodpovetniy.traininglog.ui.log

import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import com.denispodpovetniy.traininglog.util.ProgressCodec
import java.time.LocalDate

/** Полное состояние экрана «Лог». */
data class LogUiState(
    /** Тренировки в порядке лога: новые сверху. */
    val workouts: List<WorkoutLogUi> = emptyList(),
    /** Тренировка на сегодня, если она уже создана. */
    val todayWorkout: TodayWorkout? = null,
    val loading: Boolean = true,
    /** Одноразовое событие: открыть тренировку с этим id. */
    val openWorkoutId: Long? = null,
    val message: LogMessage? = null,
)

/** Карточка тренировки в логе. */
data class WorkoutLogUi(
    val workoutId: Long,
    val date: LocalDate,
    val isToday: Boolean,
    val status: WorkoutStatus,
    /** Упражнения в порядке позиций. */
    val exercises: List<LogExerciseUi>,
)

/** Упражнение внутри карточки лога. */
data class LogExerciseUi(
    val exerciseId: Long,
    val name: String,
    /** Единицы измерения пресета. */
    val units: List<String>,
    val progress: String,
    val description: String,
)

/** Минимум данных о сегодняшней тренировке для CTA. */
data class TodayWorkout(
    val workoutId: Long,
    val status: WorkoutStatus,
)

/** Разовое сообщение для snackbar. Текст подставляется в Compose. */
enum class LogMessage {
    SaveFailed,
}

/**
 * Превращает строки лога (уже отсортированные по дате DESC) в состояние экрана.
 *
 * `isToday` и [LogUiState.todayWorkout] определяются по `dateEpochDay`.
 */
fun buildLogUiState(
    rows: List<WorkoutWithExercises>,
    today: LocalDate,
): LogUiState {
    val todayEpochDay = today.toEpochDay()

    val workouts = rows.map { row ->
        WorkoutLogUi(
            workoutId = row.workout.id,
            date = LocalDate.ofEpochDay(row.workout.dateEpochDay),
            isToday = row.workout.dateEpochDay == todayEpochDay,
            status = row.workout.status,
            exercises = row.exercises
                .sortedBy { it.workoutExercise.position }
                .map { exerciseRow ->
                    LogExerciseUi(
                        exerciseId = exerciseRow.exercise.id,
                        name = exerciseRow.exercise.name,
                        units = ProgressCodec.decodeUnits(exerciseRow.exercise.units),
                        progress = exerciseRow.workoutExercise.progress,
                        description = exerciseRow.exercise.description,
                    )
                },
        )
    }

    val todayWorkout = rows
        .firstOrNull { it.workout.dateEpochDay == todayEpochDay }
        ?.let { TodayWorkout(it.workout.id, it.workout.status) }

    return LogUiState(
        workouts = workouts,
        todayWorkout = todayWorkout,
        loading = false,
    )
}
