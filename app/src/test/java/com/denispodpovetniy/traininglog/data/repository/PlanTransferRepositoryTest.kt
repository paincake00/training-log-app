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
import com.denispodpovetniy.traininglog.util.PlanFile
import com.denispodpovetniy.traininglog.util.PlanFileCodec
import com.denispodpovetniy.traininglog.util.PlanFileExercise
import com.denispodpovetniy.traininglog.util.PlanFileException
import com.denispodpovetniy.traininglog.util.PlanFileRow
import com.denispodpovetniy.traininglog.util.ProgressCodec
import java.time.Instant
import java.time.LocalDate
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
 * Экспорт и импорт недельного плана: снимок, предпросмотр, замена плана целиком,
 * сопоставление пресетов по названию без учёта регистра (см. `PLAN.md` §3.10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanTransferRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TrainingRepository
    private val extraDatabases = mutableListOf<AppDatabase>()

    private val monday = LocalDate.of(2026, 11, 23)
    private val t0 = 1_000L

    @Before
    fun setUp() {
        db = newDatabase()
        repository = TrainingRepository(db)
    }

    @After
    fun tearDown() {
        extraDatabases.forEach { it.close() }
        db.close()
    }

    // ------------------------------------------------------------------ экспорт

    @Test
    fun `build plan file snapshots presets and plan without workouts`() = runTest {
        val squat = insertExercise(
            name = "Присед",
            description = "Спина прямая",
            units = "повторы\nкг",
            lastProgress = "5/20-5/20",
        )
        val press = insertExercise(name = "  Жим  ", units = "", lastProgress = "10/60")
        val lowercaseSquat = insertExercise(name = "присед", units = "кг", lastProgress = "")
        insertPlanRow(weekday = 1, exerciseId = squat, position = 0, progress = "5/20-5/20")
        insertPlanRow(weekday = 1, exerciseId = lowercaseSquat, position = 1, progress = "")
        insertPlanRow(weekday = 3, exerciseId = press, position = 0, progress = "10/60")
        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, press, position = 0, progress = "12/70")

        val file = repository.buildPlanFile(
            appVersion = "1.0.0",
            exportedAt = Instant.parse("2026-11-27T18:40:00Z"),
        )

        assertEquals(PlanFileCodec.FORMAT_VERSION, file.formatVersion)
        assertEquals("2026-11-27T18:40:00Z", file.exportedAt)
        assertEquals("1.0.0", file.appVersion)
        // Ключ — обрезанное название в нижнем регистре; совпадения по регистру получают суффикс.
        assertEquals(listOf("присед", "жим", "присед-2"), file.exercises.map { it.key })
        assertEquals(
            listOf(
                PlanFileExercise(
                    key = "присед",
                    name = "Присед",
                    description = "Спина прямая",
                    units = listOf("повторы", "кг"),
                    lastProgress = "5/20-5/20",
                ),
                PlanFileExercise(
                    key = "жим",
                    name = "  Жим  ",
                    description = "",
                    units = emptyList(),
                    lastProgress = "10/60",
                ),
                PlanFileExercise(
                    key = "присед-2",
                    name = "присед",
                    description = "",
                    units = listOf("кг"),
                    lastProgress = "",
                ),
            ),
            file.exercises,
        )
        assertEquals(
            listOf(
                PlanFileRow(weekday = 1, position = 0, exerciseKey = "присед", progress = "5/20-5/20"),
                PlanFileRow(weekday = 1, position = 1, exerciseKey = "присед-2", progress = ""),
                PlanFileRow(weekday = 3, position = 0, exerciseKey = "жим", progress = "10/60"),
            ),
            file.plan,
        )
        // Тренировки в файл не попадают: в нём нет ни тренировки, ни её строк.
        assertFalse(PlanFileCodec.encode(file).contains("workout", ignoreCase = true))
        assertEquals(1, db.workoutDao().getExercises(workoutId).size)
    }

    // -------------------------------------------------------------- предпросмотр

    @Test
    fun `preview counts added and updated presets by name ignoring case`() = runTest {
        insertExercise(name = "присед")
        insertExercise(name = "Планка")

        val summary = repository.previewPlanImport(
            planFile(
                exercises = listOf(
                    exercise(key = "присед", name = "Присед"),
                    exercise(key = "жим", name = "Жим"),
                    exercise(key = "планка", name = "Планка"),
                ),
                plan = listOf(
                    row(weekday = 1, position = 0, key = "присед"),
                    row(weekday = 3, position = 0, key = "жим"),
                ),
            ),
        )

        assertEquals(PlanImportSummary(addedExercises = 1, updatedExercises = 2, planRows = 2), summary)
        // Предпросмотр ничего не меняет.
        assertEquals(2, db.exerciseDao().getAll().size)
        assertTrue(db.planDao().getAll().isEmpty())
    }

    // ------------------------------------------------------------------- импорт

    @Test
    fun `import replaces whole week and keeps workouts`() = runTest {
        val old = insertExercise(name = "Старое", lastProgress = "старое")
        val squat = insertExercise(name = "Присед", lastProgress = "1/1")
        insertPlanRow(weekday = 5, exerciseId = old, position = 0, progress = "старое")
        insertPlanRow(weekday = 1, exerciseId = old, position = 0, progress = "старое")
        insertPlanRow(weekday = 1, exerciseId = squat, position = 1, progress = "1/1")
        val workoutId = insertWorkout()
        insertWorkoutRow(workoutId, old, position = 0, progress = "keep")

        val result = repository.importPlan(
            planFile(
                exercises = listOf(
                    exercise(key = "присед", name = "Присед", lastProgress = "5/20"),
                    exercise(key = "жим", name = "Жим", lastProgress = "3/10"),
                ),
                plan = listOf(
                    // Позиции намеренно с дырой и не по порядку: импорт их уплотняет.
                    row(weekday = 1, position = 5, key = "присед", progress = "5/20"),
                    row(weekday = 1, position = 0, key = "жим", progress = "3/10"),
                    row(weekday = 4, position = 0, key = "присед", progress = "7/7"),
                ),
            ),
            now = t0,
        )

        assertEquals(
            ImportPlanResult.Imported(PlanImportSummary(addedExercises = 1, updatedExercises = 1, planRows = 3)),
            result,
        )

        val rows = db.planDao().getAll()
        assertEquals(listOf(1 to 0, 1 to 1, 4 to 0), rows.map { it.weekday to it.position })
        assertEquals(listOf("3/10", "5/20", "7/7"), rows.map { it.progress })
        // День 5 из файла отсутствует — его строки исчезли.
        assertTrue(rows.none { it.weekday == 5 })
        val press = db.exerciseDao().getAll().single { it.name == "Жим" }
        assertEquals(press.id, rows[0].exerciseId)
        assertTrue(rows.drop(1).all { it.exerciseId == squat })
        // Пресеты не удаляются: «Старое» осталось со своей прогрессией,
        // хотя его строк плана больше нет и в файле его не было.
        assertEquals(setOf("Старое", "Присед", "Жим"), db.exerciseDao().getAll().map { it.name }.toSet())
        assertEquals("старое", db.exerciseDao().getAll().single { it.name == "Старое" }.lastProgress)

        // Тренировки и их упражнения не затрагиваются.
        assertEquals(1, db.workoutDao().getExercises(workoutId).size)
        assertEquals("keep", db.workoutDao().getExercises(workoutId).single().progress)
        assertEquals(old, db.workoutDao().getExercises(workoutId).single().exerciseId)
    }

    @Test
    fun `import merges preset by name ignoring case and file values win`() = runTest {
        val localId = insertExercise(
            name = "присед",
            description = "Старое описание",
            units = "кг",
            lastProgress = "1/1",
        )
        insertPlanRow(weekday = 7, exerciseId = localId, position = 0, progress = "1/1")

        val result = repository.importPlan(
            planFile(
                exercises = listOf(
                    PlanFileExercise(
                        key = "присед",
                        name = "Присед",
                        description = "Новое описание",
                        units = listOf("повторы", "кг"),
                        lastProgress = "5/20",
                    ),
                ),
                plan = listOf(row(weekday = 2, position = 0, key = "присед", progress = "5/20")),
            ),
            now = t0,
        )

        assertEquals(
            ImportPlanResult.Imported(PlanImportSummary(addedExercises = 0, updatedExercises = 1, planRows = 1)),
            result,
        )

        val exercises = db.exerciseDao().getAll()
        assertEquals(1, exercises.size)
        val merged = exercises.single()
        assertEquals(localId, merged.id)
        assertEquals("Присед", merged.name)
        assertEquals("Новое описание", merged.description)
        assertEquals("5/20", merged.lastProgress)
        assertEquals(listOf("повторы", "кг"), ProgressCodec.decodeUnits(merged.units))

        val rows = db.planDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().weekday)
        assertEquals(0, rows.single().position)
        assertEquals(merged.id, rows.single().exerciseId)
        assertEquals("5/20", rows.single().progress)
    }

    @Test
    fun `import creates missing presets and links plan rows by key`() = runTest {
        val result = repository.importPlan(
            planFile(
                exercises = listOf(
                    PlanFileExercise("жим", "Жим", "Грудь", listOf("кг"), "10/60"),
                    PlanFileExercise("тяга", "Тяга", "Спина", emptyList(), "8"),
                ),
                plan = listOf(
                    row(weekday = 2, position = 0, key = "тяга", progress = "8"),
                    row(weekday = 4, position = 0, key = "жим", progress = "10/60"),
                ),
            ),
            now = t0,
        )

        assertEquals(
            ImportPlanResult.Imported(PlanImportSummary(addedExercises = 2, updatedExercises = 0, planRows = 2)),
            result,
        )

        val byName = db.exerciseDao().getAll().associateBy { it.name }
        assertEquals(setOf("Жим", "Тяга"), byName.keys)
        assertEquals(ProgressCodec.encodeUnits(listOf("кг")), byName.getValue("Жим").units)
        assertEquals("10/60", byName.getValue("Жим").lastProgress)
        assertEquals("Грудь", byName.getValue("Жим").description)
        assertEquals("Спина", byName.getValue("Тяга").description)

        val rows = db.planDao().getAll()
        assertEquals(listOf(2, 4), rows.map { it.weekday })
        assertEquals(byName.getValue("Тяга").id, rows[0].exerciseId)
        assertEquals(byName.getValue("Жим").id, rows[1].exerciseId)
        assertEquals(listOf("8", "10/60"), rows.map { it.progress })
    }

    @Test
    fun `import rejects unknown exercise key without changing data`() = runTest {
        val exerciseId = insertExercise(
            name = "Присед",
            description = "Спина прямая",
            units = "кг",
            lastProgress = "5/20",
        )
        val rowId = insertPlanRow(weekday = 1, exerciseId = exerciseId, position = 0, progress = "5/20")

        val result = repository.importPlan(
            planFile(
                exercises = listOf(exercise(key = "присед", name = "Присед")),
                plan = listOf(row(weekday = 2, position = 0, key = "нет такого")),
            ),
            now = t0,
        )

        assertEquals(ImportPlanResult.Rejected(PlanFileException.Reason.DATA), result)

        val preset = db.exerciseDao().getById(exerciseId)!!
        assertEquals("Присед", preset.name)
        assertEquals("Спина прямая", preset.description)
        assertEquals("кг", preset.units)
        assertEquals("5/20", preset.lastProgress)

        val rows = db.planDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(rowId, rows.single().id)
        assertEquals(1, rows.single().weekday)
        assertEquals(exerciseId, rows.single().exerciseId)
        assertEquals("5/20", rows.single().progress)
    }

    @Test
    fun `import rejects duplicate exercise in one day without changing data`() = runTest {
        // Такой файл не пройдёт PlanFileCodec, но репозиторий тоже обязан отказать:
        // в схеме упражнение уникально внутри дня.
        val result = repository.importPlan(
            planFile(
                exercises = listOf(exercise(key = "присед", name = "Присед")),
                plan = listOf(
                    row(weekday = 2, position = 0, key = "присед", progress = "5"),
                    row(weekday = 2, position = 1, key = "присед", progress = "6"),
                ),
            ),
            now = t0,
        )

        assertEquals(ImportPlanResult.Rejected(PlanFileException.Reason.DATA), result)
        assertTrue(db.exerciseDao().getAll().isEmpty())
        assertTrue(db.planDao().getAll().isEmpty())
    }

    @Test
    fun `presets differing only by case survive export and import`() = runTest {
        val upper = insertExercise(name = "Присед", units = "кг", lastProgress = "5/20")
        val lower = insertExercise(name = "присед", units = "повторы", lastProgress = "3-4")
        insertPlanRow(weekday = 1, exerciseId = upper, position = 0, progress = "5/20")
        insertPlanRow(weekday = 2, exerciseId = lower, position = 0, progress = "3-4")

        // Экспорт различает такие пресеты суффиксом ключа («присед», «присед-2»),
        // поэтому собственный файл обязан читаться и импортироваться.
        val content = PlanFileCodec.encode(repository.buildPlanFile(appVersion = "1.0.0"))
        val target = newDatabase()
        val targetRepository = TrainingRepository(target)

        val result = targetRepository.importPlan(PlanFileCodec.decode(content), now = t0)

        assertEquals(
            ImportPlanResult.Imported(PlanImportSummary(addedExercises = 2, updatedExercises = 0, planRows = 2)),
            result,
        )
        assertEquals(2, target.exerciseDao().getAll().size)
        assertEquals(presetSnapshot(db), presetSnapshot(target))
        assertEquals(planSnapshot(db), planSnapshot(target))
    }

    @Test
    fun `export from one database imports into another`() = runTest {
        val squat = insertExercise(
            name = "Присед",
            description = "Спина прямая",
            units = "повторы\nкг",
            lastProgress = "5/20-5/20",
        )
        val walk = insertExercise(
            name = "Ходьба",
            description = "Улица",
            units = "мин\nкм/ч",
            lastProgress = "200-200-150",
        )
        insertPlanRow(weekday = 1, exerciseId = squat, position = 0, progress = "5/20-5/20")
        insertPlanRow(weekday = 3, exerciseId = walk, position = 0, progress = "200-200-150")

        val file = repository.buildPlanFile(
            appVersion = "1.0.0",
            exportedAt = Instant.parse("2026-11-27T18:40:00Z"),
        )
        val target = newDatabase()
        val targetRepository = TrainingRepository(target)

        val result = targetRepository.importPlan(file, now = t0)

        assertEquals(
            ImportPlanResult.Imported(PlanImportSummary(addedExercises = 2, updatedExercises = 0, planRows = 2)),
            result,
        )
        assertEquals(presetSnapshot(db), presetSnapshot(target))
        assertEquals(planSnapshot(db), planSnapshot(target))
    }

    // ------------------------------------------------------------------ helpers

    private fun newDatabase(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        extraDatabases += database
        return database
    }

    private fun planFile(
        exercises: List<PlanFileExercise>,
        plan: List<PlanFileRow>,
    ): PlanFile = PlanFile(
        formatVersion = PlanFileCodec.FORMAT_VERSION,
        exportedAt = "2026-11-27T18:40:00Z",
        appVersion = "1.0.0",
        exercises = exercises,
        plan = plan,
    )

    private fun exercise(
        key: String,
        name: String,
        description: String = "",
        units: List<String> = emptyList(),
        lastProgress: String = "",
    ): PlanFileExercise = PlanFileExercise(
        key = key,
        name = name,
        description = description,
        units = units,
        lastProgress = lastProgress,
    )

    private fun row(
        weekday: Int,
        position: Int,
        key: String,
        progress: String = "",
    ): PlanFileRow = PlanFileRow(
        weekday = weekday,
        position = position,
        exerciseKey = key,
        progress = progress,
    )

    /** Пресеты без локальных id: на другом устройстве они другие. */
    private suspend fun presetSnapshot(database: AppDatabase): List<List<String>> =
        database.exerciseDao().getAll()
            .map { listOf(it.name, it.description, it.units, it.lastProgress) }
            .sortedBy { it.first() }

    /** План с названием пресета вместо локального id. */
    private suspend fun planSnapshot(database: AppDatabase): List<List<String>> {
        val namesById = database.exerciseDao().getAll().associate { it.id to it.name }
        return database.planDao().getAll().map { row ->
            listOf(
                row.weekday.toString(),
                row.position.toString(),
                namesById.getValue(row.exerciseId),
                row.progress,
            )
        }
    }

    private suspend fun insertExercise(
        name: String,
        description: String = "",
        units: String = "",
        lastProgress: String = "",
        updatedAt: Long = t0,
    ): Long = db.exerciseDao().insert(
        ExerciseEntity(
            name = name,
            description = description,
            lastProgress = lastProgress,
            units = units,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        ),
    )

    private suspend fun insertPlanRow(
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
            updatedAt = t0,
        ),
    )

    private suspend fun insertWorkout(date: LocalDate = monday): Long = db.workoutDao().insert(
        WorkoutEntity(
            dateEpochDay = date.toEpochDay(),
            status = WorkoutStatus.IN_PROGRESS,
            createdAt = t0,
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
            updatedAt = t0,
        ),
    )
}
