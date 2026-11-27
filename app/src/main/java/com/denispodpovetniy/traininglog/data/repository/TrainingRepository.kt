package com.denispodpovetniy.traininglog.data.repository

import androidx.room.withTransaction
import com.denispodpovetniy.traininglog.data.db.AppDatabase
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.relation.PlanExerciseWithExercise
import com.denispodpovetniy.traininglog.data.relation.WorkoutWithExercises
import com.denispodpovetniy.traininglog.util.ExerciseValidator
import com.denispodpovetniy.traininglog.util.PlanFile
import com.denispodpovetniy.traininglog.util.PlanFileCodec
import com.denispodpovetniy.traininglog.util.PlanFileException
import com.denispodpovetniy.traininglog.util.PlanFileExercise
import com.denispodpovetniy.traininglog.util.PlanFileRow
import com.denispodpovetniy.traininglog.util.ProgressCodec
import com.denispodpovetniy.traininglog.util.isoWeekday
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Единая точка доступа к данным для ViewModel.
 *
 * Все составные изменения выполняются в транзакции. Позиции внутри дня плана и
 * тренировки поддерживаются без дыр. Прогрессия хранится ровно как введена.
 */
class TrainingRepository(private val db: AppDatabase) {

    private val exerciseDao = db.exerciseDao()
    private val planDao = db.planDao()
    private val workoutDao = db.workoutDao()

    fun observeLog(): Flow<List<WorkoutWithExercises>> =
        workoutDao.observeLog().map { workouts ->
            workouts
                .sortedByDescending { it.workout.dateEpochDay }
                .map { it.withSortedExercises() }
        }

    fun observePlan(): Flow<List<PlanExerciseWithExercise>> = planDao.observePlan()

    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?> =
        workoutDao.observeWorkout(id).map { it?.withSortedExercises() }

    /**
     * Пресеты по названию без учёта регистра. Пустой запрос возвращает все.
     *
     * Фильтр в памяти: SQLite `LIKE` регистронезависим только для ASCII,
     * а названия упражнений в приложении русские.
     */
    fun searchExercises(query: String): Flow<List<ExerciseEntity>> {
        val needle = query.trim()
        return exerciseDao.observeAll().map { exercises ->
            if (needle.isEmpty()) {
                exercises
            } else {
                exercises.filter { it.name.contains(needle, ignoreCase = true) }
            }
        }
    }

    /**
     * Единицы, которые пользователь уже где-то задавал. Порядок — по последнему
     * изменению пресета. Стартовые подсказки добавляет `ProgressCodec.unitSuggestions`.
     */
    fun observeUnitSuggestions(): Flow<List<String>> =
        exerciseDao.observeAll().map { exercises ->
            val unique = LinkedHashMap<String, String>()
            exercises.forEach { exercise ->
                ProgressCodec.decodeUnits(exercise.units).forEach { unit ->
                    unique.putIfAbsent(unit.lowercase(), unit)
                }
            }
            ProgressCodec.unitSuggestions(unique.values.toList())
        }

    /**
     * Заменяет список единиц пресета.
     *
     * Названия обрезаются; пустые, длиннее 20 символов и повторы без учёта регистра
     * отклоняются целиком, чтобы не потерять часть списка.
     *
     * @return `false`, если список не прошёл проверку или пресет не найден.
     */
    suspend fun updateExerciseUnits(
        exerciseId: Long,
        units: List<String>,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val normalized = mutableListOf<String>()
        units.forEach { raw ->
            val name = ProgressCodec.normalizeUnit(raw, normalized) ?: return false
            normalized += name
        }
        return exerciseDao.updateUnits(
            exerciseId,
            ProgressCodec.encodeUnits(normalized),
            now,
        ) > 0
    }

