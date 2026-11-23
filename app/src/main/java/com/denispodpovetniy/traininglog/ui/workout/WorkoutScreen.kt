package com.denispodpovetniy.traininglog.ui.workout

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.TrainingLogApp
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.ui.exercises.CreateExerciseDialog
import com.denispodpovetniy.traininglog.ui.exercises.EditIdentityDialog
import com.denispodpovetniy.traininglog.ui.exercises.ExercisePickerSheet
import com.denispodpovetniy.traininglog.util.DateFormatters
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as TrainingLogApp).container.trainingRepository
    val viewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModel.Factory(workoutId, repository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WorkoutContent(
        state = state,
        onRequestBack = viewModel::requestBack,
        onProgressChange = viewModel::onProgressChange,
        onReload = viewModel::reload,
        onOpenPicker = viewModel::openPicker,
        onClosePicker = viewModel::closePicker,
        onPickerQueryChange = viewModel::setPickerQuery,
        onAddExisting = viewModel::addExisting,
        onOpenCreateFromPicker = viewModel::openCreateFromPicker,
        onOpenEditIdentity = viewModel::openEditIdentity,
        onOpenDeleteExercise = viewModel::openDeleteExercise,
        onDismissDialog = viewModel::dismissDialog,
        onSaveNewExercise = viewModel::saveNewExercise,
        onSaveIdentity = viewModel::saveIdentity,
        onDeleteExercise = viewModel::deleteExercise,
        onMove = viewModel::move,
        onFinish = viewModel::finish,
        onReopen = viewModel::reopen,
        onOpenDeleteWorkout = viewModel::openDeleteWorkout,
        onDeleteWorkout = viewModel::deleteWorkout,
        onConsumeMessage = viewModel::consumeMessage,
        onConsumeExit = viewModel::consumeExit,
        onBack = onBack,
        onFinished = onFinished,
        modifier = modifier,
    )
}

