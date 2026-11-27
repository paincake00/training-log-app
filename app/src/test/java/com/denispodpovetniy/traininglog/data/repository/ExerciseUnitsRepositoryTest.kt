package com.denispodpovetniy.traininglog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.util.ProgressCodec
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Единицы измерения пресета: хранение, подсказки и каскадное удаление позиции
 * из плана, тренировок и `lastProgress`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseUnitsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 11, 23)
    private val t0 = 1_000L

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

    @Test
    fun `create exercise stores units for plan row`() = runTest {
        val created = repository.createExerciseAndAddToPlan(
            weekday = 1,
            name = "Ходьба",
            description = "",
            progress = "20/4.6/6",
            units = listOf(" мин ", "км/ч", "наклон"),
            now = t0,
        ) as CreateExerciseResult.Created

        val exercise = db.exerciseDao().getById(created.exerciseId)!!
        assertEquals(listOf("мин", "км/ч", "наклон"), ProgressCodec.decodeUnits(exercise.units))
        val row = db.planDao().getById(created.rowId)!!
        assertEquals("20/4.6/6", row.progress)
    }

    @Test
    fun `create exercise drops invalid and duplicate units`() = runTest {
        val created = repository.createExerciseAndAddToPlan(
            weekday = 1,
            name = "Ходьба",
            description = "",
            units = listOf("кг", "КГ", " ", "a".repeat(21), "шаги"),
            now = t0,
        ) as CreateExerciseResult.Created

        val exercise = db.exerciseDao().getById(created.exerciseId)!!
        assertEquals(listOf("кг", "шаги"), ProgressCodec.decodeUnits(exercise.units))
    }

    @Test
    fun `update units normalizes names`() = runTest {
        val exerciseId = insertExercise(units = "кг")

        assertTrue(repository.updateExerciseUnits(exerciseId, listOf(" повторы ", "сек"), now = t0))

        val exercise = db.exerciseDao().getById(exerciseId)!!
        assertEquals(listOf("повторы", "сек"), ProgressCodec.decodeUnits(exercise.units))
    }

    @Test
    fun `update units rejects duplicate in any case`() = runTest {
        val exerciseId = insertExercise(units = "кг")

        assertFalse(repository.updateExerciseUnits(exerciseId, listOf("кг", "КГ"), now = t0))

        val exercise = db.exerciseDao().getById(exerciseId)!!
        assertEquals(listOf("кг"), ProgressCodec.decodeUnits(exercise.units))
    }

    @Test
    fun `update units rejects too long name`() = runTest {
        val exerciseId = insertExercise(units = "кг")

        assertFalse(repository.updateExerciseUnits(exerciseId, listOf("a".repeat(21)), now = t0))

        val exercise = db.exerciseDao().getById(exerciseId)!!
        assertEquals(listOf("кг"), ProgressCodec.decodeUnits(exercise.units))
    }

    @Test
    fun `adding unit does not change stored sets`() = runTest {
        val exerciseId = insertExercise(units = "кг", lastProgress = "20-20")

        assertTrue(repository.updateExerciseUnits(exerciseId, listOf("кг", "повторы"), now = t0))

        val exercise = db.exerciseDao().getById(exerciseId)!!
        assertEquals("20-20", exercise.lastProgress)
    }

    @Test
    fun `removing unit strips position everywhere`() = runTest {
        val exerciseId = insertExercise(
            units = "мин\nкм/ч\nнаклон",
            lastProgress = "20/4.6/6-30/4.5/5",
        )
        val planRowId = insertPlanRow(exerciseId, progress = "20/4.6/6-30/4.5/5")
        val workoutId = insertWorkout()
        val workoutRowId = insertWorkoutRow(workoutId, exerciseId, progress = "20/4.6/6-30/4.5/5")

        assertTrue(repository.removeExerciseUnit(exerciseId, index = 1, now = t0))

        val exercise = db.exerciseDao().getById(exerciseId)!!
        assertEquals(listOf("мин", "наклон"), ProgressCodec.decodeUnits(exercise.units))
        assertEquals("20/6-30/5", exercise.lastProgress)
        assertEquals("20/6-30/5", db.planDao().getById(planRowId)!!.progress)
        assertEquals("20/6-30/5", db.workoutDao().getExerciseById(workoutRowId)!!.progress)
    }

    @Test
    fun `removing last unit shrinks every set`() = runTest {
        val exerciseId = insertExercise(units = "повторы\nкг", lastProgress = "10/60-8/50")
        val planRowId = insertPlanRow(exerciseId, progress = "10/60-8/50")

        assertTrue(repository.removeExerciseUnit(exerciseId, index = 1, now = t0))

        assertEquals("10-8", db.exerciseDao().getById(exerciseId)!!.lastProgress)
        assertEquals("10-8", db.planDao().getById(planRowId)!!.progress)
    }

    @Test
    fun `removing unknown unit index changes nothing`() = runTest {
        val exerciseId = insertExercise(units = "кг", lastProgress = "60")

        assertFalse(repository.removeExerciseUnit(exerciseId, index = 3, now = t0))

        assertEquals(listOf("кг"), ProgressCodec.decodeUnits(db.exerciseDao().getById(exerciseId)!!.units))
    }

    @Test
    fun `sync copies sets and keeps units`() = runTest {
        val exerciseId = insertExercise(units = "повторы\nкг")
        val planRowId = insertPlanRow(exerciseId, progress = "10/60")
        val workoutId = insertWorkout()
        val workoutRowId = insertWorkoutRow(workoutId, exerciseId, progress = "12/70")

        val result = repository.syncProgressToPlan(workoutId, workoutRowId, now = t0)

        assertEquals(SyncResult.UpdatedPlanAndPreset, result)
        assertEquals("12/70", db.planDao().getById(planRowId)!!.progress)
        assertEquals("12/70", db.exerciseDao().getById(exerciseId)!!.lastProgress)
        assertEquals(listOf("повторы", "кг"), ProgressCodec.decodeUnits(db.exerciseDao().getById(exerciseId)!!.units))
    }

    @Test
    fun `unit suggestions dedupe case insensitively and prefer recent`() = runTest {
        insertExercise(units = "кг\nшаги", updatedAt = 1)
        insertExercise(units = "КГ\nсек", updatedAt = 2)

        val suggestions = repository.observeUnitSuggestions().first()

        assertEquals(listOf("КГ", "сек", "шаги"), suggestions)
    }

    private suspend fun insertExercise(
        units: String = "",
        lastProgress: String = "",
        updatedAt: Long = t0,
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = "Упражнение $updatedAt",
            description = "",
            lastProgress = lastProgress,
            units = units,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        ),
    )

    private suspend fun insertPlanRow(exerciseId: Long, progress: String): Long =
        db.planDao().insert(
            PlanExerciseEntity(
                weekday = 1,
                exerciseId = exerciseId,
                position = 0,
                progress = progress,
                updatedAt = t0,
            ),
        )

    private suspend fun insertWorkout(): Long = db.workoutDao().insert(
        WorkoutEntity(
            dateEpochDay = monday.toEpochDay(),
            status = WorkoutStatus.IN_PROGRESS,
            createdAt = t0,
        ),
    )

    private suspend fun insertWorkoutRow(
        workoutId: Long,
        exerciseId: Long,
        progress: String,
    ): Long = db.workoutDao().insertExercise(
        WorkoutExerciseEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            position = 0,
            progress = progress,
            updatedAt = t0,
        ),
    )
}
