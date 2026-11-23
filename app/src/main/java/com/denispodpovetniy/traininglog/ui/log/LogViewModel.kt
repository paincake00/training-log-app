package com.denispodpovetniy.traininglog.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.denispodpovetniy.traininglog.data.repository.TrainingRepository
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Лог».
 *
 * Единственное состояние — [uiState]. Список тренировок приходит из
 * [TrainingRepository.observeLog]; создание тренировки на сегодня идёт через
 * идемпотентный [TrainingRepository.createWorkoutForDate].
 */
class LogViewModel(
    private val repository: TrainingRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val openWorkoutId = MutableStateFlow<Long?>(null)
    private val message = MutableStateFlow<LogMessage?>(null)
    private val mutationInFlight = AtomicBoolean(false)

    private val logState = repository.observeLog()
        .map { rows -> buildLogUiState(rows, today()) }

    val uiState: StateFlow<LogUiState> = combine(
        logState,
        openWorkoutId,
        message,
    ) { state, workoutToOpen, currentMessage ->
        state.copy(
            openWorkoutId = workoutToOpen,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogUiState(loading = true),
    )

    /**
     * Открывает сегодняшнюю тренировку или создаёт её, если её ещё нет.
     *
     * Повторные нажатия до завершения операции отбрасываются. Тренировки
     * на прошлые и будущие даты не создаются.
     */
    fun startOrOpenTodayWorkout() {
        if (!mutationInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                // Метод идемпотентен: для существующей тренировки вернёт её id,
                // для новой — создаст. Не читаем кэш uiState, чтобы не открыть
                // вчерашнюю тренировку после смены даты.
                openWorkoutId.value = repository.createWorkoutForDate(today())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = LogMessage.SaveFailed
            } finally {
                mutationInFlight.set(false)
            }
        }
    }

    /** Забирает одноразовое событие открытия тренировки. */
    fun consumeOpenWorkout() {
        openWorkoutId.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    class Factory(
        private val repository: TrainingRepository,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
                return LogViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