/**
 * Отображает состояние тренировки и отправляет события наружу.
 *
 * Бизнес-логики здесь нет: прогрессия рендерится как есть из [state], все изменения
 * уходят в ViewModel через колбэки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutContent(
    state: WorkoutUiState,
    onRequestBack: () -> Unit,
    onProgressChange: (rowId: Long, text: String) -> Unit,
    onReload: (WorkoutExerciseUi) -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onAddExisting: (Long) -> Unit,
    onOpenCreateFromPicker: () -> Unit,
    onOpenEditIdentity: (WorkoutExerciseUi) -> Unit,
    onOpenDeleteExercise: (WorkoutExerciseUi) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveNewExercise: (name: String, description: String, progress: String) -> Unit,
    onSaveIdentity: (row: WorkoutExerciseUi, name: String, description: String) -> Unit,
    onDeleteExercise: (WorkoutExerciseUi) -> Unit,
    onMove: (row: WorkoutExerciseUi, delta: Int) -> Unit,
    onFinish: () -> Unit,
    onReopen: () -> Unit,
    onOpenDeleteWorkout: () -> Unit,
    onDeleteWorkout: () -> Unit,
    onConsumeMessage: () -> Unit,
    onConsumeExit: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val messageText = state.message?.let { stringResource(it.textRes) }
    val dateTitle = remember(state.date) {
        state.date?.let(DateFormatters::formatDate)
    } ?: stringResource(R.string.workout_title_placeholder)

    LaunchedEffect(state.message) {
        // Забираем сообщение сразу, чтобы snackbar не повторился после
        // пересоздания экрана. Показ запускаем в scope экрана.
        val text = messageText ?: return@LaunchedEffect
        onConsumeMessage()
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    LaunchedEffect(state.exit) {
        val exit = state.exit ?: return@LaunchedEffect
        onConsumeExit()
        when (exit) {
            WorkoutExit.Finished -> onFinished()
            WorkoutExit.Back,
            WorkoutExit.Deleted,
            -> onBack()
        }
    }

    BackHandler(enabled = state.exit == null) {
        onRequestBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WorkoutTopBar(
                title = dateTitle,
                status = state.status,
                onBack = onRequestBack,
                onReopen = onReopen,
                onDeleteWorkout = onOpenDeleteWorkout,
            )
        },
        bottomBar = {
            val status = state.status
            if (status != null) {
                WorkoutBottomBar(status = status, onFinish = onFinish)
            }
        },
        snackbarHost = {
            if (!state.pickerVisible) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.loading -> WorkoutLoading(Modifier.align(Alignment.Center))
                state.notFound -> WorkoutNotFound(
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.exercises.isEmpty()) {
                        item(key = "empty") {
                            EmptyWorkoutMessage()
                        }
                    }

                    itemsIndexed(
                        items = state.exercises,
                        key = { _, row -> row.rowId },
                    ) { index, row ->
                        WorkoutExerciseCard(
                            row = row,
                            isFirst = index == 0,
                            isLast = index == state.exercises.lastIndex,
                            onProgressChange = { text -> onProgressChange(row.rowId, text) },
                            onReload = { onReload(row) },
                            onEditIdentity = { onOpenEditIdentity(row) },
                            onMoveUp = { onMove(row, -1) },
                            onMoveDown = { onMove(row, 1) },
                            onDelete = { onOpenDeleteExercise(row) },
                        )
                    }

                    item(key = "add") {
                        OutlinedButton(
                            onClick = onOpenPicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.workout_add_exercise))
                        }
                    }
                }
            }
        }
    }

    ExercisePickerSheet(
        visible = state.pickerVisible,
        query = state.pickerQuery,
        results = state.pickerResults,
        onQueryChange = onPickerQueryChange,
        onSelect = onAddExisting,
        onCreateNew = onOpenCreateFromPicker,
        onDismiss = onClosePicker,
        snackbarHostState = snackbarHostState,
    )

    when (val dialog = state.dialog) {
        is WorkoutDialog.Create -> CreateExerciseDialog(
            onDismiss = onDismissDialog,
            onSave = onSaveNewExercise,
        )

        is WorkoutDialog.EditIdentity -> {
            val row = state.exercises.firstOrNull { it.rowId == dialog.rowId }
            EditIdentityDialog(
                initialName = dialog.name,
                initialDescription = dialog.description,
                onDismiss = onDismissDialog,
                onSave = { name, description ->
                    row?.let { onSaveIdentity(it, name, description) }
                },
            )
        }

        is WorkoutDialog.ConfirmDeleteExercise -> {
            val row = state.exercises.firstOrNull { it.rowId == dialog.rowId }
            ConfirmDeleteExerciseDialog(
                exerciseName = dialog.exerciseName,
                onDismiss = onDismissDialog,
                onConfirm = { row?.let(onDeleteExercise) },
            )
        }

        WorkoutDialog.ConfirmDeleteWorkout -> ConfirmDeleteWorkoutDialog(
            onDismiss = onDismissDialog,
            onConfirm = onDeleteWorkout,
        )

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(
    title: String,
    status: WorkoutStatus?,
    onBack: () -> Unit,
    onReopen: () -> Unit,
    onDeleteWorkout: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.workout_back),
                )
            }
        },
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status != null) {
                    Spacer(Modifier.height(2.dp))
                    WorkoutStatusLabel(status)
                }
            }
        },
        actions = {
            if (status != null) {
                WorkoutActionsMenu(
                    status = status,
                    onReopen = onReopen,
                    onDeleteWorkout = onDeleteWorkout,
                )
            }
        },
    )
}

@Composable
private fun WorkoutStatusLabel(status: WorkoutStatus) {
    when (status) {
        WorkoutStatus.IN_PROGRESS -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.workout_status_draft),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        WorkoutStatus.COMPLETED -> Text(
            text = stringResource(R.string.workout_status_completed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkoutActionsMenu(
    status: WorkoutStatus,
    onReopen: () -> Unit,
    onDeleteWorkout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.workout_actions),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (status == WorkoutStatus.COMPLETED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workout_action_reopen)) },
                    onClick = {
                        expanded = false
                        onReopen()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.workout_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDeleteWorkout()
                },
            )
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    row: WorkoutExerciseUi,
    isFirst: Boolean,
    isLast: Boolean,
    onProgressChange: (String) -> Unit,
    onReload: () -> Unit,
    onEditIdentity: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEditIdentity)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    if (row.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = row.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                ExerciseRowMenu(
                    canMoveUp = !isFirst,
                    canMoveDown = !isLast,
                    onEditIdentity = onEditIdentity,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDelete = onDelete,
                )
            }

            if (row.planProgress.isNotBlank()) {
                val planLabel = stringResource(R.string.workout_plan_label)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildAnnotatedString {
                        append(planLabel)
                        append(" ")
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(row.planProgress)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.progress,
                    onValueChange = onProgressChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.workout_progress_label)) },
                    minLines = 1,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )

                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onReload,
                    enabled = canSyncProgressToPlan(row.progress),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.workout_sync_to_plan))
                }
            }
        }
    }
}

@Composable
private fun ExerciseRowMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditIdentity: () -> Unit,
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
                contentDescription = stringResource(R.string.workout_exercise_actions),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_action_edit_identity)) },
                onClick = {
                    expanded = false
                    onEditIdentity()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_action_move_up)) },
                onClick = {
                    expanded = false
                    onMoveUp()
                },
                enabled = canMoveUp,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_action_move_down)) },
                onClick = {
                    expanded = false
                    onMoveDown()
                },
                enabled = canMoveDown,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.workout_action_delete_exercise),
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
private fun WorkoutBottomBar(
    status: WorkoutStatus,
    onFinish: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (status == WorkoutStatus.IN_PROGRESS) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.workout_finish))
                }
            } else {
                Text(
                    text = stringResource(R.string.workout_completed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.workout_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkoutNotFound(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.workout_not_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.workout_back))
        }
    }
}

@Composable
private fun EmptyWorkoutMessage() {
    Text(
        text = stringResource(R.string.workout_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
    )
}

@Composable
private fun ConfirmDeleteExerciseDialog(
    exerciseName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val text = if (exerciseName.isBlank()) {
        stringResource(R.string.workout_delete_exercise_text_no_name)
    } else {
        stringResource(R.string.workout_delete_exercise_text, exerciseName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_delete_exercise_title)) },
        text = { Text(text) },
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

@Composable
private fun ConfirmDeleteWorkoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_delete_workout_title)) },
        text = { Text(stringResource(R.string.workout_delete_workout_text)) },
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
internal val WorkoutMessage.textRes: Int
    get() = when (this) {
        WorkoutMessage.DuplicateExercise -> R.string.workout_message_duplicate
        WorkoutMessage.ExerciseNotFound -> R.string.workout_message_not_found
        WorkoutMessage.SaveFailed -> R.string.workout_message_save_failed
        WorkoutMessage.SyncedPlanAndPreset -> R.string.workout_message_synced_plan_and_preset
        WorkoutMessage.SyncedPresetOnly -> R.string.workout_message_synced_preset_only
    }
