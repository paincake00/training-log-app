package com.denispodpovetniy.traininglog.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.repository.AddResult
import com.denispodpovetniy.traininglog.data.repository.CreateExerciseResult
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import com.denispodpovetniy.traininglog.util.isoWeekday
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel недельного плана.
 *
 * Единственное состояние — [uiState]. Все изменения идут через [TrainingRepository],
 * ошибки возвращаются как [PlanMessage] и показываются snackbar в UI.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val repository: TrainingRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val pickerWeekday = MutableStateFlow<Int?>(null)
    private val pickerQuery = MutableStateFlow("")
    private val dialog = MutableStateFlow<PlanDialog?>(null)
    private val message = MutableStateFlow<PlanMessage?>(null)
    private val openWorkoutId = MutableStateFlow<Long?>(null)
    private val mutationInFlight = AtomicBoolean(false)

    private val searchResults: StateFlow<List<ExerciseEntity>> = pickerQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query -> repository.searchExercises(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сегодняшняя тренировка по логу — только id и статус для CTA. */
    private val todayWorkoutState: StateFlow<TodayWorkoutUi?> = repository.observeLog()
        .map { workouts ->
            val todayEpochDay = today().toEpochDay()
            workouts
                .firstOrNull { it.workout.dateEpochDay == todayEpochDay }
                ?.let { TodayWorkoutUi(it.workout.id, it.workout.status) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val pickerState = combine(
        repository.observePlan(),
        pickerWeekday,
        pickerQuery,
        searchResults,
        dialog,
    ) { rows, weekday, query, results, currentDialog ->
        PlanUiState(
            days = buildPlanDays(rows, isoWeekday(today())),
            pickerWeekday = weekday,
            pickerQuery = query,
            pickerResults = results,
            dialog = currentDialog,
        )
    }

    val uiState: StateFlow<PlanUiState> = combine(
        pickerState,
        message,
        todayWorkoutState,
        openWorkoutId,
    ) { state, currentMessage, todayWorkout, workoutToOpen ->
        state.copy(
            todayWorkout = todayWorkout,
            openWorkoutId = workoutToOpen,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState(
            days = buildPlanDays(emptyList(), isoWeekday(today())),
        ),
    )

    /**
     * Создаёт тренировку на сегодня или возвращает существующую и просит UI открыть её.
     */
    fun startOrOpenTodayWorkout() {
        launchMutation {
            openWorkoutId.value = repository.createWorkoutForDate(today())
        }
    }

    /** Забирает одноразовое событие открытия тренировки. */
    fun consumeOpenWorkout() {
        openWorkoutId.value = null
    }

    fun openPicker(weekday: Int) {
        pickerQuery.value = ""
        pickerWeekday.value = weekday
    }

    fun closePicker() {
        pickerWeekday.value = null
        pickerQuery.value = ""
    }

    fun setPickerQuery(query: String) {
        pickerQuery.value = query
    }

    fun addExisting(exerciseId: Long) {
        val weekday = pickerWeekday.value ?: return
        launchMutation {
            when (repository.addPlanExercise(weekday, exerciseId, progress = null)) {
                is AddResult.Added -> closePicker()
                AddResult.Duplicate -> message.value = PlanMessage.DuplicateExercise
                AddResult.ExerciseNotFound -> message.value = PlanMessage.ExerciseNotFound
                AddResult.WorkoutNotFound -> message.value = PlanMessage.SaveFailed
            }
        }
    }

    fun openCreateFromPicker() {
        val weekday = pickerWeekday.value ?: return
        closePicker()
        dialog.value = PlanDialog.Create(weekday)
    }

    fun openEditIdentity(row: PlanExerciseUi) {
        dialog.value = PlanDialog.EditIdentity(
            planRowId = row.planRowId,
            exerciseId = row.exerciseId,
            name = row.name,
            description = row.description,
        )
    }

    fun openEditProgress(row: PlanExerciseUi) {
        dialog.value = PlanDialog.EditProgress(
            planRowId = row.planRowId,
            progress = row.progress,
        )
    }

    fun openDelete(row: PlanExerciseUi) {
        dialog.value = PlanDialog.ConfirmDelete(
            planRowId = row.planRowId,
            exerciseName = row.name,
        )
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun saveNewExercise(name: String, description: String, progress: String) {
        val create = dialog.value as? PlanDialog.Create ?: return
        launchMutation {
            when (repository.createExerciseAndAddToPlan(create.weekday, name, description, progress)) {
                is CreateExerciseResult.Created -> dismissDialog()
                CreateExerciseResult.InvalidName,
                CreateExerciseResult.WorkoutNotFound -> message.value = PlanMessage.SaveFailed
            }
        }
    }

    fun saveIdentity(row: PlanExerciseUi, name: String, description: String) {
        launchMutation {
            if (repository.updateExerciseIdentity(row.exerciseId, name, description)) {
                dismissDialog()
            } else {
                message.value = PlanMessage.SaveFailed
            }
        }
    }

    fun saveProgress(row: PlanExerciseUi, progress: String) {
        launchMutation {
            repository.updatePlanProgress(row.planRowId, progress)
            dismissDialog()
        }
    }

    fun delete(row: PlanExerciseUi) {
        launchMutation {
            repository.deletePlanExercise(row.planRowId)
            dismissDialog()
        }
    }

    fun move(row: PlanExerciseUi, delta: Int) {
        val day = uiState.value.days.firstOrNull { day ->
            day.exercises.any { it.planRowId == row.planRowId }
        } ?: return
        val orderedIds = reorderedIds(day.exercises, row.planRowId, delta) ?: return
        launchMutation {
            repository.reorderPlan(day.weekday, orderedIds)
        }
    }

    fun consumeMessage() {
        message.value = null
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
                message.value = PlanMessage.SaveFailed
            } finally {
                mutationInFlight.set(false)
            }
        }
    }

    class Factory(
        private val repository: TrainingRepository,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlanViewModel::class.java)) {
                return PlanViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
