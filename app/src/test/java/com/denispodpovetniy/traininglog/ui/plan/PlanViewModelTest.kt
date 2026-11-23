package com.denispodpovetniy.traininglog.ui.plan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository
    private lateinit var viewModel: PlanViewModel

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
    fun `duplicate add keeps picker open and emits duplicate message`() = runTest {
        val exerciseId = insertExercise("Жим")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0)
        observeState()

        viewModel.openPicker(2)
        advanceUntilIdle()
        viewModel.addExisting(exerciseId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pickerWeekday)
        assertEquals(PlanMessage.DuplicateExercise, state.message)
        assertEquals(1, planRows(2).size)
    }

    @Test
    fun `successful add closes picker and uses preset last progress`() = runTest {
        val exerciseId = insertExercise("Жим", lastProgress = "5/20-5/20")
        observeState()

        viewModel.openPicker(2)
        advanceUntilIdle()
        viewModel.addExisting(exerciseId)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pickerWeekday)
        val rows = planRows(2)
        assertEquals(1, rows.size)
        assertEquals("5/20-5/20", rows.single().progress)
    }

    @Test
    fun `invalid create keeps dialog open then valid create appends row`() = runTest {
        observeState()
        viewModel.openPicker(1)
        viewModel.openCreateFromPicker()
        advanceUntilIdle()

        assertEquals(PlanDialog.Create(1), viewModel.uiState.value.dialog)
        assertNull(viewModel.uiState.value.pickerWeekday)

        viewModel.saveNewExercise(name = " ", description = "", progress = "")
        advanceUntilIdle()

        assertEquals(PlanDialog.Create(1), viewModel.uiState.value.dialog)
        assertEquals(PlanMessage.SaveFailed, viewModel.uiState.value.message)
        assertEquals(0, countRows("exercises"))

        viewModel.saveNewExercise(name = "Присед", description = "Ноги", progress = "3/10")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(1, countRows("exercises"))
        assertEquals(1, planRows(1).size)
        assertEquals("3/10", planRows(1).single().progress)
    }

    @Test
    fun `double create tap creates only one preset and one plan row`() = runTest {
        observeState()
        viewModel.openPicker(1)
        viewModel.openCreateFromPicker()
        advanceUntilIdle()

        // Оба вызова происходят до запуска первой операции:
        // второй должен быть отброшен защитой mutationInFlight.
        viewModel.saveNewExercise(name = "Присед", description = "", progress = "3/10")
        viewModel.saveNewExercise(name = "Присед", description = "", progress = "3/10")
        advanceUntilIdle()

        assertEquals(1, countRows("exercises"))
        assertEquals(1, planRows(1).size)
    }

    @Test
    fun `move persists expected order and contiguous positions`() = runTest {
        val first = insertExercise("Первое")
        val second = insertExercise("Второе")
        val third = insertExercise("Третье")
        addPlanRow(weekday = 2, exerciseId = first, position = 0)
        addPlanRow(weekday = 2, exerciseId = second, position = 1)
        addPlanRow(weekday = 2, exerciseId = third, position = 2)
        observeState()

        val day = viewModel.uiState.value.days.first { it.weekday == 2 }
        viewModel.move(day.exercises.first(), delta = 1)
        advanceUntilIdle()

        val rows = planRows(2)
        assertEquals(listOf(second, first, third), rows.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    private fun TestScope.observeState() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        viewModel = PlanViewModel(repository, today = { TUESDAY })
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
    ): Long = db.planDao().insert(
        PlanExerciseEntity(
            weekday = weekday,
            exerciseId = exerciseId,
            position = position,
            progress = "",
            updatedAt = T0,
        ),
    )

    private suspend fun planRows(weekday: Int): List<PlanExerciseEntity> =
        db.planDao().getByWeekday(weekday)

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
