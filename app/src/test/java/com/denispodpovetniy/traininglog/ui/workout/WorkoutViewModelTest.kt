package com.denispodpovetniy.traininglog.ui.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * Robolectric + Room (in-memory) тесты [WorkoutViewModel].
 *
 * Тренировка всегда на вторник 24.11.2026 (ISO 2). Время виртуальное:
 * debounce прогрессии 300 мс проверяется через `advanceTimeBy`, реальных пауз нет.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository
    private lateinit var viewModel: WorkoutViewModel

    /** Значение, которое вернёт инъектированный `now` лямбда ViewModel. */
    private var currentNow: Long = T0

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

    // ------------------------------------------------------- загрузка состояния

    @Test
    fun `load exposes date status ordered exercises and plan hints of workout weekday only`() = runTest {
        val squat = insertExercise("Присед", description = "Ноги")
        val bench = insertExercise("Жим")
        val pull = insertExercise("Тяга")

        // Вторник (ISO 2) — план тренировки.
        addPlanRow(weekday = 2, exerciseId = squat, position = 0, progress = "5/20-5/20")
        addPlanRow(weekday = 2, exerciseId = bench, position = 1, progress = "   ")
        addPlanRow(weekday = 2, exerciseId = pull, position = 2, progress = "10-10")
        // Другие дни недели не должны попадать в подсказку.
        addPlanRow(weekday = 3, exerciseId = squat, position = 0, progress = "999")
        addPlanRow(weekday = 1, exerciseId = bench, position = 0, progress = "777")

        val workoutId = insertWorkout()
        // Вставка в обратном порядке — порядок задаёт position, а не id вставки.
        insertWorkoutRow(workoutId, pull, position = 2, progress = "10-10-10")
        insertWorkoutRow(workoutId, squat, position = 0, progress = "5/20-5/20-5/20")
        insertWorkoutRow(workoutId, bench, position = 1, progress = "")

        startViewModel(workoutId)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertFalse(state.notFound)
        assertEquals(workoutId, state.workoutId)
        assertEquals(TUESDAY, state.date)
        assertEquals(WorkoutStatus.IN_PROGRESS, state.status)

        assertEquals(listOf("Присед", "Жим", "Тяга"), state.exercises.map { it.name })
        assertEquals(listOf(0, 1, 2), state.exercises.map { it.position })
        assertEquals(listOf("Ноги", "", ""), state.exercises.map { it.description })
        // Пустая плановая прогрессия не показывается, чужие дни недели игнорируются.
        assertEquals(listOf("5/20-5/20", "", "10-10"), state.exercises.map { it.planProgress })
        assertEquals(
            listOf("5/20-5/20-5/20", "", "10-10-10"),
            state.exercises.map { it.progress },
        )
    }

    // ------------------------------------------------------- автосохранение

    @Test
    fun `progress change is visible immediately and persisted verbatim after debounce`() = runTest {
        val squat = insertExercise("Присед")
        val bench = insertExercise("Жим")
        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "старое")
        val benchRow = insertWorkoutRow(workoutId, bench, position = 1, progress = "второе")

        startViewModel(workoutId)

        val typed = "  5/20-5/20 (лёгкий) \t"
        viewModel.onProgressChange(squatRow, typed)
        runCurrent()

        // UI видит ввод сразу, БД — ещё нет.
        assertEquals(typed, progressOf(squatRow))
        assertEquals("старое", db.workoutDao().getExerciseById(squatRow)!!.progress)

        viewModel.onProgressChange(benchRow, "60-60")
        runCurrent()

        advanceTimeBy(299)
        runCurrent()
        assertEquals("старое", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals("второе", db.workoutDao().getExerciseById(benchRow)!!.progress)

        advanceTimeBy(1)
        runCurrent()

        val savedSquat = db.workoutDao().getExerciseById(squatRow)!!
        val savedBench = db.workoutDao().getExerciseById(benchRow)!!
        assertEquals(typed, savedSquat.progress)
        assertEquals("60-60", savedBench.progress)
        assertEquals(T0, savedSquat.updatedAt)
        assertEquals(T0, savedBench.updatedAt)
        // Ввод одной строки не попал в другую.
        assertEquals(typed, progressOf(squatRow))
        assertEquals("60-60", progressOf(benchRow))
    }

    @Test
    fun `flushProgress persists pending text immediately and skips the debounce`() = runTest {
        val squat = insertExercise("Присед")
        val bench = insertExercise("Жим")
        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "старое")
        val benchRow = insertWorkoutRow(workoutId, bench, position = 1, progress = "второе")

        startViewModel(workoutId)

        viewModel.onProgressChange(squatRow, " 42 ")
        runCurrent()
        viewModel.flushProgress()

        assertEquals(" 42 ", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals(T0, db.workoutDao().getExerciseById(squatRow)!!.updatedAt)
        // Соседняя строка не затронута.
        assertEquals("второе", db.workoutDao().getExerciseById(benchRow)!!.progress)

        // Отложенный debounce после flush ничего не перезаписывает.
        advanceUntilIdle()
        assertEquals(" 42 ", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals(T0, db.workoutDao().getExerciseById(squatRow)!!.updatedAt)
    }

    // ------------------------------------------------------- добавление упражнений

    @Test
    fun `addExisting closes picker on success and keeps it open on duplicate or unknown preset`() = runTest {
        val preset = insertExercise("Жим", lastProgress = "60-60-60")
        val workoutId = insertWorkout()
        startViewModel(workoutId)

        viewModel.openPicker()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pickerVisible)

        viewModel.addExisting(preset)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.pickerVisible)
        val added = db.workoutDao().getExercises(workoutId).single()
        assertEquals(preset, added.exerciseId)
        assertEquals("60-60-60", added.progress)
        assertEquals(0, added.position)

        viewModel.openPicker()
        advanceUntilIdle()
        viewModel.addExisting(preset)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pickerVisible)
        assertEquals(WorkoutMessage.DuplicateExercise, viewModel.uiState.value.message)
        assertEquals(1, db.workoutDao().getExercises(workoutId).size)

        viewModel.consumeMessage()
        advanceUntilIdle()
        viewModel.addExisting(UNKNOWN_ID)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pickerVisible)
        assertEquals(WorkoutMessage.ExerciseNotFound, viewModel.uiState.value.message)
        assertEquals(1, db.workoutDao().getExercises(workoutId).size)
    }

    @Test
    fun `saveNewExercise rejects blank name then creates preset and workout row with entered progress`() = runTest {
        val workoutId = insertWorkout()
        startViewModel(workoutId)

        viewModel.openPicker()
        advanceUntilIdle()
        viewModel.openCreateFromPicker()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.pickerVisible)
        assertEquals(WorkoutDialog.Create, viewModel.uiState.value.dialog)

        viewModel.saveNewExercise(name = "   ", description = "", progress = "")
        advanceUntilIdle()

        assertEquals(WorkoutDialog.Create, viewModel.uiState.value.dialog)
        assertEquals(WorkoutMessage.SaveFailed, viewModel.uiState.value.message)
        assertEquals(0, countRows("exercises"))
        assertEquals(0, countRows("workout_exercises"))

        viewModel.consumeMessage()
        viewModel.saveNewExercise(name = "  Присед  ", description = "Ноги", progress = " 3/10 ")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        val preset = db.exerciseDao().observeAll().first().single()
        assertEquals("Присед", preset.name)
        assertEquals("Ноги", preset.description)
        assertEquals(" 3/10 ", preset.lastProgress)

        val row = db.workoutDao().getExercises(workoutId).single()
        assertEquals(preset.id, row.exerciseId)
        assertEquals(" 3/10 ", row.progress)
        assertEquals(0, row.position)
    }

    @Test
    fun `openCreateFromPicker requires visible picker`() = runTest {
        val workoutId = insertWorkout()
        startViewModel(workoutId)

        viewModel.openCreateFromPicker()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
    }

    // ------------------------------------------------------- reload

    @Test
    fun `reload flushes pending progress into plan row and preset of workout weekday`() = runTest {
        val squat = insertExercise("Присед", lastProgress = "пресет-старое")
        val bench = insertExercise("Жим", lastProgress = "жим-старое")
        addPlanRow(weekday = 2, exerciseId = squat, position = 0, progress = "план-старое")
        addPlanRow(weekday = 2, exerciseId = bench, position = 1, progress = "жим-план")
        addPlanRow(weekday = 4, exerciseId = squat, position = 0, progress = "четверг-план")

        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "старое")
        startViewModel(workoutId)

        viewModel.onProgressChange(squatRow, " 5/20-5/20 ")
        runCurrent()
        currentNow = T_SYNC

        viewModel.reload(viewModel.uiState.value.exercises.single())
        advanceUntilIdle()

        // Отложенный ввод сохранён до синхронизации.
        assertEquals(" 5/20-5/20 ", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals(
            " 5/20-5/20 ",
            db.planDao().findByWeekdayAndExercise(2, squat)!!.progress,
        )
        assertEquals(" 5/20-5/20 ", db.exerciseDao().getById(squat)!!.lastProgress)
        // Другие дни и соседние упражнения не тронуты.
        assertEquals("жим-план", db.planDao().findByWeekdayAndExercise(2, bench)!!.progress)
        assertEquals("четверг-план", db.planDao().findByWeekdayAndExercise(4, squat)!!.progress)
        assertEquals("жим-старое", db.exerciseDao().getById(bench)!!.lastProgress)
        assertEquals(T_SYNC, db.planDao().findByWeekdayAndExercise(2, squat)!!.updatedAt)
        assertEquals(WorkoutMessage.SyncedPlanAndPreset, viewModel.uiState.value.message)
    }

    @Test
    fun `reload without plan row updates only preset last progress`() = runTest {
        val squat = insertExercise("Присед", lastProgress = "пресет-старое")
        val bench = insertExercise("Жим")
        addPlanRow(weekday = 2, exerciseId = bench, position = 0, progress = "чужое")

        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "10-10")
        startViewModel(workoutId)

        viewModel.reload(viewModel.uiState.value.exercises.single())
        advanceUntilIdle()

        assertEquals("10-10", db.exerciseDao().getById(squat)!!.lastProgress)
        assertNull(db.planDao().findByWeekdayAndExercise(2, squat))
        assertEquals("чужое", db.planDao().findByWeekdayAndExercise(2, bench)!!.progress)
        assertEquals(WorkoutMessage.SyncedPresetOnly, viewModel.uiState.value.message)
    }

    @Test
    fun `reload on completed workout changes nothing and emits no message`() = runTest {
        val squat = insertExercise("Присед", lastProgress = "пресет")
        addPlanRow(weekday = 2, exerciseId = squat, position = 0, progress = "план")
        val workoutId = insertWorkout(status = WorkoutStatus.COMPLETED, completedAt = T_OLD)
        insertWorkoutRow(workoutId, squat, position = 0, progress = "факт")
        startViewModel(workoutId)

        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.status)
        viewModel.reload(viewModel.uiState.value.exercises.single())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertEquals("план", db.planDao().findByWeekdayAndExercise(2, squat)!!.progress)
        assertEquals("пресет", db.exerciseDao().getById(squat)!!.lastProgress)
        assertEquals(WorkoutStatus.COMPLETED, db.workoutDao().getById(workoutId)!!.status)
    }

    // ------------------------------------------------------- статус и выход

    @Test
    fun `finish flushes pending progress and completes workout`() = runTest {
        val squat = insertExercise("Присед")
        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "старое")
        startViewModel(workoutId)

        viewModel.onProgressChange(squatRow, " 5/20 ")
        runCurrent()
        currentNow = T_FINISH

        viewModel.finish()
        advanceUntilIdle()

        val workout = db.workoutDao().getById(workoutId)!!
        assertEquals(WorkoutStatus.COMPLETED, workout.status)
        assertEquals(T_FINISH, workout.completedAt)
        assertEquals(" 5/20 ", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals(WorkoutExit.Finished, viewModel.uiState.value.exit)
        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.status)
    }

    @Test
    fun `reopen returns completed workout to in progress with null completedAt`() = runTest {
        val squat = insertExercise("Присед")
        val workoutId = insertWorkout(status = WorkoutStatus.COMPLETED, completedAt = T_FINISH)
        insertWorkoutRow(workoutId, squat, position = 0, progress = "5/20")
        startViewModel(workoutId)

        assertEquals(WorkoutStatus.COMPLETED, viewModel.uiState.value.status)
        viewModel.reopen()
        advanceUntilIdle()

        val workout = db.workoutDao().getById(workoutId)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.completedAt)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.status)
    }

    @Test
    fun `requestBack flushes progress emits back and keeps draft in progress`() = runTest {
        val squat = insertExercise("Присед")
        val workoutId = insertWorkout()
        val squatRow = insertWorkoutRow(workoutId, squat, position = 0, progress = "старое")
        startViewModel(workoutId)

        viewModel.onProgressChange(squatRow, "черновик")
        runCurrent()

        viewModel.requestBack()
        advanceUntilIdle()

        assertEquals("черновик", db.workoutDao().getExerciseById(squatRow)!!.progress)
        assertEquals(WorkoutStatus.IN_PROGRESS, db.workoutDao().getById(workoutId)!!.status)
        assertEquals(WorkoutExit.Back, viewModel.uiState.value.exit)
        assertEquals(WorkoutStatus.IN_PROGRESS, viewModel.uiState.value.status)
    }

    @Test
    fun `deleteWorkout requires confirmation and removes only workout rows`() = runTest {
        val squat = insertExercise("Присед")
        addPlanRow(weekday = 2, exerciseId = squat, position = 0, progress = "план")
        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, squat, position = 0, progress = "факт")
        startViewModel(workoutId)

        // Без подтверждения ничего не удаляется.
        viewModel.deleteWorkout()
        advanceUntilIdle()
        assertNotNull(db.workoutDao().getById(workoutId))
        assertNull(viewModel.uiState.value.exit)

        viewModel.openDeleteWorkout()
        advanceUntilIdle()
        assertEquals(WorkoutDialog.ConfirmDeleteWorkout, viewModel.uiState.value.dialog)

        viewModel.deleteWorkout()
        advanceUntilIdle()

        assertEquals(WorkoutExit.Deleted, viewModel.uiState.value.exit)
        assertNull(db.workoutDao().getById(workoutId))
        assertEquals(0, countRows("workout_exercises"))
        assertEquals(1, countRows("exercises"))
        assertEquals(1, countRows("plan_exercises"))
        assertEquals("план", db.planDao().findByWeekdayAndExercise(2, squat)!!.progress)
    }

    // ------------------------------------------------------- порядок, удаление, идентичность

    @Test
    fun `move persists order without gaps and rejects edges`() = runTest {
        val first = insertExercise("Первое")
        val second = insertExercise("Второе")
        val third = insertExercise("Третье")
        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, first, position = 0, progress = "1")
        insertWorkoutRow(workoutId, second, position = 1, progress = "2")
        insertWorkoutRow(workoutId, third, position = 2, progress = "3")
        startViewModel(workoutId)

        viewModel.move(viewModel.uiState.value.exercises.first(), delta = -1)
        advanceUntilIdle()
        assertEquals(listOf(first, second, third), exerciseIds(workoutId))

        viewModel.move(viewModel.uiState.value.exercises.last(), delta = 1)
        advanceUntilIdle()
        assertEquals(listOf(first, second, third), exerciseIds(workoutId))

        viewModel.move(viewModel.uiState.value.exercises.first(), delta = 1)
        advanceUntilIdle()

        assertEquals(listOf(second, first, third), exerciseIds(workoutId))
        assertEquals(listOf(0, 1, 2), db.workoutDao().getExercises(workoutId).map { it.position })
        assertEquals(
            listOf("Второе", "Первое", "Третье"),
            viewModel.uiState.value.exercises.map { it.name },
        )
    }

    @Test
    fun `deleteExercise compacts positions and keeps preset`() = runTest {
        val first = insertExercise("Первое")
        val second = insertExercise("Второе")
        val third = insertExercise("Третье")
        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, first, position = 0, progress = "1")
        insertWorkoutRow(workoutId, second, position = 1, progress = "2")
        insertWorkoutRow(workoutId, third, position = 2, progress = "3")
        startViewModel(workoutId)

        viewModel.deleteExercise(viewModel.uiState.value.exercises[1])
        advanceUntilIdle()

        val rows = db.workoutDao().getExercises(workoutId)
        assertEquals(listOf(first, third), rows.map { it.exerciseId })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(3, countRows("exercises"))
        assertEquals(listOf("Первое", "Третье"), viewModel.uiState.value.exercises.map { it.name })
    }

    @Test
    fun `saveIdentity rejects blank name then updates global preset everywhere`() = runTest {
        val squat = insertExercise("Присед", description = "старое")
        addPlanRow(weekday = 2, exerciseId = squat, position = 0, progress = "план")
        val pastWorkoutId = insertWorkout(date = WEDNESDAY)
        insertWorkoutRow(pastWorkoutId, squat, position = 0, progress = "история")

        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, squat, position = 0, progress = "5/20")
        startViewModel(workoutId)

        val row = viewModel.uiState.value.exercises.single()
        viewModel.openEditIdentity(row)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.dialog is WorkoutDialog.EditIdentity)

        viewModel.saveIdentity(row, name = "   ", description = "")
        advanceUntilIdle()

        assertEquals(WorkoutMessage.SaveFailed, viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.dialog is WorkoutDialog.EditIdentity)
        assertEquals("Присед", db.exerciseDao().getById(squat)!!.name)

        viewModel.consumeMessage()
        currentNow = T_RENAME
        viewModel.saveIdentity(row, name = "  Новое имя  ", description = "Новое описание")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        val preset = db.exerciseDao().getById(squat)!!
        assertEquals("Новое имя", preset.name)
        assertEquals("Новое описание", preset.description)
        assertEquals(T_RENAME, preset.updatedAt)

        // Видно и в текущей тренировке, и в прошлой, и в плане.
        assertEquals("Новое имя", viewModel.uiState.value.exercises.single().name)
        val past = repository.observeWorkout(pastWorkoutId).first()!!
        assertEquals("Новое имя", past.exercises.single().exercise.name)
        assertEquals("Новое описание", past.exercises.single().exercise.description)
        assertEquals(
            "Новое имя",
            repository.observePlan().first().single { it.planExercise.weekday == 2 }.exercise.name,
        )
    }

    // ------------------------------------------------------- helpers

    private fun TestScope.startViewModel(workoutId: Long) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        viewModel = WorkoutViewModel(workoutId, repository, now = { currentNow })
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
    }

    private fun progressOf(rowId: Long): String =
        viewModel.uiState.value.exercises.first { it.rowId == rowId }.progress

    private suspend fun exerciseIds(workoutId: Long): List<Long> =
        db.workoutDao().getExercises(workoutId).map { it.exerciseId }

    private suspend fun insertExercise(
        name: String,
        description: String = "",
        lastProgress: String = "",
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            description = description,
            lastProgress = lastProgress,
            createdAt = T_OLD,
            updatedAt = T_OLD,
        ),
    )

    private suspend fun addPlanRow(
        weekday: Int,
        exerciseId: Long,
        position: Int,
        progress: String,
    ): Long = db.planDao().insert(
        PlanExerciseEntity(
            weekday = weekday,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = T_OLD,
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
            createdAt = T_OLD,
            completedAt = completedAt,
        ),
    )

    private suspend fun insertWorkoutRow(
        workoutId: Long,
        exerciseId: Long,
        position: Int,
        progress: String,
    ): Long = db.workoutDao().insertExercise(
        WorkoutExerciseEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            position = position,
            progress = progress,
            updatedAt = T_OLD,
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
        val WEDNESDAY: LocalDate = LocalDate.of(2026, 11, 25)

        const val UNKNOWN_ID = 9_999L

        /** Время в БД до действий пользователя. */
        const val T_OLD = 500L

        /** Инъектированное `now` для автосохранения, завершения, reload и переименования. */
        const val T0 = 1_000L
        const val T_SYNC = 1_500L
        const val T_FINISH = 2_000L
        const val T_RENAME = 2_500L
    }
}