    /**
     * Удаляет единицу с позицией [index] и вычищает её значения из всех подходов:
     * план, все тренировки и `lastProgress`. Одна транзакция.
     *
     * @return `false`, если пресета или такой позиции нет.
     */
    suspend fun removeExerciseUnit(
        exerciseId: Long,
        index: Int,
        now: Long = System.currentTimeMillis(),
    ): Boolean = db.withTransaction {
        val exercise = exerciseDao.getById(exerciseId) ?: return@withTransaction false
        val units = ProgressCodec.decodeUnits(exercise.units)
        if (index !in units.indices) return@withTransaction false

        exerciseDao.updateUnits(
            exerciseId,
            ProgressCodec.encodeUnits(units.filterIndexed { position, _ -> position != index }),
            now,
        )

        val lastProgress = ProgressCodec.removeUnitValues(exercise.lastProgress, index)
        if (lastProgress != exercise.lastProgress) {
            exerciseDao.updateLastProgress(exerciseId, lastProgress, now)
        }

        planDao.getByExercise(exerciseId).forEach { row ->
            val stripped = ProgressCodec.removeUnitValues(row.progress, index)
            if (stripped != row.progress) {
                planDao.updateProgress(row.id, stripped, now)
            }
        }

        workoutDao.getExercisesByExercise(exerciseId).forEach { row ->
            val stripped = ProgressCodec.removeUnitValues(row.progress, index)
            if (stripped != row.progress) {
                workoutDao.updateProgress(row.id, stripped, now)
            }
        }

        true
    }

    /**
     * Снимок пресетов и недельного плана для экспорта. В БД ничего не меняется.
     *
     * Прогрессия переносится как есть, включая старые строки: на другом
     * устройстве они останутся читаемыми в режиме `LEGACY`.
     */
    suspend fun buildPlanFile(
        appVersion: String,
        exportedAt: Instant = Instant.now(),
    ): PlanFile {
        val exercises = exerciseDao.getAll()
        val planRows = planDao.getAll()

        // Ключ — название без регистра. Если пресеты различаются только регистром,
        // ключи получают суффикс, иначе файл не прошёл бы проверку на дубли.
        val usedKeys = mutableMapOf<String, Int>()
        val keyById = exercises.associate { exercise ->
            val base = PlanFileCodec.keyOf(exercise.name)
            val count = (usedKeys[base] ?: 0) + 1
            usedKeys[base] = count
            exercise.id to if (count == 1) base else "$base-$count"
        }

        return PlanFile(
            formatVersion = PlanFileCodec.FORMAT_VERSION,
            exportedAt = exportedAt.toString(),
            appVersion = appVersion,
            exercises = exercises.map { exercise ->
                PlanFileExercise(
                    key = keyById.getValue(exercise.id),
                    name = exercise.name,
                    description = exercise.description,
                    units = ProgressCodec.decodeUnits(exercise.units),
                    lastProgress = exercise.lastProgress,
                )
            },
            plan = planRows.mapNotNull { row ->
                val key = keyById[row.exerciseId] ?: return@mapNotNull null
                PlanFileRow(
                    weekday = row.weekday,
                    position = row.position,
                    exerciseKey = key,
                    progress = row.progress,
                )
            },
        )
    }

    /**
     * Что изменится при импорте: сколько пресетов добавится, обновится и строк плана.
     *
     * Ничего не меняет; используется для предпросмотра до подтверждения.
     */
    suspend fun previewPlanImport(file: PlanFile): PlanImportSummary {
        val existing = exerciseDao.getAll()
        val existingExact = existing.associateBy { it.name.trim() }
        val existingByKey = existing.groupBy { PlanFileCodec.keyOf(it.name) }
        val added = file.exercises.count { matchPreset(existingExact, existingByKey, it) == null }
        return PlanImportSummary(
            addedExercises = added,
            updatedExercises = file.exercises.size - added,
            planRows = file.plan.size,
        )
    }

