package com.denispodpovetniy.traininglog.ui.plan

import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanUiStateTest {

    @Test
    fun `empty plan has exactly seven days in order and one today`() {
        val days = buildPlanDays(rows = emptyList(), todayWeekday = 3)

        assertEquals((1..7).toList(), days.map { it.weekday })
        assertTrue(days.all { it.exercises.isEmpty() })
        assertEquals(listOf(3), days.filter { it.isToday }.map { it.weekday })
    }

    @Test
    fun `today flag is set only for matching weekday`() {
        val sunday = buildPlanDays(rows = emptyList(), todayWeekday = 7)
        assertTrue(sunday.last().isToday)
        assertFalse(sunday.first().isToday)

        val noToday = buildPlanDays(rows = emptyList(), todayWeekday = 0)
        assertTrue(noToday.none { it.isToday })
    }

    @Test
    fun `rows are grouped by weekday and sorted by position`() {
        val rows = listOf(
            planRow(rowId = 20, weekday = 2, position = 1),
            planRow(rowId = 21, weekday = 2, position = 0),
            planRow(rowId = 50, weekday = 5, position = 0),
            planRow(rowId = 22, weekday = 2, position = 2),
        )

        val days = buildPlanDays(rows, todayWeekday = 1)

        val tuesday = days.single { it.weekday == 2 }
        assertEquals(listOf(21L, 20L, 22L), tuesday.exercises.map { it.planRowId })
        assertEquals(listOf(0, 1, 2), tuesday.exercises.map { it.position })

        val friday = days.single { it.weekday == 5 }
        assertEquals(listOf(50L), friday.exercises.map { it.planRowId })

        assertTrue(days.single { it.weekday == 1 }.exercises.isEmpty())
        assertEquals(4, days.sumOf { it.exercises.size })
    }

    @Test
    fun `exercise fields are mapped from relation`() {
        val rows = listOf(
            planRow(
                rowId = 7,
                weekday = 4,
                position = 0,
                exerciseId = 99,
                name = "Подтягивания",
                description = "Широкий хват",
                progress = "5/20-5/20-5/0",
            ),
        )

        val exercise = buildPlanDays(rows, todayWeekday = 4)
            .single { it.weekday == 4 }
            .exercises
            .single()

        assertEquals(7L, exercise.planRowId)
        assertEquals(99L, exercise.exerciseId)
        assertEquals("Подтягивания", exercise.name)
        assertEquals("Широкий хват", exercise.description)
        assertEquals("5/20-5/20-5/0", exercise.progress)
    }

    @Test
    fun `reordered ids move up and down`() {
        val exercises = listOf(uiRow(1), uiRow(2), uiRow(3))

        assertEquals(listOf(2L, 1L, 3L), reorderedIds(exercises, rowId = 2, delta = -1))
        assertEquals(listOf(1L, 3L, 2L), reorderedIds(exercises, rowId = 2, delta = 1))
        assertEquals(listOf(3L, 1L, 2L), reorderedIds(exercises, rowId = 3, delta = -2))
    }

    @Test
    fun `reordered ids return null at edges and for unknown row`() {
        val exercises = listOf(uiRow(1), uiRow(2), uiRow(3))

        assertNull(reorderedIds(exercises, rowId = 1, delta = -1))
        assertNull(reorderedIds(exercises, rowId = 3, delta = 1))
        assertNull(reorderedIds(exercises, rowId = 42, delta = 1))
        assertNull(reorderedIds(exercises, rowId = 2, delta = 0))
        assertNull(reorderedIds(exercises, rowId = 2, delta = 5))
        assertNull(reorderedIds(emptyList(), rowId = 1, delta = 1))
    }

    private fun planRow(
        rowId: Long,
        weekday: Int,
        position: Int,
        exerciseId: Long = rowId,
        name: String = "Упражнение $rowId",
        description: String = "",
        progress: String = "",
    ): PlanExerciseWithExercise = PlanExerciseWithExercise(
        planExercise = PlanExerciseEntity(
            id = rowId,
            weekday = weekday,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = 0L,
        ),
        exercise = ExerciseEntity(
            id = exerciseId,
            name = name,
            description = description,
            lastProgress = "",
            createdAt = 0L,
            updatedAt = 0L,
        ),
    )

    private fun uiRow(rowId: Long): PlanExerciseUi = PlanExerciseUi(
        planRowId = rowId,
        exerciseId = rowId,
        name = "Упражнение $rowId",
        description = "",
        progress = "",
        position = 0,
    )
}
