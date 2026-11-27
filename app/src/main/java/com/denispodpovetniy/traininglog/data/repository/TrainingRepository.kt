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
import com.denispodpovetniy.traininglog.util.ProgressCodec
import com.denispodpovetniy.traininglog.util.isoWeekday
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

    /** Отбрасывает пустые, слишком длинные и повторяющиеся названия единиц. */
    private fun normalizeUnits(units: List<String>): List<String> {
        val normalized = mutableListOf<String>()
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