    /**
     * Применяет файл плана в одной транзакции: пресеты сопоставляются по названию
     * (сначала точное, затем без учёта регистра; значения из файла побеждают),
     * недельный план заменяется целиком. Тренировки не затрагиваются.
     */
    suspend fun importPlan(
        file: PlanFile,
        now: Long = System.currentTimeMillis(),
    ): ImportPlanResult {
        val fileKeys = file.exercises.mapTo(mutableSetOf()) { it.key }
        val existing = exerciseDao.getAll()
        val existingExact = existing.associateBy { it.name.trim() }
        val existingByKey = existing.groupBy { PlanFileCodec.keyOf(it.name) }

        // Два пресета файла могут совпасть с одним локальным; в одном дне
        // они столкнутся с уникальностью (weekday, exerciseId).
        val matchedIds = HashMap<String, Long>()
        file.exercises.forEach { imported ->
            matchPreset(existingExact, existingByKey, imported)?.let { matchedIds[imported.key] = it.id }
        }
        val samePresetTwiceInDay = file.plan.groupBy { it.weekday }.any { (_, rows) ->
            val ids = rows.mapNotNull { matchedIds[it.exerciseKey] }
            ids.distinct().size != ids.size
        }

        val invalidFile = fileKeys.size != file.exercises.size ||
            file.plan.any { it.exerciseKey !in fileKeys } ||
            file.plan.groupBy { it.weekday }.any { (weekday, rows) ->
                // Позиции и упражнения в дне уникальны — так же, как в схеме БД.
                weekday !in 1..7 ||
                    rows.map { it.position }.toSet().size != rows.size ||
                    rows.map { it.exerciseKey }.toSet().size != rows.size
            } ||
            samePresetTwiceInDay
        if (invalidFile) return ImportPlanResult.Rejected(PlanFileException.Reason.DATA)

        return db.withTransaction {
            val idByKey = HashMap<String, Long>()
            var added = 0
            var updated = 0

            file.exercises.forEach { imported ->
                val units = ProgressCodec.encodeUnits(imported.units)
                val match = matchPreset(existingExact, existingByKey, imported)
                if (match == null) {
                    idByKey[imported.key] = exerciseDao.insert(
                        ExerciseEntity(
                            name = imported.name,
                            description = imported.description,
                            lastProgress = imported.lastProgress,
                            units = units,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    added++
                } else {
                    exerciseDao.updateFromPlanFile(
                        id = match.id,
                        name = imported.name,
                        description = imported.description,
                        progress = imported.lastProgress,
                        units = units,
                        now = now,
                    )
                    idByKey[imported.key] = match.id
                    updated++
                }
            }

            planDao.deleteAll()
            file.plan
                .groupBy { it.weekday }
                .toSortedMap()
                .forEach { (weekday, rows) ->
                    rows.sortedBy { it.position }.forEachIndexed { index, row ->
                        planDao.insert(
                            PlanExerciseEntity(
                                weekday = weekday,
                                exerciseId = idByKey.getValue(row.exerciseKey),
                                position = index,
                                progress = row.progress,
                                updatedAt = now,
                            ),
                        )
                    }
                }

            ImportPlanResult.Imported(
                PlanImportSummary(
                    addedExercises = added,
                    updatedExercises = updated,
                    planRows = file.plan.size,
                ),
            )
        }
    }

    /**
     * Идемпотентно возвращает тренировку на [date], создавая её при необходимости.
     *
     * В одной транзакции создаётся тренировка `IN_PROGRESS` и копируются упражнения
     * плана соответствующего дня недели в порядке позиций. Строки тренировки
     * создаются с пустой фактической прогрессией: плановая прогрессия остаётся
     * в плане и показывается в UI только подсказкой «По плану».
     * Пустой план даёт пустую тренировку.
     */
    suspend fun createWorkoutForDate(
        date: LocalDate,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val dateEpochDay = date.toEpochDay()
        workoutDao.getByDate(dateEpochDay)?.let { return@withTransaction it.id }

        val workoutId = workoutDao.insert(
            WorkoutEntity(
                dateEpochDay = dateEpochDay,
                status = WorkoutStatus.IN_PROGRESS,
                createdAt = now,
            ),
        )

        planDao.getByWeekday(isoWeekday(date)).forEach { planExercise ->
            workoutDao.insertExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = planExercise.exerciseId,
                    position = planExercise.position,
                    // Плановое значение — только подсказка, фактическая прогрессия пустая.
                    progress = "",
                    updatedAt = now,
                ),
            )
        }

        workoutId
    }

