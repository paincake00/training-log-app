package com.denispodpovetniy.traininglog.ui.log

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.TrainingLogApp
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.util.DateFormatters
import kotlinx.coroutines.launch

@Composable
fun LogScreen(
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as TrainingLogApp).container.trainingRepository
    val viewModel: LogViewModel = viewModel(factory = LogViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LogContent(
        state = state,
        onStartOrOpenTodayWorkout = viewModel::startOrOpenTodayWorkout,
        onConsumeOpenWorkout = viewModel::consumeOpenWorkout,
        onOpenWorkout = onOpenWorkout,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun LogContent(
    state: LogUiState,
    onStartOrOpenTodayWorkout: () -> Unit,
    onConsumeOpenWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val messageText = state.message?.let { stringResource(it.textRes) }

    LaunchedEffect(state.message) {
        // Забираем сообщение сразу, чтобы snackbar не повторился после
        // пересоздания экрана. Показ запускаем в scope экрана.
        val text = messageText ?: return@LaunchedEffect
        onConsumeMessage()
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    LaunchedEffect(state.openWorkoutId) {
        val workoutId = state.openWorkoutId ?: return@LaunchedEffect
        onConsumeOpenWorkout()
        onOpenWorkout(workoutId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.loading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "today-cta") {
                    TodayWorkoutCta(
                        workout = state.todayWorkout,
                        onClick = onStartOrOpenTodayWorkout,
                    )
                }

                if (state.workouts.isEmpty()) {
                    item(key = "empty") {
                        LogEmptyState()
                    }
                } else {
                    items(state.workouts, key = { it.workoutId }) { workout ->
                        WorkoutCard(
                            workout = workout,
                            onClick = { onOpenWorkout(workout.workoutId) },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun TodayWorkoutCta(
    workout: TodayWorkout?,
    onClick: () -> Unit,
) {
    val label = when (workout?.status) {
        null -> R.string.log_workout_start
        WorkoutStatus.IN_PROGRESS -> R.string.log_workout_continue
        WorkoutStatus.COMPLETED -> R.string.log_workout_open
    }
    val buttonModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)

    when (workout?.status) {
        null -> Button(onClick = onClick, modifier = buttonModifier) {
            Text(stringResource(label))
        }

        WorkoutStatus.IN_PROGRESS -> FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
        ) {
            Text(stringResource(label))
        }

        WorkoutStatus.COMPLETED -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
        ) {
            Text(stringResource(label))
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutLogUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateFormatters.formatDate(workout.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (workout.status == WorkoutStatus.IN_PROGRESS) {
                    Spacer(Modifier.width(8.dp))
                    DraftChip()
                }
            }

            Spacer(Modifier.size(12.dp))

            if (workout.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.log_no_exercises),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                workout.exercises.forEachIndexed { index, exercise ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = colors.outlineVariant,
                        )
                    }
                    LogExerciseRow(exercise)
                }
            }
        }
    }
}

@Composable
private fun DraftChip() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.log_draft),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LogExerciseRow(exercise: LogExerciseUi) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (exercise.units.isNotEmpty() && exercise.progress.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = exercise.units.joinToString(" / "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        if (exercise.progress.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = exercise.progress,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp,
                ),
                color = colors.primary,
                softWrap = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (exercise.description.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = exercise.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LogEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tab_log),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.log_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@get:StringRes
private val LogMessage.textRes: Int
    get() = when (this) {
        LogMessage.SaveFailed -> R.string.log_message_save_failed
    }
