package com.denispodpovetniy.traininglog.ui.log

import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.WorkoutExerciseWithExercise
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUiStateTest {

    @Test
    fun `rows keep descending order and today flag only for today`() {
        val rows = listOf(
            workoutRow(workoutId = 3, date = TUESDAY),
            workoutRow(workoutId = 2, date = MONDAY),
            workoutRow(workoutId = 1, date = SUNDAY),
        )

        val state = buildLogUiState(rows, today = TUESDAY)

        assertEquals(listOf(3L, 2L, 1L), state.workouts.map { it.workoutId })
        assertEquals(listOf(TUESDAY, MONDAY, SUNDAY), state.workouts.map { it.date })
        assertEquals(listOf(true, false, false), state.workouts.map { it.isToday })
        assertFalse(state.loading)
    }

    @Test
    fun `today workout is derived by date and exposes status`() {
        val rows = listOf(
            workoutRow(workoutId = 5, date = MONDAY, status = WorkoutStatus.COMPLETED),
            workoutRow(workoutId = 4, date = TUESDAY, status = WorkoutStatus.IN_PROGRESS),
        )

        val state = buildLogUiState(rows, today = TUESDAY)

        assertEquals(TodayWorkout(workoutId = 4, status = WorkoutStatus.IN_PROGRESS), state.todayWorkout)

        val completedState = buildLogUiState(
            listOf(workoutRow(workoutId = 4, date = TUESDAY, status = WorkoutStatus.COMPLETED)),
            today = TUESDAY,
        )
        assertEquals(
            TodayWorkout(workoutId = 4, status = WorkoutStatus.COMPLETED),
            completedState.todayWorkout,
        )
    }

    @Test
    fun `no workout today yields null today workout`() {
        val state = buildLogUiState(
            listOf(workoutRow(workoutId = 7, date = MONDAY)),
            today = TUESDAY,
        )

        assertNull(state.todayWorkout)
        assertTrue(state.workouts.none { it.isToday })
    }

    @Test
    fun `empty log has no workouts and null today workout`() {
        val state = buildLogUiState(rows = emptyList(), today = TUESDAY)

        assertTrue(state.workouts.isEmpty())
        assertNull(state.todayWorkout)
        assertFalse(state.loading)
    }

    @Test
    fun `empty workout keeps no exercises`() {
        val state = buildLogUiState(
            listOf(workoutRow(workoutId = 8, date = TUESDAY)),
            today = TUESDAY,
        )

        assertTrue(state.workouts.single().exercises.isEmpty())
    }

    @Test
    fun `exercises are sorted by position and fields are mapped from relations`() {
        val rows = listOf(
            workoutRow(
                workoutId = 9,
                date = TUESDAY,
                exercises = listOf(
                    exerciseRow(
                        rowId = 90,
                        exerciseId = 901,
                        position = 1,
                        name = "Жим",
                        progress = "60-60-60",
                        description = "Лёжа",
                    ),
                    exerciseRow(
                        rowId = 91,
                        exerciseId = 902,
                        position = 0,
                        name = "Подтягивания",
                        progress = "5/20-5/20-5/0",
                        description = "Широкий хват",
                    ),
                ),
            ),
        )

        val workout = buildLogUiState(rows, today = TUESDAY).workouts.single()

        assertEquals(listOf(902L, 901L), workout.exercises.map { it.exerciseId })
        assertEquals(listOf("Подтягивания", "Жим"), workout.exercises.map { it.name })
        assertEquals(listOf("5/20-5/20-5/0", "60-60-60"), workout.exercises.map { it.progress })
        assertEquals(listOf("Широкий хват", "Лёжа"), workout.exercises.map { it.description })
    }

    private fun workoutRow(
        workoutId: Long,
        date: LocalDate,
        status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
        exercises: List<WorkoutExerciseWithExercise> = emptyList(),
    ): WorkoutWithExercises = WorkoutWithExercises(
        workout = WorkoutEntity(
            id = workoutId,
            dateEpochDay = date.toEpochDay(),
            status = status,
            createdAt = 0L,
        ),
        exercises = exercises,
    )

    private fun exerciseRow(
        rowId: Long,
        exerciseId: Long,
        position: Int,
        name: String,
        progress: String,
        description: String,
    ): WorkoutExerciseWithExercise = WorkoutExerciseWithExercise(
        workoutExercise = WorkoutExerciseEntity(
            id = rowId,
            workoutId = 1L,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = 0L,
        ),
        exercise = ExerciseEntity(
            id = exerciseId,
            name = name,
            description = description,
            createdAt = 0L,
            updatedAt = 0L,
        ),
    )

    private companion object {
        val TUESDAY: LocalDate = LocalDate.of(2026, 11, 24)
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)
        val SUNDAY: LocalDate = LocalDate.of(2026, 11, 22)
    }
}
