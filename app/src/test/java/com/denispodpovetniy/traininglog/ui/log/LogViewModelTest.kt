package com.denispodpovetniy.traininglog.ui.log

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Room (in-memory) тесты [LogViewModel].
 *
 * «Сегодня» всегда вторник 24.11.2026 (ISO 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository
    private lateinit var viewModel: LogViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executor { it.run() })
            .setTransactionExecutor(Executor { it.run() })
            .build()
        repository = TrainingRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `start creates today workout when absent and emits its id`() = runTest {
        val exerciseId = insertExercise("Жим", lastProgress = "5/20")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0, progress = "5/20")
        startViewModel()

        assertNull(viewModel.uiState.value.todayWorkout)
        assertEquals(0, countRows("workouts"))

        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        val workoutId = viewModel.uiState.value.openWorkoutId
        assertNotNull(workoutId)
        assertEquals(1, countRows("workouts"))
        assertEquals(workoutId, viewModel.uiState.value.todayWorkout?.workoutId)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.todayWorkout?.status)

        val workout = db.workoutDao().getById(workoutId!!)!!
        assertEquals(TUESDAY.toEpochDay(), workout.dateEpochDay)
        assertEquals(
            listOf(exerciseId),
            db.workoutDao().getExercises(workoutId).map { it.exerciseId },
        )
        // Фактическая прогрессия начинается пустой: плановое значение — подсказка.
        assertEquals(
            listOf(""),
            db.workoutDao().getExercises(workoutId).map { it.progress },
        )
    }

    @Test
    fun `existing in progress workout is opened without duplicate`() = runTest {
        val existingId = insertWorkout(status = WorkoutStatus.IN_PROGRESS)
        startViewModel()

        assertEquals(existingId, viewModel.uiState.value.todayWorkout?.workoutId)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.todayWorkout?.status)

        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        assertEquals(existingId, viewModel.uiState.value.openWorkoutId)
        assertEquals(1, countRows("workouts"))
    }

    @Test
    fun `existing completed workout is opened`() = runTest {
        val existingId = insertWorkout(status = WorkoutStatus.COMPLETED, completedAt = T0)
        startViewModel()

        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.todayWorkout?.status)

        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        assertEquals(existingId, viewModel.uiState.value.openWorkoutId)
        assertEquals(1, countRows("workouts"))
    }

    @Test
    fun `today workout status is exposed in log state and updates on finish`() = runTest {
        val workoutId = insertWorkout(status = WorkoutStatus.IN_PROGRESS)
        startViewModel()

        val card = viewModel.uiState.value.workouts.single()
        assertEquals(workoutId, card.workoutId)
        assertEquals(TUESDAY, card.date)
        assertTrue(card.isToday)
        assertEquals(WorkoutStatus.IN_PROGRESS, card.status)

        repository.finishWorkout(workoutId, now = T0)
        advanceUntilIdle()

        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.todayWorkout?.status)
        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.workouts.single().status)
    }

    @Test
    fun `double tap creates only one workout and open event is one shot`() = runTest {
        startViewModel()

        // Оба вызова происходят до завершения первой операции:
        // второй должен быть отброшен защитой mutationInFlight.
        viewModel.startOrOpenTodayWorkout()
        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.openWorkoutId)
        assertEquals(1, countRows("workouts"))

        viewModel.consumeOpenWorkout()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.openWorkoutId)
    }

    @Test
    fun `save failure is exposed and consumed`() = runTest {
        startViewModel()

        // Закрытая база заставляет создание тренировки упасть;
        // ViewModel должен показать snackbar-сообщение, а не уронить приложение.
        db.close()
        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        assertEquals(LogMessage.SaveFailed, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.openWorkoutId)

        viewModel.consumeMessage()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    private fun TestScope.startViewModel() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        viewModel = LogViewModel(repository, today = { TUESDAY })
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
    }

    private suspend fun insertExercise(
        name: String,
        lastProgress: String = "",
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            lastProgress = lastProgress,
            createdAt = T0,
            updatedAt = T0,
        ),
    )

    private suspend fun addPlanRow(
        weekday: Int,
        exerciseId: Long,
        position: Int,
        progress: String = "",
    ): Long = db.planDao().insert(
        PlanExerciseEntity(
            weekday = weekday,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = T0,
        ),
    )

    private suspend fun insertWorkout(
        date: LocalDate = TUESDAY,
        status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
        completedAt: Long? = null,
    ): Long = db.workoutDao().insert(
        WorkoutEntity(
            dateEpochDay = date.toEpochDay(),
            status = status,
            createdAt = T0,
            completedAt = completedAt,
        ),
    )

    private fun countRows(table: String): Int =
        db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM $table")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private companion object {
        val TUESDAY: LocalDate = LocalDate.of(2026, 11, 24)
        const val T0 = 1_000L
    }
}
