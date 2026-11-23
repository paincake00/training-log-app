package com.denispodpovetniy.traininglog.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.data.repository.AddResult
import com.denispodpovetniy.traininglog.data.repository.CreateExerciseResult
import com.denispodpovetniy.traininglog.data.repository.SyncResult
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel экрана тренировки.
 *
 * Единственное состояние — [uiState]. Ввод фактической прогрессии сразу попадает
 * в состояние (локальный overlay), а в БД уходит с задержкой [PROGRESS_DEBOUNCE_MS].
 * Перед завершением, reload и выходом отложенное сохранение сбрасывается через
 * [flushProgress].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val workoutId: Long,
    private val repository: TrainingRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val pickerVisible = MutableStateFlow(false)
    private val pickerQuery = MutableStateFlow("")
    private val dialog = MutableStateFlow<WorkoutDialog?>(null)
    private val message = MutableStateFlow<WorkoutMessage?>(null)
    private val exit = MutableStateFlow<WorkoutExit?>(null)
    private val mutationInFlight = AtomicBoolean(false)
    private val backInFlight = AtomicBoolean(false)

    /** Несохранённый ввод фактической прогрессии: id строки тренировки → текст. */
    private val progressOverrides = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** Последние значения, гарантированно записанные в БД. */
    private var savedProgress: Map<Long, String> = emptyMap()
    private val progressMutex = Mutex()

    private val searchResults: StateFlow<List<ExerciseEntity>> = pickerQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query -> repository.searchExercises(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val contentState = combine(
        repository.observeWorkout(workoutId),
        repository.observePlan(),
        progressOverrides,
    ) { workout, planRows, overrides ->
        buildWorkoutUiState(
            requestedId = workoutId,
            workout = workout,
            planRows = planRows,
            progressOverrides = overrides,
        )
    }

    private val pickerState = combine(
        pickerVisible,
        pickerQuery,
        searchResults,
        dialog,
    ) { visible, query, results, currentDialog ->
        WorkoutPickerState(
            visible = visible,
            query = query,
            results = results,
            dialog = currentDialog,
        )
    }

    val uiState: StateFlow<WorkoutUiState> = combine(
        contentState,
        pickerState,
        message,
        exit,
    ) { content, picker, currentMessage, exitEvent ->
        content.copy(
            pickerVisible = picker.visible,
            pickerQuery = picker.query,
            pickerResults = picker.results,
            dialog = picker.dialog,
            message = currentMessage,
            exit = exitEvent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutUiState(loading = true, workoutId = workoutId),
    )

    init {
        viewModelScope.launch {
            progressOverrides
                .filter { it.isNotEmpty() }
                .debounce(PROGRESS_DEBOUNCE_MS)
                .collect {
                    try {
                        savePendingProgress()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message.value = WorkoutMessage.SaveFailed
                    }
                }
        }
    }

    /** Немедленно сохраняет последний введённый текст прогрессии. */
    suspend fun flushProgress() {
        savePendingProgress()
    }

    fun onProgressChange(rowId: Long, text: String) {
        progressOverrides.update { it + (rowId to text) }
    }

    fun openPicker() {
        pickerQuery.value = ""
        pickerVisible.value = true
    }

    fun closePicker() {
        pickerVisible.value = false
        pickerQuery.value = ""
    }

    fun setPickerQuery(query: String) {
        pickerQuery.value = query
    }

    fun addExisting(exerciseId: Long) {
        if (!pickerVisible.value) return
        launchMutation {
            when (
                repository.addWorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    progress = null,
                    now = now(),
                )
            ) {
                is AddResult.Added -> closePicker()
                AddResult.Duplicate -> message.value = WorkoutMessage.DuplicateExercise
                AddResult.ExerciseNotFound -> message.value = WorkoutMessage.ExerciseNotFound
                AddResult.WorkoutNotFound -> message.value = WorkoutMessage.SaveFailed
            }
        }
    }

    fun openCreateFromPicker() {
        if (!pickerVisible.value) return
        closePicker()
        dialog.value = WorkoutDialog.Create
    }

    fun saveNewExercise(name: String, description: String, progress: String) {
        if (dialog.value !is WorkoutDialog.Create) return
        launchMutation {
            when (
                repository.createExerciseAndAddToWorkout(
                    workoutId = workoutId,
                    name = name,
                    description = description,
                    progress = progress,
                    now = now(),
                )
            ) {
                is CreateExerciseResult.Created -> dismissDialog()
                CreateExerciseResult.InvalidName,
                CreateExerciseResult.WorkoutNotFound,
                -> message.value = WorkoutMessage.SaveFailed
            }
        }
    }

    fun openEditIdentity(row: WorkoutExerciseUi) {
        dialog.value = WorkoutDialog.EditIdentity(
            rowId = row.rowId,
            exerciseId = row.exerciseId,
            name = row.name,
            description = row.description,
        )
    }

    fun openDeleteExercise(row: WorkoutExerciseUi) {
        dialog.value = WorkoutDialog.ConfirmDeleteExercise(
            rowId = row.rowId,
            exerciseName = row.name,
        )
    }

    fun openDeleteWorkout() {
        dialog.value = WorkoutDialog.ConfirmDeleteWorkout
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun saveIdentity(row: WorkoutExerciseUi, name: String, description: String) {
        launchMutation {
            if (repository.updateExerciseIdentity(row.exerciseId, name, description, now = now())) {
                dismissDialog()
            } else {
                message.value = WorkoutMessage.SaveFailed
            }
        }
    }

    fun deleteExercise(row: WorkoutExerciseUi) {
        launchMutation {
            repository.deleteWorkoutExercise(row.rowId)
            dismissDialog()
        }
    }

    fun deleteWorkout() {
        if (dialog.value != WorkoutDialog.ConfirmDeleteWorkout) return
        launchMutation {
            repository.deleteWorkout(workoutId)
            dismissDialog()
            exit.value = WorkoutExit.Deleted
        }
    }

    fun move(row: WorkoutExerciseUi, delta: Int) {
        val orderedIds = reorderedWorkoutIds(uiState.value.exercises, row.rowId, delta) ?: return
        launchMutation {
            repository.reorderWorkout(workoutId, orderedIds)
        }
    }

    /**
     * Синхронизирует фактическую прогрессию строки в план дня недели тренировки
     * и в `lastProgress` пресета. Доступно только для `IN_PROGRESS`.
     */
    fun reload(row: WorkoutExerciseUi) {
        if (uiState.value.status != WorkoutStatus.IN_PROGRESS) return
        launchMutation {
            flushProgress()
            when (repository.syncProgressToPlan(workoutId, row.rowId, now = now())) {
                SyncResult.UpdatedPlanAndPreset -> message.value = WorkoutMessage.SyncedPlanAndPreset
                SyncResult.UpdatedPresetOnly -> message.value = WorkoutMessage.SyncedPresetOnly
                SyncResult.WorkoutNotInProgress -> message.value = WorkoutMessage.NotInProgress
                SyncResult.NotFound -> message.value = WorkoutMessage.ExerciseNotFound
            }
        }
    }

    /** Завершает тренировку, предварительно сохраняя отложенный ввод. */
    fun finish() {
        if (uiState.value.status != WorkoutStatus.IN_PROGRESS) return
        launchMutation {
            flushProgress()
            repository.finishWorkout(workoutId, now = now())
            exit.value = WorkoutExit.Finished
        }
    }

    /** Возвращает завершённую тренировку в работу. */
    fun reopen() {
        if (uiState.value.status != WorkoutStatus.COMPLETED) return
        launchMutation {
            repository.reopenWorkout(workoutId)
        }
    }

    /** Системная кнопка «Назад» и стрелка: сохраняет ввод и оставляет черновик. */
    fun requestBack() {
        if (exit.value != null) return
        if (!backInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                flushProgress()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = WorkoutMessage.SaveFailed
            } finally {
                if (exit.value == null) {
                    exit.value = WorkoutExit.Back
                }
                backInFlight.set(false)
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeExit() {
        exit.value = null
    }

    private suspend fun savePendingProgress() {
        // Ввод может прийти, пока предыдущая запись ещё выполняется. Повторяем
        // проход, пока не сохраним последнюю версию; ограничение защищает от
        // бесконечного цикла при непрерывном вводе.
        repeat(MAX_FLUSH_PASSES) {
            val dirty = progressMutex.withLock {
                val pending = progressOverrides.value
                val toSave = pending.filter { (rowId, text) -> savedProgress[rowId] != text }
                toSave.forEach { (rowId, text) ->
                    repository.updateWorkoutExerciseProgress(rowId, text, now = now())
                    savedProgress = savedProgress + (rowId to text)
                }
                toSave
            }
            if (dirty.isEmpty()) return
        }
    }

    /**
     * Запускает изменяющую операцию и отбрасывает повторные нажатия,
     * пока предыдущая операция не завершилась.
     */
    private fun launchMutation(block: suspend () -> Unit) {
        if (!mutationInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = WorkoutMessage.SaveFailed
            } finally {
                mutationInFlight.set(false)
            }
        }
    }

    class Factory(
        private val workoutId: Long,
        private val repository: TrainingRepository,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                return WorkoutViewModel(workoutId, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private data class WorkoutPickerState(
        val visible: Boolean,
        val query: String,
        val results: List<ExerciseEntity>,
        val dialog: WorkoutDialog?,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val PROGRESS_DEBOUNCE_MS = 300L
        const val MAX_FLUSH_PASSES = 8
    }
}
