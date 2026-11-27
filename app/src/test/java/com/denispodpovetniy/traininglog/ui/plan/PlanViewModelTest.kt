package com.denispodpovetniy.traininglog.ui.plan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.repository.PlanImportSummary
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import com.denispodpovetniy.traininglog.util.PlanFile
import com.denispodpovetniy.traininglog.util.PlanFileCodec
import com.denispodpovetniy.traininglog.util.PlanFileException
import com.denispodpovetniy.traininglog.util.PlanFileExercise
import com.denispodpovetniy.traininglog.util.PlanFileRow
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

        viewModel.saveNewExercise(name = " ", description = "", units = emptyList(), progress = "")
        advanceUntilIdle()

        assertEquals(PlanDialog.Create(1), viewModel.uiState.value.dialog)
        assertEquals(PlanMessage.SaveFailed, viewModel.uiState.value.message)
        assertEquals(0, countRows("exercises"))

        viewModel.saveNewExercise(name = "Присед", description = "Ноги", units = emptyList(), progress = "3/10")
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
        viewModel.saveNewExercise(name = "Присед", description = "", units = emptyList(), progress = "3/10")
        viewModel.saveNewExercise(name = "Присед", description = "", units = emptyList(), progress = "3/10")
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

    @Test
    fun `start or open today workout creates once and returns same id`() = runTest {
        val exerciseId = insertExercise("Жим", lastProgress = "5/20")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0, progress = "5/20")
        observeState()

        assertNull(viewModel.uiState.value.todayWorkout)

        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        val workoutId = viewModel.uiState.value.openWorkoutId
        assertNotNull(workoutId)
        assertEquals(1, countRows("workouts"))
        assertEquals(workoutId, viewModel.uiState.value.todayWorkout?.workoutId)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.todayWorkout?.status)
        // Упражнения скопированы из плана вторника, фактическая прогрессия пустая.
        val rows = workoutRows(workoutId!!)
        assertEquals(listOf(exerciseId), rows.map { it.exerciseId })
        assertEquals(listOf(""), rows.map { it.progress })

        viewModel.consumeOpenWorkout()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.openWorkoutId)

        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()

        assertEquals(workoutId, viewModel.uiState.value.openWorkoutId)
        assertEquals(1, countRows("workouts"))
        assertEquals(1, workoutRows(workoutId).size)
    }

    @Test
    fun `today workout reflects status after finish`() = runTest {
        observeState()
        viewModel.startOrOpenTodayWorkout()
        advanceUntilIdle()
        val workoutId = requireNotNull(viewModel.uiState.value.openWorkoutId)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.todayWorkout?.status)

        repository.finishWorkout(workoutId, now = T0)
        advanceUntilIdle()

        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.todayWorkout?.status)
        assertEquals(workoutId, viewModel.uiState.value.todayWorkout?.workoutId)
    }

    // ------------------------------------------------------- единицы измерения

    @Test
    fun `rename unit updates preset and keeps plan progress`() = runTest {
        val exerciseId = insertExercise("Ходьба", lastProgress = "20/4,6", units = "мин\nкм/ч")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0, progress = "20/4,6-30/4,5")
        observeState()

        viewModel.renameUnit(exerciseId, index = 1, name = "скорость")
        advanceUntilIdle()

        assertEquals("мин\nскорость", db.exerciseDao().getById(exerciseId)!!.units)
        assertEquals("20/4,6-30/4,5", planRows(2).single().progress)
        assertEquals(
            listOf("мин", "скорость"),
            viewModel.uiState.value.days.first { it.weekday == 2 }.exercises.single().units,
        )
    }

    @Test
    fun `add unit appends it to preset and suggestions without touching progress`() = runTest {
        val exerciseId = insertExercise("Ходьба", units = "мин")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0, progress = "20-30")
        observeState()

        viewModel.addUnit(exerciseId, "наклон")
        advanceUntilIdle()

        assertEquals("мин\nнаклон", db.exerciseDao().getById(exerciseId)!!.units)
        assertEquals("20-30", planRows(2).single().progress)
        assertTrue(viewModel.uiState.value.unitSuggestions.contains("наклон"))
    }

    @Test
    fun `remove unit strips values from plan rows and last progress`() = runTest {
        val exerciseId = insertExercise("Ходьба", lastProgress = "20/4,6", units = "мин\nкм/ч")
        addPlanRow(weekday = 2, exerciseId = exerciseId, position = 0, progress = "20/4,6-30/4,5")
        observeState()

        viewModel.removeUnit(exerciseId, index = 1)
        advanceUntilIdle()

        assertEquals("20-30", planRows(2).single().progress)
        assertEquals("20", db.exerciseDao().getById(exerciseId)!!.lastProgress)
        assertEquals("мин", db.exerciseDao().getById(exerciseId)!!.units)
        assertEquals(PlanMessage.UnitRemoved, viewModel.uiState.value.message)
    }

    // --------------------------------------------------- экспорт и импорт плана

    @Test
    fun `export request carries file name and preset snapshot`() = runTest {
        val exerciseId = insertExercise("Присед", lastProgress = "5/20", units = "повторы\nкг")
        addPlanRow(weekday = 1, exerciseId = exerciseId, position = 0, progress = "5/20")
        observeState()

        viewModel.requestExport(appVersion = "1.0.0")
        advanceUntilIdle()

        val request = requireNotNull(viewModel.uiState.value.exportRequest)
        assertEquals("training-log-plan-2026-11-24.json", request.fileName)
        val file = PlanFileCodec.decode(request.content)
        assertEquals("1.0.0", file.appVersion)
        assertEquals(listOf("Присед"), file.exercises.map { it.name })
        assertEquals(listOf("повторы", "кг"), file.exercises.single().units)
        assertEquals(listOf(1 to "присед"), file.plan.map { it.weekday to it.exerciseKey })

        viewModel.onExportFinished(written = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.exportRequest)
        assertEquals(PlanMessage.Exported, viewModel.uiState.value.message)
    }

    @Test
    fun `cancelled export clears request without message`() = runTest {
        insertExercise("Присед")
        observeState()

        viewModel.requestExport(appVersion = "1.0.0")
        advanceUntilIdle()
        viewModel.onExportFinished(written = false)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.exportRequest)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `invalid file shows import error and no preview`() = runTest {
        observeState()

        viewModel.importFromFile("{oops")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(PlanMessage.ImportFailed(PlanFileException.Reason.CORRUPT), viewModel.uiState.value.message)
    }

    @Test
    fun `import shows preview then replaces plan after confirmation`() = runTest {
        val old = insertExercise("Старое", lastProgress = "старое")
        addPlanRow(weekday = 7, exerciseId = old, position = 0, progress = "старое")
        observeState()

        val content = PlanFileCodec.encode(
            PlanFile(
                formatVersion = PlanFileCodec.FORMAT_VERSION,
                exportedAt = "2026-11-27T18:40:00Z",
                appVersion = "1.0.0",
                exercises = listOf(
                    PlanFileExercise(
                        key = "присед",
                        name = "Присед",
                        description = "Спина прямая",
                        units = listOf("повторы"),
                        lastProgress = "5/20-5/20",
                    ),
                ),
                plan = listOf(
                    PlanFileRow(weekday = 2, position = 0, exerciseKey = "присед", progress = "5/20-5/20"),
                ),
            ),
        )

        viewModel.importFromFile(content)
        advanceUntilIdle()

        assertEquals(
            PlanDialog.ConfirmImport(PlanImportSummary(addedExercises = 1, updatedExercises = 0, planRows = 1)),
            viewModel.uiState.value.dialog,
        )
        // До подтверждения ничего не меняется.
        assertEquals(1, planRows(7).size)
        assertEquals(1, countRows("exercises"))

        viewModel.confirmImport()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(
            PlanMessage.Imported(PlanImportSummary(addedExercises = 1, updatedExercises = 0, planRows = 1)),
            viewModel.uiState.value.message,
        )
        assertTrue(planRows(7).isEmpty())
        val rows = planRows(2)
        assertEquals(listOf("5/20-5/20"), rows.map { it.progress })
        // Пресет не удалён, новый добавлен.
        assertEquals(setOf("Старое", "Присед"), db.exerciseDao().getAll().map { it.name }.toSet())
    }

    @Test
    fun `dismissing preview keeps plan and data unchanged`() = runTest {
        val old = insertExercise("Старое", lastProgress = "старое")
        addPlanRow(weekday = 7, exerciseId = old, position = 0, progress = "старое")
        observeState()

        viewModel.importFromFile(
            PlanFileCodec.encode(
                PlanFile(
                    formatVersion = PlanFileCodec.FORMAT_VERSION,
                    exportedAt = "",
                    appVersion = "",
                    exercises = emptyList(),
                    plan = emptyList(),
                ),
            ),
        )
        advanceUntilIdle()
        viewModel.dismissDialog()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(1, planRows(7).size)
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
        units: String = "",
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            lastProgress = lastProgress,
            units = units,
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

    private suspend fun planRows(weekday: Int): List<PlanExerciseEntity> =
        db.planDao().getByWeekday(weekday)

    private suspend fun workoutRows(workoutId: Long): List<WorkoutExerciseEntity> =
        db.workoutDao().getExercises(workoutId)

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
