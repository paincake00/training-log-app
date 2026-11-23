package com.denispodpovetniy.traininglog.ui.plan

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.TrainingLogApp
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.ui.components.LongPressDescription
import com.denispodpovetniy.traininglog.ui.exercises.CreateExerciseDialog
import com.denispodpovetniy.traininglog.ui.exercises.EditIdentityDialog
import com.denispodpovetniy.traininglog.ui.exercises.EditProgressDialog
import com.denispodpovetniy.traininglog.ui.exercises.ExercisePickerSheet
import java.util.Locale
import kotlinx.coroutines.launch

private val RussianLocale: Locale = Locale.forLanguageTag("ru")

@Composable
fun PlanScreen(
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as TrainingLogApp).container.trainingRepository
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PlanContent(
        state = state,
        onOpenPicker = viewModel::openPicker,
        onClosePicker = viewModel::closePicker,
        onPickerQueryChange = viewModel::setPickerQuery,
        onAddExisting = viewModel::addExisting,
        onOpenCreateFromPicker = viewModel::openCreateFromPicker,
        onOpenEditIdentity = viewModel::openEditIdentity,
        onOpenEditProgress = viewModel::openEditProgress,
        onOpenDelete = viewModel::openDelete,
        onDismissDialog = viewModel::dismissDialog,
        onSaveNewExercise = viewModel::saveNewExercise,
        onSaveIdentity = viewModel::saveIdentity,
        onSaveProgress = viewModel::saveProgress,
        onDelete = viewModel::delete,
        onMove = viewModel::move,
        onStartOrOpenTodayWorkout = viewModel::startOrOpenTodayWorkout,
        onConsumeOpenWorkout = viewModel::consumeOpenWorkout,
        onOpenWorkout = onOpenWorkout,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun PlanContent(
    state: PlanUiState,
    onOpenPicker: (Int) -> Unit,
    onClosePicker: () -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onAddExisting: (Long) -> Unit,
    onOpenCreateFromPicker: () -> Unit,
    onOpenEditIdentity: (PlanExerciseUi) -> Unit,
    onOpenEditProgress: (PlanExerciseUi) -> Unit,
    onOpenDelete: (PlanExerciseUi) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveNewExercise: (name: String, description: String, progress: String) -> Unit,
    onSaveIdentity: (row: PlanExerciseUi, name: String, description: String) -> Unit,
    onSaveProgress: (row: PlanExerciseUi, progress: String) -> Unit,
    onDelete: (PlanExerciseUi) -> Unit,
    onMove: (row: PlanExerciseUi, delta: Int) -> Unit,
    onStartOrOpenTodayWorkout: () -> Unit,
    onConsumeOpenWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val weekdays = stringArrayResource(R.array.weekdays)
    val messageText = state.message?.let { stringResource(it.textRes) }
    val allExercises = remember(state.days) { state.days.flatMap { it.exercises } }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.days, key = { it.weekday }) { day ->
                PlanDayCard(
                    day = day,
                    todayWorkout = state.todayWorkout,
                    weekdays = weekdays,
                    onAdd = { onOpenPicker(day.weekday) },
                    onEditIdentity = onOpenEditIdentity,
                    onEditProgress = onOpenEditProgress,
                    onDelete = onOpenDelete,
                    onMove = onMove,
                    onStartOrOpenTodayWorkout = onStartOrOpenTodayWorkout,
                )
            }
        }

        if (state.pickerWeekday == null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    ExercisePickerSheet(
        visible = state.pickerWeekday != null,
        query = state.pickerQuery,
        results = state.pickerResults,
        onQueryChange = onPickerQueryChange,
        onSelect = onAddExisting,
        onCreateNew = onOpenCreateFromPicker,
        onDismiss = onClosePicker,
        snackbarHostState = snackbarHostState,
    )

    when (val dialog = state.dialog) {
        is PlanDialog.Create -> CreateExerciseDialog(
            onDismiss = onDismissDialog,
            onSave = onSaveNewExercise,
        )

        is PlanDialog.EditIdentity -> {
            val row = allExercises.firstOrNull { it.planRowId == dialog.planRowId }
            EditIdentityDialog(
                initialName = dialog.name,
                initialDescription = dialog.description,
                onDismiss = onDismissDialog,
                onSave = { name, description -> row?.let { onSaveIdentity(it, name, description) } },
            )
        }

        is PlanDialog.EditProgress -> {
            val row = allExercises.firstOrNull { it.planRowId == dialog.planRowId }
            EditProgressDialog(
                initialProgress = dialog.progress,
                onDismiss = onDismissDialog,
                onSave = { progress -> row?.let { onSaveProgress(it, progress) } },
            )
        }

        is PlanDialog.ConfirmDelete -> {
            val row = allExercises.firstOrNull { it.planRowId == dialog.planRowId }
            ConfirmDeleteDialog(
                onDismiss = onDismissDialog,
                onConfirm = { row?.let(onDelete) },
            )
        }

        null -> Unit
    }
}

