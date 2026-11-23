package com.denispodpovetniy.traininglog.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Room (in-memory) тесты данных и бизнес-логики [TrainingRepository].
 *
 * Сценарии соответствуют `PLAN.md` §9.1. Даты фиксированы:
 * понедельник 23.11.2026, вторник 24.11.2026, воскресенье 29.11.2026.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainingRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrainingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------- сценарий 1

    @Test
    fun `scenario 1 - create workout copies plan exercises in order with plan progress`() = runTest {
        val squat = insertExercise("Присед")
        val bench = insertExercise("Жим")
        val row = insertExercise("Тяга")
        addPlanRow(weekday = 1, exerciseId = squat, position = 0, progress = "5/20-5/20-5/20")
        addPlanRow(weekday = 1, exerciseId = bench, position = 1, progress = "60-60-60")
        addPlanRow(weekday = 1, exerciseId = row, position = 2, progress = "")

        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        val workout = db.workoutDao().getById(workoutId)
        assertNotNull(workout)
        workout!!
        assertEquals(MONDAY.toEpochDay(), workout.dateEpochDay)
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertEquals(T0, workout.createdAt)
        assertNull(workout.completedAt)

        val rows = workoutRows(workoutId)
        assertEquals(listOf(squat, bench, row), rows.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
        assertEquals(listOf("5/20-5/20-5/20", "60-60-60", ""), rows.map { it.progress })
        assertEquals(listOf(T0, T0, T0), rows.map { it.updatedAt })

        val snapshot = workoutSnapshot(workoutId)!!
        assertEquals(listOf("Присед", "Жим", "Тяга"), snapshot.exercises.map { it.exercise.name })
        assertEquals(listOf(0, 1, 2), snapshot.exercises.map { it.workoutExercise.position })
    }

    // ---------------------------------------------------------------- сценарий 2

    @Test
    fun `scenario 2 - repeated creation for the same date returns existing id without duplicate`() = runTest {
        val exercise = insertExercise("Присед")
        addPlanRow(weekday = 2, exerciseId = exercise, position = 0, progress = "10-10")

        val firstId = repository.createWorkoutForDate(TUESDAY, now = T0)
        val secondId = repository.createWorkoutForDate(TUESDAY, now = T2)

        assertEquals(firstId, secondId)
        assertEquals(1, countRows("workouts"))

        val workout = db.workoutDao().getById(firstId)!!
        assertEquals(T0, workout.createdAt)
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)

        val rows = workoutRows(firstId)
        assertEquals(1, rows.size)
        assertEquals("10-10", rows.single().progress)
        assertEquals(T0, rows.single().updatedAt)
        assertEquals(1, repository.observeLog().first().size)
    }

    // ---------------------------------------------------------------- сценарий 3

    @Test
    fun `scenario 3 - workout for empty plan day is created empty`() = runTest {
        val exercise = insertExercise("Присед")
        addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "5")

        val workoutId = repository.createWorkoutForDate(SUNDAY, now = T0)

        val workout = db.workoutDao().getById(workoutId)
        assertNotNull(workout)
        assertEquals(SUNDAY.toEpochDay(), workout!!.dateEpochDay)
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertEquals(T0, workout.createdAt)

        assertTrue(workoutRows(workoutId).isEmpty())
        assertTrue(workoutSnapshot(workoutId)!!.exercises.isEmpty())
        assertEquals(1, planRows(1).size)
    }

    // ---------------------------------------------------------------- сценарий 4

    @Test
    fun `scenario 4 - reload updates plan progress of workout weekday and lastProgress`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "старый пресет")
        val planRowId = addPlanRow(weekday = 2, exerciseId = exercise, position = 0, progress = "старый план")
        val workoutId = repository.createWorkoutForDate(TUESDAY, now = T0)
        val workoutRow = workoutRows(workoutId).single()
        repository.updateWorkoutExerciseProgress(workoutRow.id, "новый факт", now = T1)

        val result = repository.syncProgressToPlan(workoutId, workoutRow.id, now = T2)

        assertEquals(SyncResult.UpdatedPlanAndPreset, result)

        val planRow = db.planDao().getById(planRowId)!!
        assertEquals("новый факт", planRow.progress)
        assertEquals(T2, planRow.updatedAt)

        val preset = db.exerciseDao().getById(exercise)!!
        assertEquals("новый факт", preset.lastProgress)
        assertEquals(T2, preset.updatedAt)

        assertEquals("новый факт", workoutRows(workoutId).single().progress)
    }

    // ---------------------------------------------------------------- сценарий 5

    @Test
    fun `scenario 5 - reload does not touch other plan days or other workouts`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "база")
        val mondayPlanId = addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "пн-план")
        val tuesdayPlanId = addPlanRow(weekday = 2, exerciseId = exercise, position = 0, progress = "вт-план")

        val mondayWorkoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        val tuesdayWorkoutId = repository.createWorkoutForDate(TUESDAY, now = T0)
        val tuesdayRow = workoutRows(tuesdayWorkoutId).single()
        repository.updateWorkoutExerciseProgress(tuesdayRow.id, "вт-факт", now = T1)

        val result = repository.syncProgressToPlan(tuesdayWorkoutId, tuesdayRow.id, now = T2)

        assertEquals(SyncResult.UpdatedPlanAndPreset, result)
        assertEquals("вт-факт", db.planDao().getById(tuesdayPlanId)!!.progress)
        assertEquals("пн-план", db.planDao().getById(mondayPlanId)!!.progress)

        val mondayRow = workoutRows(mondayWorkoutId).single()
        assertEquals("пн-план", mondayRow.progress)
        assertEquals(T0, mondayRow.updatedAt)

        assertEquals("вт-факт", db.exerciseDao().getById(exercise)!!.lastProgress)
        assertEquals("вт-факт", workoutRows(tuesdayWorkoutId).single().progress)
        assertEquals(2, planRows(1).size + planRows(2).size)
    }

    // ---------------------------------------------------------------- сценарий 6

    @Test
    fun `scenario 6 - reload without plan row updates only lastProgress`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "старый пресет")
        val otherExercise = insertExercise("Жим", lastProgress = "жим-пресет")
        val mondayPlanId = addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "пн-план")
        val tuesdayPlanId = addPlanRow(weekday = 2, exerciseId = otherExercise, position = 0, progress = "вт-план")

        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        val workoutRow = workoutRows(workoutId).single()
        repository.updateWorkoutExerciseProgress(workoutRow.id, "только факт", now = T1)
        repository.deletePlanExercise(mondayPlanId)

        val result = repository.syncProgressToPlan(workoutId, workoutRow.id, now = T2)

        assertEquals(SyncResult.UpdatedPresetOnly, result)
        assertTrue(planRows(1).isEmpty())
        assertEquals("только факт", db.exerciseDao().getById(exercise)!!.lastProgress)
        assertEquals(T2, db.exerciseDao().getById(exercise)!!.updatedAt)

        assertEquals("вт-план", db.planDao().getById(tuesdayPlanId)!!.progress)
        assertEquals("жим-пресет", db.exerciseDao().getById(otherExercise)!!.lastProgress)
        assertEquals("только факт", workoutRows(workoutId).single().progress)
    }

    // ---------------------------------------------------------------- сценарий 7

    @Test
    fun `scenario 7 - manual plan progress update also updates lastProgress`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "")
        val planRowId = addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "старое")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        assertEquals("старое", workoutRows(workoutId).single().progress)

        repository.updatePlanProgress(planRowId, " 5/20-5/20 ", now = T1)

        val planRow = db.planDao().getById(planRowId)!!
        assertEquals(" 5/20-5/20 ", planRow.progress)
        assertEquals(T1, planRow.updatedAt)

        val preset = db.exerciseDao().getById(exercise)!!
        assertEquals(" 5/20-5/20 ", preset.lastProgress)
        assertEquals(T1, preset.updatedAt)

        // Изменение плана не переписывает уже существующую тренировку.
        assertEquals("старое", workoutRows(workoutId).single().progress)
        assertEquals(T0, workoutRows(workoutId).single().updatedAt)
    }

    @Test
    fun `updatePlanProgress for unknown id changes nothing`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "пресет")
        addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "план")

        repository.updatePlanProgress(9999L, "другое", now = T1)

        assertEquals("план", planRows(1).single().progress)
        assertEquals("пресет", db.exerciseDao().getById(exercise)!!.lastProgress)
    }

    // ---------------------------------------------------------------- сценарий 8

    @Test
    fun `scenario 8 - rename and description change are visible in plan and all workouts`() = runTest {
        val exercise = insertExercise("Старое имя", description = "Старое описание", lastProgress = "5")
        addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "5")
        addPlanRow(weekday = 2, exerciseId = exercise, position = 0, progress = "6")

        val mondayId = repository.createWorkoutForDate(MONDAY, now = T0)
        val tuesdayId = repository.createWorkoutForDate(TUESDAY, now = T0)

        assertTrue(repository.updateExerciseIdentity(exercise, "  Новое имя  ", "Новое\nописание  ", now = T1))

        val planEntry = planSnapshot().single { it.planExercise.weekday == 1 }
        assertEquals("Новое имя", planEntry.exercise.name)
        assertEquals("Новое\nописание  ", planEntry.exercise.description)
        assertEquals(T1, planEntry.exercise.updatedAt)

        val monday = workoutSnapshot(mondayId)!!
        val tuesday = workoutSnapshot(tuesdayId)!!
        assertEquals("Новое имя", monday.exercises.single().exercise.name)
        assertEquals("Новое\nописание  ", monday.exercises.single().exercise.description)
        assertEquals("Новое имя", tuesday.exercises.single().exercise.name)
        assertEquals("Новое\nописание  ", tuesday.exercises.single().exercise.description)

        val log = logSnapshot()
        assertEquals(2, log.size)
        log.forEach { entry ->
            assertEquals("Новое имя", entry.exercises.single().exercise.name)
            assertEquals("Новое\nописание  ", entry.exercises.single().exercise.description)
        }

        // Название/описание общие, но прогрессии не меняются.
        assertEquals("5", planEntry.planExercise.progress)
        assertEquals("5", monday.exercises.single().workoutExercise.progress)
        assertEquals("6", tuesday.exercises.single().workoutExercise.progress)
        assertEquals("5", db.exerciseDao().getById(exercise)!!.lastProgress)
    }

    @Test
    fun `updateExerciseIdentity rejects blank name and unknown exercise`() = runTest {
        val exercise = insertExercise("Имя", description = "Описание", lastProgress = "5")
        assertTrue(repository.updateExerciseIdentity(exercise, "Новое", "Новое описание", now = T1))

        assertFalse(repository.updateExerciseIdentity(exercise, "   ", "Другое", now = T2))
        assertFalse(repository.updateExerciseIdentity(exercise, "\n\t ", "Другое", now = T2))
        assertFalse(repository.updateExerciseIdentity(9999L, "Существует", "", now = T2))

        val preset = db.exerciseDao().getById(exercise)!!
        assertEquals("Новое", preset.name)
        assertEquals("Новое описание", preset.description)
        assertEquals(T1, preset.updatedAt)
    }

    // ---------------------------------------------------------------- сценарий 9

    @Test
    fun `scenario 9 - duplicate exercise in plan is rejected by repository and unique index`() = runTest {
        val exercise = insertExercise("Присед")
        addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "5")

        val result = repository.addPlanExercise(weekday = 1, exerciseId = exercise, now = T1)

        assertEquals(AddResult.Duplicate, result)
        assertEquals(1, planRows(1).size)

        val error = runCatching {
            db.planDao().insert(
                PlanExerciseEntity(
                    weekday = 1,
                    exerciseId = exercise,
                    position = 1,
                    progress = "6",
                    updatedAt = T1,
                ),
            )
        }.exceptionOrNull()

        assertTrue(
            "Ожидалось нарушение UNIQUE(weekday, exerciseId), получено: $error",
            error is SQLiteConstraintException,
        )
        assertEquals(1, planRows(1).size)
        assertEquals(1, countRows("plan_exercises"))
    }

    // ---------------------------------------------------------------- сценарий 10

    @Test
    fun `scenario 10 - duplicate exercise in workout is rejected by repository and unique index`() = runTest {
        val exercise = insertExercise("Присед")
        addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "5")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        val result = repository.addWorkoutExercise(workoutId, exercise, now = T1)

        assertEquals(AddResult.Duplicate, result)
        assertEquals(1, workoutRows(workoutId).size)

        val error = runCatching {
            db.workoutDao().insertExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exercise,
                    position = 1,
                    progress = "6",
                    updatedAt = T1,
                ),
            )
        }.exceptionOrNull()

        assertTrue(
            "Ожидалось нарушение UNIQUE(workoutId, exerciseId), получено: $error",
            error is SQLiteConstraintException,
        )
        assertEquals(1, workoutRows(workoutId).size)
        assertEquals(1, countRows("workout_exercises"))
    }

    // ---------------------------------------------------------------- сценарий 11

    @Test
    fun `scenario 11 - deleting workout cascades its exercises and keeps presets and plan`() = runTest {
        val first = insertExercise("Присед", lastProgress = "п1")
        val second = insertExercise("Жим", lastProgress = "п2")
        addPlanRow(weekday = 1, exerciseId = first, position = 0, progress = "пн-1")
        addPlanRow(weekday = 1, exerciseId = second, position = 1, progress = "пн-2")
        addPlanRow(weekday = 2, exerciseId = first, position = 0, progress = "вт-1")

        val mondayId = repository.createWorkoutForDate(MONDAY, now = T0)
        val tuesdayId = repository.createWorkoutForDate(TUESDAY, now = T0)
        assertEquals(2, countRows("workout_exercises", "workoutId = ?", mondayId))

        repository.deleteWorkout(mondayId)

        assertNull(db.workoutDao().getById(mondayId))
        assertEquals(0, countRows("workout_exercises", "workoutId = ?", mondayId))
        assertEquals(1, countRows("workout_exercises", "workoutId = ?", tuesdayId))

        assertNotNull(db.exerciseDao().getById(first))
        assertNotNull(db.exerciseDao().getById(second))
        assertEquals(2, countRows("exercises"))
        assertEquals(2, planRows(1).size)
        assertEquals(1, planRows(2).size)

        assertNotNull(db.workoutDao().getById(tuesdayId))
        assertEquals(listOf(first), workoutRows(tuesdayId).map { it.exerciseId })
    }

    // ---------------------------------------------------------------- сценарий 12

    @Test
    fun `scenario 12 - deleting plan exercise keeps preset and workout history`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "5")
        val planRowId = addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "5")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        repository.deletePlanExercise(planRowId)

        assertTrue(planRows(1).isEmpty())
        assertNotNull(db.exerciseDao().getById(exercise))
        assertNotNull(db.workoutDao().getById(workoutId))

        val historyRow = workoutRows(workoutId).single()
        assertEquals(exercise, historyRow.exerciseId)
        assertEquals("5", historyRow.progress)
        assertEquals("5", db.exerciseDao().getById(exercise)!!.lastProgress)
    }

    @Test
    fun `deleting a plan exercise compacts only its weekday and keeps history`() = runTest {
        val firstExercise = insertExercise("А", lastProgress = "а")
        val secondExercise = insertExercise("Б", lastProgress = "б")
        val thirdExercise = insertExercise("В", lastProgress = "в")

        val first = addPlanRow(weekday = 1, exerciseId = firstExercise, position = 0, progress = "а")
        val second = addPlanRow(weekday = 1, exerciseId = secondExercise, position = 1, progress = "б")
        val third = addPlanRow(weekday = 1, exerciseId = thirdExercise, position = 2, progress = "в")
        val otherWeekday = addPlanRow(weekday = 2, exerciseId = firstExercise, position = 0, progress = "вт")

        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        assertEquals(3, workoutRows(workoutId).size)

        repository.deletePlanExercise(second)

        val mondayRows = planRows(1)
        assertEquals(listOf(first, third), mondayRows.map { it.id })
        assertEquals(listOf(0, 1), mondayRows.map { it.position })
        assertEquals(listOf("а", "в"), mondayRows.map { it.progress })

        // Соседний день недели не затронут.
        val tuesdayRows = planRows(2)
        assertEquals(listOf(otherWeekday), tuesdayRows.map { it.id })
        assertEquals(0, tuesdayRows.single().position)

        // История тренировки и все пресеты сохранились.
        val history = workoutRows(workoutId)
        assertEquals(listOf(firstExercise, secondExercise, thirdExercise), history.map { it.exerciseId })
        assertEquals(3, countRows("exercises"))
        assertNotNull(db.exerciseDao().getById(secondExercise))

        // Новое упражнение встаёт после компактификации, без дыры.
        val newExercise = insertExercise("Г", lastProgress = "г")
        val added = repository.addPlanExercise(weekday = 1, exerciseId = newExercise, now = T1)
        added as AddResult.Added
        assertEquals(2, planRows(1).single { it.id == added.rowId }.position)
    }

    // ---------------------------------------------------------------- сценарий 13

    @Test
    fun `scenario 13 - reorder plan keeps order without gaps`() = runTest {
        val first = addPlanRow(weekday = 1, exerciseId = insertExercise("А"), position = 0, progress = "а")
        val second = addPlanRow(weekday = 1, exerciseId = insertExercise("Б"), position = 1, progress = "б")
        val third = addPlanRow(weekday = 1, exerciseId = insertExercise("В"), position = 2, progress = "в")

        repository.reorderPlan(weekday = 1, orderedIds = listOf(third, first, second))

        val rows = planRows(1)
        assertEquals(listOf(third, first, second), rows.map { it.id })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
        assertEquals(listOf("в", "а", "б"), rows.map { it.progress })

        val snapshot = planSnapshot()
        assertEquals(listOf(third, first, second), snapshot.map { it.planExercise.id })
        assertEquals(listOf(0, 1, 2), snapshot.map { it.planExercise.position })
    }

    @Test
    fun `reorderPlan rejects incomplete or duplicate id lists and keeps order`() = runTest {
        val first = addPlanRow(weekday = 1, exerciseId = insertExercise("А"), position = 0)
        val second = addPlanRow(weekday = 1, exerciseId = insertExercise("Б"), position = 1)
        val third = addPlanRow(weekday = 1, exerciseId = insertExercise("В"), position = 2)

        assertFailsWithType<IllegalArgumentException> {
            repository.reorderPlan(weekday = 1, orderedIds = listOf(third, first))
        }
        assertFailsWithType<IllegalArgumentException> {
            repository.reorderPlan(weekday = 1, orderedIds = listOf(third, third, first))
        }
        // id чужого дня тоже отклоняется.
        val otherDayId = addPlanRow(weekday = 2, exerciseId = insertExercise("Г"), position = 0)
        assertFailsWithType<IllegalArgumentException> {
            repository.reorderPlan(weekday = 1, orderedIds = listOf(third, first, otherDayId))
        }
        assertFailsWithType<IllegalArgumentException> {
            repository.reorderPlan(weekday = 0, orderedIds = listOf(first, second, third))
        }

        val rows = planRows(1)
        assertEquals(listOf(first, second, third), rows.map { it.id })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    // ---------------------------------------------------------------- сценарий 14

    @Test
    fun `scenario 14 - reorder workout keeps order without gaps`() = runTest {
        addPlanRow(weekday = 1, exerciseId = insertExercise("А"), position = 0, progress = "а")
        addPlanRow(weekday = 1, exerciseId = insertExercise("Б"), position = 1, progress = "б")
        addPlanRow(weekday = 1, exerciseId = insertExercise("В"), position = 2, progress = "в")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        val rows = workoutRows(workoutId)
        val (first, second, third) = rows
        repository.reorderWorkout(workoutId, listOf(second.id, third.id, first.id))

        val reordered = workoutRows(workoutId)
        assertEquals(listOf(second.id, third.id, first.id), reordered.map { it.id })
        assertEquals(listOf(0, 1, 2), reordered.map { it.position })
        assertEquals(listOf("б", "в", "а"), reordered.map { it.progress })

        val snapshot = workoutSnapshot(workoutId)!!
        assertEquals(
            listOf(second.id, third.id, first.id),
            snapshot.exercises.map { it.workoutExercise.id },
        )

        assertFailsWithType<IllegalArgumentException> {
            repository.reorderWorkout(workoutId, listOf(first.id))
        }
        assertFailsWithType<IllegalArgumentException> {
            repository.reorderWorkout(workoutId, listOf(first.id, first.id, second.id))
        }
        assertEquals(listOf(second.id, third.id, first.id), workoutRows(workoutId).map { it.id })
    }

    // ---------------------------------------------------------------- сценарий 15

    @Test
    fun `scenario 15 - adding preset to plan substitutes lastProgress`() = runTest {
        addPlanRow(weekday = 1, exerciseId = insertExercise("Существующее"), position = 0, progress = "0")

        val exercise = insertExercise("Новое", lastProgress = "45/45-45/45")
        val result = repository.addPlanExercise(weekday = 1, exerciseId = exercise, now = T1)

        val rowId = (result as AddResult.Added).rowId
        val row = db.planDao().getById(rowId)!!
        assertEquals(1, row.position)
        assertEquals("45/45-45/45", row.progress)
        assertEquals(T1, row.updatedAt)

        // Явно переданную пустую прогрессию не заменяем на lastProgress.
        val emptyProgressExercise = insertExercise("Пустая", lastProgress = "не использовать")
        val emptyRowId = (
            repository.addPlanExercise(
                weekday = 1,
                exerciseId = emptyProgressExercise,
                progress = "",
                now = T2,
            ) as AddResult.Added
            ).rowId
        assertEquals("", db.planDao().getById(emptyRowId)!!.progress)
        assertEquals(2, db.planDao().getById(emptyRowId)!!.position)

        // Явная прогрессия имеет приоритет.
        val explicitExercise = insertExercise("Явная", lastProgress = "пресет")
        val explicitRowId = (
            repository.addPlanExercise(
                weekday = 2,
                exerciseId = explicitExercise,
                progress = " 7-7 ",
                now = T2,
            ) as AddResult.Added
            ).rowId
        assertEquals(" 7-7 ", db.planDao().getById(explicitRowId)!!.progress)

        assertEquals(AddResult.ExerciseNotFound, repository.addPlanExercise(weekday = 1, exerciseId = 9999))
        assertEquals(4, countRows("plan_exercises"))
    }

    @Test
    fun `addPlanExercise rejects invalid weekday`() = runTest {
        val exercise = insertExercise("А")
        assertFailsWithType<IllegalArgumentException> {
            repository.addPlanExercise(weekday = 0, exerciseId = exercise)
        }
        assertFailsWithType<IllegalArgumentException> {
            repository.addPlanExercise(weekday = 8, exerciseId = exercise)
        }
        assertEquals(0, countRows("plan_exercises"))
    }

    // ---------------------------------------------------------------- сценарий 16

    @Test
    fun `scenario 16 - adding preset to workout substitutes lastProgress`() = runTest {
        addPlanRow(weekday = 1, exerciseId = insertExercise("Существующее"), position = 0, progress = "0")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        val exercise = insertExercise("Новое", lastProgress = "60-60-60")
        val result = repository.addWorkoutExercise(workoutId, exercise, now = T1)

        val rowId = (result as AddResult.Added).rowId
        val row = db.workoutDao().getExerciseById(rowId)!!
        assertEquals(1, row.position)
        assertEquals("60-60-60", row.progress)
        assertEquals(T1, row.updatedAt)

        val emptyExercise = insertExercise("Пустая", lastProgress = "не использовать")
        val emptyRowId = (
            repository.addWorkoutExercise(
                workoutId = workoutId,
                exerciseId = emptyExercise,
                progress = "",
                now = T2,
            ) as AddResult.Added
            ).rowId
        assertEquals("", db.workoutDao().getExerciseById(emptyRowId)!!.progress)

        assertEquals(AddResult.ExerciseNotFound, repository.addWorkoutExercise(workoutId, 9999))
        assertEquals(AddResult.WorkoutNotFound, repository.addWorkoutExercise(9999, exercise))
        assertEquals(3, countRows("workout_exercises"))
    }

    // ---------------------------------------------------------------- сценарий 17

    @Test
    fun `scenario 17 - finish and reopen change status and completedAt`() = runTest {
        addPlanRow(weekday = 1, exerciseId = insertExercise("А"), position = 0, progress = "5")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)

        repository.finishWorkout(workoutId, now = T1)

        var workout = db.workoutDao().getById(workoutId)!!
        assertEquals(WorkoutStatus.COMPLETED, workout.status)
        assertEquals(T1, workout.completedAt)
        assertEquals(T0, workout.createdAt)
        assertEquals(1, workoutRows(workoutId).size)

        repository.reopenWorkout(workoutId)

        workout = db.workoutDao().getById(workoutId)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.completedAt)
        assertEquals(T0, workout.createdAt)
    }

    @Test
    fun `editing completed workout is allowed and does not affect plan`() = runTest {
        addPlanRow(weekday = 1, exerciseId = insertExercise("А"), position = 0, progress = "план")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        val row = workoutRows(workoutId).single()
        repository.finishWorkout(workoutId, now = T1)

        repository.updateWorkoutExerciseProgress(row.id, "после завершения", now = T2)

        val workout = db.workoutDao().getById(workoutId)!!
        assertEquals(WorkoutStatus.COMPLETED, workout.status)
        assertEquals("после завершения", workoutRows(workoutId).single().progress)
        assertEquals("план", planRows(1).single().progress)
    }

    @Test
    fun `syncProgressToPlan returns WorkoutNotInProgress for completed workout and changes nothing`() = runTest {
        val exercise = insertExercise("Присед", lastProgress = "исходный пресет")
        val planRowId = addPlanRow(weekday = 1, exerciseId = exercise, position = 0, progress = "план")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        val workoutRow = workoutRows(workoutId).single()
        repository.updateWorkoutExerciseProgress(workoutRow.id, "факт", now = T1)
        repository.finishWorkout(workoutId, now = T1)

        val result = repository.syncProgressToPlan(workoutId, workoutRow.id, now = T2)

        assertEquals(SyncResult.WorkoutNotInProgress, result)
        assertEquals("план", db.planDao().getById(planRowId)!!.progress)
        assertEquals(T0, db.planDao().getById(planRowId)!!.updatedAt)
        assertEquals("исходный пресет", db.exerciseDao().getById(exercise)!!.lastProgress)
        assertEquals(T0, db.exerciseDao().getById(exercise)!!.updatedAt)
        assertEquals("факт", workoutRows(workoutId).single().progress)

        // После возврата в работу синхронизация снова доступна.
        repository.reopenWorkout(workoutId)
        assertEquals(
            SyncResult.UpdatedPlanAndPreset,
            repository.syncProgressToPlan(workoutId, workoutRow.id, now = T2),
        )
        assertEquals("факт", db.planDao().getById(planRowId)!!.progress)
        assertEquals("факт", db.exerciseDao().getById(exercise)!!.lastProgress)
    }

    @Test
    fun `syncProgressToPlan returns NotFound for unknown workout or exercise row`() = runTest {
        val first = insertExercise("А", lastProgress = "исходный")
        addPlanRow(weekday = 1, exerciseId = first, position = 0, progress = "план")
        val mondayId = repository.createWorkoutForDate(MONDAY, now = T0)
        val mondayRow = workoutRows(mondayId).single()

        val other = insertExercise("Б")
        val tuesdayId = repository.createWorkoutForDate(TUESDAY, now = T1)
        val tuesdayRow = (repository.addWorkoutExercise(tuesdayId, other, now = T1) as AddResult.Added).rowId

        assertEquals(
            SyncResult.NotFound,
            repository.syncProgressToPlan(mondayId, tuesdayRow, now = T2),
        )
        assertEquals(
            SyncResult.NotFound,
            repository.syncProgressToPlan(9999L, mondayRow.id, now = T2),
        )
        assertEquals(
            SyncResult.NotFound,
            repository.syncProgressToPlan(mondayId, 9999L, now = T2),
        )

        assertEquals("план", planRows(1).single().progress)
        assertEquals("исходный", db.exerciseDao().getById(first)!!.lastProgress)
    }

    // ---------------------------------------------------------------- сценарий 18

    @Test
    fun `scenario 18 - creating new exercise requires non blank name`() = runTest {
        val workoutId = db.workoutDao().insert(
            WorkoutEntity(
                dateEpochDay = TUESDAY.toEpochDay(),
                status = WorkoutStatus.IN_PROGRESS,
                createdAt = T0,
            ),
        )

        assertEquals(
            CreateExerciseResult.InvalidName,
            repository.createExerciseAndAddToPlan(1, "   ", "описание", "5", now = T1),
        )
        assertEquals(
            CreateExerciseResult.InvalidName,
            repository.createExerciseAndAddToPlan(1, "\n\t ", "описание", "5", now = T1),
        )
        assertEquals(
            CreateExerciseResult.InvalidName,
            repository.createExerciseAndAddToWorkout(workoutId, "  ", "описание", "5", now = T1),
        )

        assertEquals(0, countRows("exercises"))
        assertEquals(0, countRows("plan_exercises"))
        assertEquals(0, countRows("workout_exercises"))

        val created = repository.createExerciseAndAddToPlan(1, "  Жим  ", "", "5", now = T1)
        created as CreateExerciseResult.Created
        val preset = db.exerciseDao().getById(created.exerciseId)!!
        assertEquals("Жим", preset.name)
        assertEquals(0, planRows(1).single().position)
        assertEquals(1, planRows(1).size)
    }

    @Test
    fun `createExerciseAndAddToWorkout rejects unknown workout without creating preset`() = runTest {
        val result = repository.createExerciseAndAddToWorkout(9999L, "Жим", "описание", "5", now = T1)

        assertEquals(CreateExerciseResult.WorkoutNotFound, result)
        assertEquals(0, countRows("exercises"))
        assertEquals(0, countRows("workout_exercises"))
    }

    @Test
    fun `createExerciseAndAddToWorkout creates preset and appends workout row`() = runTest {
        val workoutId = repository.createWorkoutForDate(TUESDAY, now = T0)
        val existingExercise = insertExercise("Старое", lastProgress = "1")
        repository.addWorkoutExercise(workoutId, existingExercise, progress = "1", now = T0)

        val result = repository.createExerciseAndAddToWorkout(
            workoutId = workoutId,
            name = "  Новое  ",
            description = "Описание\n",
            progress = "7/20",
            now = T1,
        )
        result as CreateExerciseResult.Created

        val preset = db.exerciseDao().getById(result.exerciseId)!!
        assertEquals("Новое", preset.name)
        assertEquals("Описание\n", preset.description)
        assertEquals("7/20", preset.lastProgress)
        assertEquals(T1, preset.createdAt)
        assertEquals(T1, preset.updatedAt)

        val rows = workoutRows(workoutId)
        assertEquals(listOf(existingExercise, result.exerciseId), rows.map { it.exerciseId })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf("1", "7/20"), rows.map { it.progress })
        assertEquals(listOf(T0, T1), rows.map { it.updatedAt })
        assertEquals(2, countRows("exercises"))
        assertEquals(2, countRows("workout_exercises", "workoutId = ?", workoutId))
    }

    // ---------------------------------------------------------------- сценарий 19

    @Test
    fun `scenario 19 - progress and description are stored exactly as entered`() = runTest {
        val progress = "  5/20 - 5/20 /  60-60  "
        val description = "Первая строка\nВторая  строка\t/ тест - 1"

        val created = repository.createExerciseAndAddToPlan(
            weekday = 1,
            name = "  Упражнение  ",
            description = description,
            progress = progress,
            now = T0,
        ) as CreateExerciseResult.Created

        val preset = db.exerciseDao().getById(created.exerciseId)!!
        assertEquals("Упражнение", preset.name)
        assertEquals(description, preset.description)
        assertEquals(progress, preset.lastProgress)

        val planRow = db.planDao().getById(created.rowId)!!
        assertEquals(progress, planRow.progress)

        val workoutId = repository.createWorkoutForDate(MONDAY, now = T1)
        val workoutRow = workoutRows(workoutId).single()
        assertEquals(progress, workoutRow.progress)
        assertEquals(description, workoutSnapshot(workoutId)!!.exercises.single().exercise.description)

        val typedProgress = " 5/20-5/20-5/0 "
        repository.updateWorkoutExerciseProgress(workoutRow.id, typedProgress, now = T2)
        assertEquals(typedProgress, workoutRows(workoutId).single().progress)

        repository.updatePlanProgress(created.rowId, typedProgress, now = T2)
        assertEquals(typedProgress, db.planDao().getById(created.rowId)!!.progress)
        assertEquals(typedProgress, db.exerciseDao().getById(created.exerciseId)!!.lastProgress)
    }

    // ---------------------------------------------------------------- сценарий 20

    @Test
    fun `scenario 20 - workout date is unique`() = runTest {
        val firstId = db.workoutDao().insert(
            WorkoutEntity(
                dateEpochDay = MONDAY.toEpochDay(),
                status = WorkoutStatus.IN_PROGRESS,
                createdAt = T0,
            ),
        )

        val error = runCatching {
            db.workoutDao().insert(
                WorkoutEntity(
                    dateEpochDay = MONDAY.toEpochDay(),
                    status = WorkoutStatus.COMPLETED,
                    createdAt = T1,
                ),
            )
        }.exceptionOrNull()

        assertTrue(
            "Ожидалось нарушение UNIQUE(dateEpochDay), получено: $error",
            error is SQLiteConstraintException,
        )
        assertEquals(1, countRows("workouts", "dateEpochDay = ?", MONDAY.toEpochDay()))
        assertEquals(firstId, db.workoutDao().getByDate(MONDAY.toEpochDay())!!.id)
        assertEquals(1, countRows("workouts"))
    }

    // ------------------------------------------------------- удаление упражнения тренировки

    @Test
    fun `deleteWorkoutExercise removes only workout row and compacts positions`() = runTest {
        val first = insertExercise("А", lastProgress = "п1")
        val second = insertExercise("Б", lastProgress = "п2")
        val third = insertExercise("В", lastProgress = "п3")
        addPlanRow(weekday = 1, exerciseId = first, position = 0, progress = "план-а")
        addPlanRow(weekday = 1, exerciseId = second, position = 1, progress = "план-б")
        addPlanRow(weekday = 1, exerciseId = third, position = 2, progress = "план-в")
        val workoutId = repository.createWorkoutForDate(MONDAY, now = T0)
        val before = workoutRows(workoutId)

        repository.deleteWorkoutExercise(before[1].id)

        val after = workoutRows(workoutId)
        assertEquals(listOf(first, third), after.map { it.exerciseId })
        assertEquals(listOf(0, 1), after.map { it.position })
        assertEquals(listOf("план-а", "план-в"), after.map { it.progress })

        val plan = planRows(1)
        assertEquals(3, plan.size)
        assertEquals(listOf(0, 1, 2), plan.map { it.position })
        assertEquals(listOf("план-а", "план-б", "план-в"), plan.map { it.progress })
        assertEquals(3, countRows("exercises"))
        assertNotNull(db.exerciseDao().getById(second))
        assertNotNull(db.workoutDao().getById(workoutId))
        assertEquals(2, repository.observeWorkout(workoutId).first()!!.exercises.size)

        // Удаление несуществующей строки — no-op.
        repository.deleteWorkoutExercise(9999L)
        assertEquals(2, workoutRows(workoutId).size)
    }

    // -------------------------------------------------------------- наблюдаемые данные

    @Test
    fun `observeLog sorts workouts by date descending and exercises by position`() = runTest {
        val first = insertExercise("А")
        val second = insertExercise("Б")
        val olderId = db.workoutDao().insert(
            WorkoutEntity(
                dateEpochDay = MONDAY.toEpochDay(),
                status = WorkoutStatus.IN_PROGRESS,
                createdAt = T0,
            ),
        )
        val newerId = db.workoutDao().insert(
            WorkoutEntity(
                dateEpochDay = TUESDAY.toEpochDay(),
                status = WorkoutStatus.COMPLETED,
                createdAt = T1,
                completedAt = T2,
            ),
        )
        // Вставляем в обратном порядке, чтобы проверить сортировку в репозитории.
        db.workoutDao().insertExercise(
            WorkoutExerciseEntity(
                workoutId = olderId,
                exerciseId = second,
                position = 1,
                progress = "2",
                updatedAt = T0,
            ),
        )
        db.workoutDao().insertExercise(
            WorkoutExerciseEntity(
                workoutId = olderId,
                exerciseId = first,
                position = 0,
                progress = "1",
                updatedAt = T0,
            ),
        )

        val log = repository.observeLog().first()

        assertEquals(listOf(newerId, olderId), log.map { it.workout.id })
        assertEquals(listOf(first, second), log[1].exercises.map { it.workoutExercise.exerciseId })
        assertEquals(listOf(0, 1), log[1].exercises.map { it.workoutExercise.position })
        assertEquals(WorkoutStatus.COMPLETED, log[0].workout.status)
        assertTrue(log[0].exercises.isEmpty())
    }

    @Test
    fun `searchExercises returns all presets by recency and filters by substring`() = runTest {
        val first = insertExercise("Жим лёжа", now = T0)
        val second = insertExercise("Присед", now = T1)
        val third = insertExercise("Жим гантелей", now = T2)

        assertEquals(
            listOf(third, second, first),
            repository.searchExercises("").first().map { it.id },
        )
        assertEquals(
            listOf(third, first),
            repository.searchExercises("Жим").first().map { it.id },
        )
        assertEquals(
            listOf(third, first),
            repository.searchExercises("  Жим  ").first().map { it.id },
        )
        // Кириллица: регистр не важен.
        assertEquals(
            listOf(third, first),
            repository.searchExercises("жим").first().map { it.id },
        )
        assertTrue(repository.searchExercises("нет такого").first().isEmpty())
        // Символы LIKE не должны работать как шаблоны.
        assertTrue(repository.searchExercises("%").first().isEmpty())
    }

    // ---------------------------------------------------------------- помощники

    private suspend fun insertExercise(
        name: String,
        description: String = "",
        lastProgress: String = "",
        now: Long = T0,
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            description = description,
            lastProgress = lastProgress,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun addPlanRow(
        weekday: Int,
        exerciseId: Long,
        position: Int,
        progress: String = "",
        now: Long = T0,
    ): Long = db.planDao().insert(
        PlanExerciseEntity(
            weekday = weekday,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = now,
        ),
    )

    private suspend fun planRows(weekday: Int): List<PlanExerciseEntity> =
        db.planDao().getByWeekday(weekday)

    private suspend fun workoutRows(workoutId: Long): List<WorkoutExerciseEntity> =
        db.workoutDao().getExercises(workoutId)

    private suspend fun planSnapshot(): List<PlanExerciseWithExercise> =
        repository.observePlan().first()

    private suspend fun logSnapshot(): List<WorkoutWithExercises> =
        repository.observeLog().first()

    private suspend fun workoutSnapshot(id: Long): WorkoutWithExercises? =
        repository.observeWorkout(id).first()

    private fun countRows(table: String, whereClause: String? = null, vararg args: Any?): Int {
        val sql = if (whereClause == null) {
            "SELECT COUNT(*) FROM $table"
        } else {
            "SELECT COUNT(*) FROM $table WHERE $whereClause"
        }
        db.openHelper.writableDatabase.query(sql, args).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private inline fun <reified T : Throwable> assertFailsWithType(block: () -> Unit): T {
        val error = runCatching(block).exceptionOrNull()
        assertNotNull("Ожидалось исключение ${T::class.simpleName}", error)
        assertTrue("Ожидалось ${T::class.simpleName}, получено: $error", error is T)
        @Suppress("UNCHECKED_CAST")
        return error as T
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)
        val TUESDAY: LocalDate = LocalDate.of(2026, 11, 24)
        val SUNDAY: LocalDate = LocalDate.of(2026, 11, 29)

        const val T0: Long = 1_700_000_000_000L
        const val T1: Long = T0 + 60_000L
        const val T2: Long = T0 + 120_000L
    }
}