    /** Сохраняет фактическую прогрессию без нормализации. */
    suspend fun updateWorkoutExerciseProgress(
        id: Long,
        text: String,
        now: Long = System.currentTimeMillis(),
    ) {
        workoutDao.updateProgress(id, text, now)
    }

    /**
     * Обновляет глобальные название и описание пресета.
     *
     * @return `false`, если название пустое (изменения не применяются) или пресет не найден.
     */
    suspend fun updateExerciseIdentity(
        exerciseId: Long,
        name: String,
        description: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val normalizedName = ExerciseValidator.normalizeName(name) ?: return false
        return exerciseDao.updateIdentity(exerciseId, normalizedName, description, now) > 0
    }

    /** Обновляет плановую прогрессию и `Exercise.lastProgress`. */
    suspend fun updatePlanProgress(
        id: Long,
        text: String,
        now: Long = System.currentTimeMillis(),
    ) {
        db.withTransaction {
            val planExercise = planDao.getById(id) ?: return@withTransaction
            planDao.updateProgress(id, text, now)
            exerciseDao.updateLastProgress(planExercise.exerciseId, text, now)
        }
    }

    /**
     * Ручная синхронизация фактической прогрессии в план дня недели тренировки
     * и в `lastProgress` пресета. Другие дни и тренировки не затрагиваются.
     *
     * Доступна для тренировки в любом статусе: и `IN_PROGRESS`, и `COMPLETED`.
     */
    suspend fun syncProgressToPlan(
        workoutId: Long,
        workoutExerciseId: Long,
        now: Long = System.currentTimeMillis(),
    ): SyncResult = db.withTransaction {
        val workout = workoutDao.getById(workoutId)
            ?: return@withTransaction SyncResult.NotFound

        val row = workoutDao.getExerciseById(workoutExerciseId)
            ?: return@withTransaction SyncResult.NotFound
        if (row.workoutId != workoutId) {
            return@withTransaction SyncResult.NotFound
        }

        val weekday = isoWeekday(LocalDate.ofEpochDay(workout.dateEpochDay))
        val planExercise = planDao.findByWeekdayAndExercise(weekday, row.exerciseId)

        if (planExercise != null) {
            planDao.updateProgress(planExercise.id, row.progress, now)
            exerciseDao.updateLastProgress(row.exerciseId, row.progress, now)
            SyncResult.UpdatedPlanAndPreset
        } else {
            exerciseDao.updateLastProgress(row.exerciseId, row.progress, now)
            SyncResult.UpdatedPresetOnly
        }
    }

    /**
     * Добавляет существующий пресет в план дня.
     *
     * @param progress `null` — подставить `Exercise.lastProgress`.
     */
    suspend fun addPlanExercise(
        weekday: Int,
        exerciseId: Long,
        progress: String? = null,
        now: Long = System.currentTimeMillis(),
    ): AddResult {
        require(weekday in WEEKDAY_RANGE) { "weekday must be in 1..7" }

        return db.withTransaction {
            val exercise = exerciseDao.getById(exerciseId)
                ?: return@withTransaction AddResult.ExerciseNotFound

            if (planDao.findByWeekdayAndExercise(weekday, exerciseId) != null) {
                return@withTransaction AddResult.Duplicate
            }

            val position = planDao.maxPosition(weekday) + 1
            val rowId = planDao.insert(
                PlanExerciseEntity(
                    weekday = weekday,
                    exerciseId = exerciseId,
                    position = position,
                    progress = progress ?: exercise.lastProgress,
                    updatedAt = now,
                ),
            )
            AddResult.Added(rowId)
        }
    }

