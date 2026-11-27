package com.denispodpovetniy.traininglog.ui.plan

import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import com.denispodpovetniy.traininglog.util.ProgressCodec

/** Полное состояние экрана «Недельный план». */
data class PlanUiState(
    /** Всегда ровно 7 дней, понедельник → воскресенье. */
    val days: List<PlanDayUi> = emptyList(),
    /** Тренировка на сегодня, если она уже создана. */
    val todayWorkout: TodayWorkoutUi? = null,
    /** Одноразовое событие: открыть тренировку с этим id. */
    val openWorkoutId: Long? = null,
    val pickerWeekday: Int? = null,
    val pickerQuery: String = "",
    val pickerResults: List<ExerciseEntity> = emptyList(),
    /** Подсказки единиц: сохранённые пользователем и стартовый список. */
    val unitSuggestions: List<String> = emptyList(),
    val dialog: PlanDialog? = null,
    val message: PlanMessage? = null,
)

/** Минимум данных о сегодняшней тренировке для CTA в плане. */
data class TodayWorkoutUi(
    val workoutId: Long,
    val status: WorkoutStatus,
)

/** День недели плана: ISO 1..7. */
data class PlanDayUi(
    val weekday: Int,
    val isToday: Boolean,
    val exercises: List<PlanExerciseUi>,
)

/** Строка плана, готовая к отображению. */
data class PlanExerciseUi(
    val planRowId: Long,
    val exerciseId: Long,
    val name: String,
    val description: String,
    /** Единицы измерения пресета. */
    val units: List<String>,
    val progress: String,
    val position: Int,
)

/** Открытый диалог экрана плана. */
sealed interface PlanDialog {

    data class Create(val weekday: Int) : PlanDialog

    data class EditIdentity(
        val planRowId: Long,
        val exerciseId: Long,
        val name: String,
        val description: String,
    ) : PlanDialog

    data class EditProgress(
        val planRowId: Long,
        val exerciseId: Long,
        val progress: String,
    ) : PlanDialog

    data class Units(val exerciseId: Long) : PlanDialog

    data class ConfirmDelete(
        val planRowId: Long,
        val exerciseName: String,
    ) : PlanDialog
}

/** Разовое сообщение для snackbar. Тексты подставляются в Compose. */
enum class PlanMessage {
    DuplicateExercise,
    ExerciseNotFound,
    SaveFailed,
    UnitRemoved,
}

/**
 * Раскладывает строки плана по семи дням недели.
 *
 * Дни всегда идут по порядку 1..7, упражнения внутри дня — по `position`.
 */
fun buildPlanDays(
    rows: List<PlanExerciseWithExercise>,
    todayWeekday: Int,
): List<PlanDayUi> {
    val byWeekday = rows.groupBy { it.planExercise.weekday }

    return (1..7).map { weekday ->
        PlanDayUi(
            weekday = weekday,
            isToday = weekday == todayWeekday,
            exercises = byWeekday[weekday]
                .orEmpty()
                .sortedBy { it.planExercise.position }
                .map { row ->
                    PlanExerciseUi(
                        planRowId = row.planExercise.id,
                        exerciseId = row.exercise.id,
                        name = row.exercise.name,
                        description = row.exercise.description,
                        units = ProgressCodec.decodeUnits(row.exercise.units),
                        progress = row.planExercise.progress,
                        position = row.planExercise.position,
                    )
                },
        )
    }
}

/**
 * Возвращает новый порядок плановых строк после сдвига [rowId] на [delta].
 *
 * `null` — если строка не найдена или сдвиг выходит за границы дня.
 */
fun reorderedIds(
    exercises: List<PlanExerciseUi>,
    rowId: Long,
    delta: Int,
): List<Long>? {
    if (delta == 0) return null

    val index = exercises.indexOfFirst { it.planRowId == rowId }
    if (index < 0) return null

    val target = index + delta
    if (target !in exercises.indices) return null

    val ids = exercises.map { it.planRowId }.toMutableList()
    val moved = ids.removeAt(index)
    ids.add(target, moved)
    return ids
}