@Composable
private fun PlanDayCard(
    day: PlanDayUi,
    todayWorkout: TodayWorkoutUi?,
    weekdays: Array<String>,
    onAdd: () -> Unit,
    onEditIdentity: (PlanExerciseUi) -> Unit,
    onEditProgress: (PlanExerciseUi) -> Unit,
    onDelete: (PlanExerciseUi) -> Unit,
    onMove: (PlanExerciseUi, Int) -> Unit,
    onStartOrOpenTodayWorkout: () -> Unit,
) {
    val userColors = MaterialTheme.colorScheme
    val border = if (day.isToday) {
        BorderStroke(2.dp, userColors.primary)
    } else {
        BorderStroke(1.dp, userColors.outlineVariant)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = userColors.surface,
        border = border,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weekdayTitle(day, weekdays),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day.isToday) userColors.primary else userColors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.plan_exercise_count,
                        day.exercises.size,
                        day.exercises.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = userColors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(12.dp))

            if (day.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.plan_day_rest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = userColors.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    day.exercises.forEachIndexed { index, exercise ->
                        PlanExerciseRow(
                            exercise = exercise,
                            isFirst = index == 0,
                            isLast = index == day.exercises.lastIndex,
                            onEditIdentity = { onEditIdentity(exercise) },
                            onEditProgress = { onEditProgress(exercise) },
                            onMoveUp = { onMove(exercise, -1) },
                            onMoveDown = { onMove(exercise, 1) },
                            onDelete = { onDelete(exercise) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(4.dp))

            TextButton(
                onClick = onAdd,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.plan_add))
            }

            if (day.isToday) {
                Spacer(Modifier.size(4.dp))
                TodayWorkoutCta(
                    workout = todayWorkout,
                    onClick = onStartOrOpenTodayWorkout,
                )
            }
        }
    }
}

@Composable
private fun TodayWorkoutCta(
    workout: TodayWorkoutUi?,
    onClick: () -> Unit,
) {
    val completed = workout?.status == WorkoutStatus.COMPLETED
    val label = when (workout?.status) {
        null -> R.string.plan_workout_start
        WorkoutStatus.IN_PROGRESS -> R.string.plan_workout_continue
        WorkoutStatus.COMPLETED -> R.string.plan_workout_open
    }

    if (completed) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(label))
        }
    } else {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(label))
        }
    }
}

@Composable
private fun weekdayTitle(day: PlanDayUi, weekdays: Array<String>): String {
    val name = weekdays.getOrElse(day.weekday - 1) { "" }
    return if (day.isToday) {
        stringResource(R.string.plan_today_header, name)
    } else {
        name.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(RussianLocale) else char.toString()
        }
    }
}

@Composable
private fun PlanExerciseRow(
    exercise: PlanExerciseUi,
    isFirst: Boolean,
    isLast: Boolean,
    onEditIdentity: () -> Unit,
    onEditProgress: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(modifier = Modifier.fillMaxWidth()) {
        LongPressDescription(
            description = exercise.description,
            modifier = Modifier.weight(1f),
        ) {
            Column {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                if (exercise.progress.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = exercise.progress,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (exercise.description.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = exercise.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ExerciseActionsMenu(
            canMoveUp = !isFirst,
            canMoveDown = !isLast,
            onEditIdentity = onEditIdentity,
            onEditProgress = onEditProgress,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ExerciseActionsMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditIdentity: () -> Unit,
    onEditProgress: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.plan_exercise_actions),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_action_edit_identity)) },
                onClick = {
                    expanded = false
                    onEditIdentity()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_action_edit_progress)) },
                onClick = {
                    expanded = false
                    onEditProgress()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_action_move_up)) },
                onClick = {
                    expanded = false
                    onMoveUp()
                },
                enabled = canMoveUp,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_action_move_down)) },
                onClick = {
                    expanded = false
                    onMoveDown()
                },
                enabled = canMoveDown,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.plan_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_confirm_title)) },
        text = { Text(stringResource(R.string.dialog_delete_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.dialog_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@get:StringRes
private val PlanMessage.textRes: Int
    get() = when (this) {
        PlanMessage.DuplicateExercise -> R.string.plan_message_duplicate
        PlanMessage.ExerciseNotFound -> R.string.plan_message_not_found
        PlanMessage.SaveFailed -> R.string.plan_message_save_failed
    }