    /**
     * Добавляет существующий пресет в тренировку.
     *
     * @param progress `null` — подставить `Exercise.lastProgress`.
     */
    suspend fun addWorkoutExercise(
        workoutId: Long,
        exerciseId: Long,
        progress: String? = null,
        now: Long = System.currentTimeMillis(),
    ): AddResult = db.withTransaction {
        if (workoutDao.getById(workoutId) == null) {
            return@withTransaction AddResult.WorkoutNotFound
        }

        val exercise = exerciseDao.getById(exerciseId)
            ?: return@withTransaction AddResult.ExerciseNotFound

        if (workoutDao.findExercise(workoutId, exerciseId) != null) {
            return@withTransaction AddResult.Duplicate
        }

        val position = workoutDao.maxPosition(workoutId) + 1
        val rowId = workoutDao.insertExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                position = position,
                progress = progress ?: exercise.lastProgress,
                updatedAt = now,
            ),
        )
        AddResult.Added(rowId)
    }

    /** Создаёт пресет и сразу добавляет его в план дня в одной транзакции. */
    suspend fun createExerciseAndAddToPlan(
        weekday: Int,
        name: String,
        description: String,
        progress: String? = null,
        units: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): CreateExerciseResult {
        require(weekday in WEEKDAY_RANGE) { "weekday must be in 1..7" }

        val normalizedName = ExerciseValidator.normalizeName(name)
            ?: return CreateExerciseResult.InvalidName
        val progressValue = progress.orEmpty()

        return db.withTransaction {
            val exerciseId = exerciseDao.insert(
                ExerciseEntity(
                    name = normalizedName,
                    description = description,
                    lastProgress = progressValue,
                    units = ProgressCodec.encodeUnits(normalizeUnits(units)),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val position = planDao.maxPosition(weekday) + 1
            val rowId = planDao.insert(
                PlanExerciseEntity(
                    weekday = weekday,
                    exerciseId = exerciseId,
                    position = position,
                    progress = progressValue,
                    updatedAt = now,
                ),
            )
            CreateExerciseResult.Created(exerciseId = exerciseId, rowId = rowId)
        }
    }

    /** Создаёт пресет и сразу добавляет его в тренировку в одной транзакции. */
    suspend fun createExerciseAndAddToWorkout(
        workoutId: Long,
        name: String,
        description: String,
        progress: String? = null,
        units: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): CreateExerciseResult {
        val normalizedName = ExerciseValidator.normalizeName(name)
            ?: return CreateExerciseResult.InvalidName
        val progressValue = progress.orEmpty()

        return db.withTransaction {
            if (workoutDao.getById(workoutId) == null) {
                return@withTransaction CreateExerciseResult.WorkoutNotFound
            }

            val exerciseId = exerciseDao.insert(
                ExerciseEntity(
                    name = normalizedName,
                    description = description,
                    lastProgress = progressValue,
                    units = ProgressCodec.encodeUnits(normalizeUnits(units)),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val position = workoutDao.maxPosition(workoutId) + 1
            val rowId = workoutDao.insertExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    position = position,
                    progress = progressValue,
                    updatedAt = now,
                ),
            )
            CreateExerciseResult.Created(exerciseId = exerciseId, rowId = rowId)
        }
    }

    /** Удаляет строку плана и пересчитывает позиции дня без дыр. */
    suspend fun deletePlanExercise(id: Long) {
        db.withTransaction {
            val planExercise = planDao.getById(id) ?: return@withTransaction
            planDao.deleteById(id)
            compactPlanPositions(planExercise.weekday)
        }
    }

    /** Удаляет строку тренировки и пересчитывает позиции тренировки без дыр. */
    suspend fun deleteWorkoutExercise(id: Long) {
        db.withTransaction {
            val workoutExercise = workoutDao.getExerciseById(id) ?: return@withTransaction
            workoutDao.deleteExerciseById(id)
            compactWorkoutPositions(workoutExercise.workoutId)
        }
    }

    /** Удаляет тренировку. Её упражнения удаляются каскадно, план и пресеты сохраняются. */
    suspend fun deleteWorkout(id: Long) {
        db.withTransaction {
            workoutDao.deleteById(id)
        }
    }

    suspend fun finishWorkout(id: Long, now: Long = System.currentTimeMillis()) {
        workoutDao.updateStatus(id, WorkoutStatus.COMPLETED, now)
    }

    suspend fun reopenWorkout(id: Long) {
        workoutDao.updateStatus(id, WorkoutStatus.IN_PROGRESS, null)
    }

    /**
     * Переставляет упражнения дня плана.
     *
     * @throws IllegalArgumentException если [orderedIds] не содержит ровно все id этого дня.
     */
    suspend fun reorderPlan(weekday: Int, orderedIds: List<Long>) {
        require(weekday in WEEKDAY_RANGE) { "weekday must be in 1..7" }
        requireDistinct(orderedIds)

        db.withTransaction {
            val existingIds = planDao.getByWeekday(weekday).map { it.id }
            require(existingIds.size == orderedIds.size && existingIds.toSet() == orderedIds.toSet()) {
                "orderedIds must contain exactly all plan exercise ids for weekday $weekday"
            }

            orderedIds.forEachIndexed { index, id ->
                planDao.updatePosition(id, index)
            }
        }
    }

    /**
     * Переставляет упражнения тренировки.
     *
     * @throws IllegalArgumentException если [orderedIds] не содержит ровно все id тренировки.
     */
    suspend fun reorderWorkout(workoutId: Long, orderedIds: List<Long>) {
        requireDistinct(orderedIds)

        db.withTransaction {
            val existingIds = workoutDao.getExercises(workoutId).map { it.id }
            require(existingIds.size == orderedIds.size && existingIds.toSet() == orderedIds.toSet()) {
                "orderedIds must contain exactly all workout exercise ids for workout $workoutId"
            }

            orderedIds.forEachIndexed { index, id ->
                workoutDao.updatePosition(id, index)
            }
        }
    }

    private suspend fun compactPlanPositions(weekday: Int) {
        planDao.getByWeekday(weekday).forEachIndexed { index, planExercise ->
            if (planExercise.position != index) {
                planDao.updatePosition(planExercise.id, index)
            }
        }
    }

    private suspend fun compactWorkoutPositions(workoutId: Long) {
        workoutDao.getExercises(workoutId).forEachIndexed { index, workoutExercise ->
            if (workoutExercise.position != index) {
                workoutDao.updatePosition(workoutExercise.id, index)
            }
        }
    }

    private fun requireDistinct(orderedIds: List<Long>) {
        require(orderedIds.size == orderedIds.toSet().size) {
            "orderedIds must not contain duplicates"
        }
    }

    /**
     * Ищет локальный пресет для пресета из файла: сначала точное название,
     * затем без учёта регистра. Точное совпадение сохраняет раздельными пресеты,
     * которые отличаются только регистром.
     */
    private fun matchPreset(
        exact: Map<String, ExerciseEntity>,
        byKey: Map<String, List<ExerciseEntity>>,
        imported: PlanFileExercise,
    ): ExerciseEntity? =
        exact[imported.name.trim()] ?: byKey[PlanFileCodec.keyOf(imported.name)]?.firstOrNull()

    /** Отбрасывает пустые, слишком длинные и повторяющиеся названия единиц. */
    private fun normalizeUnits(units: List<String>): List<String> {        val normalized = mutableListOf<String>()
        units.forEach { raw ->
            ProgressCodec.normalizeUnit(raw, normalized)?.let(normalized::add)
        }
        return normalized
    }

    private fun WorkoutWithExercises.withSortedExercises(): WorkoutWithExercises =
        copy(exercises = exercises.sortedBy { it.workoutExercise.position })

    private companion object {
        val WEEKDAY_RANGE = 1..7
    }
}
