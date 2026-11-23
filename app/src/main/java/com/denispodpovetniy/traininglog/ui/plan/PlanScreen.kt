package com.denispodpovetniy.traininglog.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.TrainingLogApp
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import java.util.Locale
import kotlinx.coroutines.launch

private val RussianLocale: Locale = Locale.forLanguageTag("ru")

@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.days, key = { it.weekday }) { day ->
                PlanDayCard(
                    day = day,
                    weekdays = weekdays,
                    onAdd = { onOpenPicker(day.weekday) },
                    onEditIdentity = onOpenEditIdentity,
                    onEditProgress = onOpenEditProgress,
                    onDelete = onOpenDelete,
                    onMove = onMove,
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

    if (state.pickerWeekday != null) {
        ExercisePickerSheet(
            query = state.pickerQuery,
            results = state.pickerResults,
            snackbarHostState = snackbarHostState,
            onQueryChange = onPickerQueryChange,
            onSelect = onAddExisting,
            onCreateNew = onOpenCreateFromPicker,
            onDismiss = onClosePicker,
        )
    }

    when (val dialog = state.dialog) {
        is PlanDialog.Create -> CreateExerciseDialog(
            onDismiss = onDismissDialog,
            onConfirm = onSaveNewExercise,
        )

        is PlanDialog.EditIdentity -> {
            val row = allExercises.firstOrNull { it.planRowId == dialog.planRowId }
            EditIdentityDialog(
                initialName = dialog.name,
                initialDescription = dialog.description,
                onDismiss = onDismissDialog,
                onConfirm = { name, description -> row?.let { onSaveIdentity(it, name, description) } },
            )
        }

        is PlanDialog.EditProgress -> {
            val row = allExercises.firstOrNull { it.planRowId == dialog.planRowId }
            EditProgressDialog(
                initialProgress = dialog.progress,
                onDismiss = onDismissDialog,
                onConfirm = { progress -> row?.let { onSaveProgress(it, progress) } },
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
    weekdays: Array<String>,
    onAdd: () -> Unit,
    onEditIdentity: (PlanExerciseUi) -> Unit,
    onEditProgress: (PlanExerciseUi) -> Unit,
    onDelete: (PlanExerciseUi) -> Unit,
    onMove: (PlanExerciseUi, Int) -> Unit,
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
        Column(modifier = Modifier.weight(1f)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    query: String,
    results: List<ExerciseEntity>,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.picker_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.picker_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )

            Spacer(Modifier.size(8.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(results, key = { it.id }) { exercise ->
                        ExercisePickerRow(
                            exercise = exercise,
                            onClick = { onSelect(exercise.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(8.dp))

            OutlinedButton(
                onClick = onCreateNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.picker_create_new))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ExercisePickerRow(
    exercise: ExerciseEntity,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (exercise.description.isNotBlank()) {
            Spacer(Modifier.size(2.dp))
            Text(
                text = exercise.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (exercise.lastProgress.isNotBlank()) {
            Spacer(Modifier.size(2.dp))
            Text(
                text = exercise.lastProgress,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, progress: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var progress by rememberSaveable { mutableStateOf("") }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_create_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NameField(
                    value = name,
                    onValueChange = { name = it },
                )
                DescriptionField(
                    value = description,
                    onValueChange = { description = it },
                )
                ProgressField(
                    value = progress,
                    onValueChange = { progress = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, progress) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.dialog_add))
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
private fun EditIdentityDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_identity_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.dialog_identity_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NameField(
                    value = name,
                    onValueChange = { name = it },
                )
                DescriptionField(
                    value = description,
                    onValueChange = { description = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.dialog_save))
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
private fun EditProgressDialog(
    initialProgress: String,
    onDismiss: () -> Unit,
    onConfirm: (progress: String) -> Unit,
) {
    var progress by rememberSaveable { mutableStateOf(initialProgress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_progress_title)) },
        text = {
            ProgressField(
                value = progress,
                onValueChange = { progress = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(progress) }) {
                Text(stringResource(R.string.dialog_save))
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

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val isBlank = value.isBlank()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.dialog_name_label)) },
        singleLine = true,
        isError = isBlank,
        supportingText = if (isBlank) {
            { Text(stringResource(R.string.dialog_name_required)) }
        } else {
            null
        },
    )
}

@Composable
private fun DescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.dialog_description_label)) },
        minLines = 2,
        maxLines = 4,
    )
}

@Composable
private fun ProgressField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.dialog_progress_label)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        minLines = 1,
        maxLines = 4,
    )
}

@get:StringRes
private val PlanMessage.textRes: Int
    get() = when (this) {
        PlanMessage.DuplicateExercise -> R.string.plan_message_duplicate
        PlanMessage.ExerciseNotFound -> R.string.plan_message_not_found
        PlanMessage.SaveFailed -> R.string.plan_message_save_failed
    }
