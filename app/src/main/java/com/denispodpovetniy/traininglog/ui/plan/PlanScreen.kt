package com.denispodpovetniy.traininglog.ui.plan

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.BuildConfig
import com.denispodpovetniy.traininglog.TrainingLogApp
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus
import com.denispodpovetniy.traininglog.ui.components.LongPressDescription
import com.denispodpovetniy.traininglog.ui.exercises.CreateExerciseDialog
import com.denispodpovetniy.traininglog.ui.exercises.EditIdentityDialog
import com.denispodpovetniy.traininglog.ui.exercises.EditProgressDialog
import com.denispodpovetniy.traininglog.ui.exercises.ExercisePickerSheet
import com.denispodpovetniy.traininglog.ui.components.ProgressSummary
import com.denispodpovetniy.traininglog.ui.components.UnitsEditorDialog
import com.denispodpovetniy.traininglog.util.PlanFileCodec
import com.denispodpovetniy.traininglog.util.PlanFileException
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RussianLocale: Locale = Locale.forLanguageTag("ru")

/** MIME-тип файла плана. */
private const val PLAN_FILE_MIME = "application/json"

/** Что принимает системный выбор файла: JSON и файлы, которые часть провайдеров отдаёт иначе. */
private val PLAN_FILE_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Читает выбранный файл целиком.
 *
 * @throws PlanFileException с причиной [PlanFileException.Reason.TOO_LARGE],
 * если файл больше [PlanFileCodec.MAX_FILE_BYTES].
 */
private fun Context.readPlanFile(uri: Uri): String {
    val bytes = contentResolver.openInputStream(uri)?.use { stream ->
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            output.write(chunk, 0, read)
            if (output.size() > PlanFileCodec.MAX_FILE_BYTES) {
                throw PlanFileException(PlanFileException.Reason.TOO_LARGE)
            }
        }
        output.toByteArray()
    } ?: throw PlanFileException(PlanFileException.Reason.CORRUPT)
    return String(bytes, Charsets.UTF_8)
}

@Composable
fun PlanScreen(
    planActions: PlanActionsState,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as TrainingLogApp).container.trainingRepository
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var exportLaunched by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Колбэк системного диалога живёт дольше композиции при повороте экрана:
    // берём актуальный запрос из ViewModel, а не из замыкания.
    val currentExport by rememberUpdatedState(state.exportRequest)

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PLAN_FILE_MIME),
    ) { uri ->
        val request = currentExport
        if (uri == null || request == null) {
            viewModel.onExportFinished(written = false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(request.content.toByteArray(Charsets.UTF_8))
                    } ?: error("Пустой поток записи")
                }.isSuccess
            }
            viewModel.onExportFinished(written)
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { context.readPlanFile(uri) } }
            result.fold(
                onSuccess = { viewModel.importFromFile(it) },
                onFailure = { error ->
                    viewModel.rejectImport(
                        (error as? PlanFileException)?.reason ?: PlanFileException.Reason.CORRUPT,
                    )
                },
            )
        }
    }

    LaunchedEffect(state.exportRequest) {
        val request = state.exportRequest
        if (request == null) {
            exportLaunched = false
            return@LaunchedEffect
        }
        // После поворота эффект запускается снова: диалог уже открыт, второй не нужен.
        if (exportLaunched) return@LaunchedEffect
        exportLaunched = true
        createDocument.launch(request.fileName)
    }

    val onExport = rememberUpdatedState<() -> Unit> {
        viewModel.requestExport(BuildConfig.VERSION_NAME)
    }
    val onImport = rememberUpdatedState<() -> Unit> {
        openDocument.launch(PLAN_FILE_MIME_TYPES)
    }
    DisposableEffect(planActions) {
        planActions.onExport = { onExport.value() }
        planActions.onImport = { onImport.value() }
        onDispose { planActions.clear() }
    }

    PlanContent(
        state = state,
        onOpenPicker = viewModel::openPicker,
        onClosePicker = viewModel::closePicker,
        onPickerQueryChange = viewModel::setPickerQuery,
        onAddExisting = viewModel::addExisting,
        onOpenCreateFromPicker = viewModel::openCreateFromPicker,
        onOpenEditIdentity = viewModel::openEditIdentity,
        onOpenEditProgress = viewModel::openEditProgress,
        onOpenUnits = viewModel::openUnits,
        onAddUnit = viewModel::addUnit,
        onRenameUnit = viewModel::renameUnit,
        onRemoveUnit = viewModel::removeUnit,
        onOpenDelete = viewModel::openDelete,
        onDismissDialog = viewModel::dismissDialog,
        onSaveNewExercise = viewModel::saveNewExercise,
        onSaveIdentity = viewModel::saveIdentity,
        onSaveProgress = viewModel::saveProgress,
        onConfirmImport = viewModel::confirmImport,
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
    onOpenUnits: (PlanExerciseUi) -> Unit,
    onAddUnit: (exerciseId: Long, name: String) -> Unit,
    onRenameUnit: (exerciseId: Long, index: Int, name: String) -> Unit,
    onRemoveUnit: (exerciseId: Long, index: Int) -> Unit,
    onOpenDelete: (PlanExerciseUi) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveNewExercise: (name: String, description: String, units: List<String>, progress: String) -> Unit,
    onSaveIdentity: (row: PlanExerciseUi, name: String, description: String) -> Unit,
    onSaveProgress: (row: PlanExerciseUi, progress: String) -> Unit,
    onConfirmImport: () -> Unit,
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
    val messageText = state.message?.let { message ->
        when (message) {
            PlanMessage.DuplicateExercise -> stringResource(R.string.plan_message_duplicate)
            PlanMessage.ExerciseNotFound -> stringResource(R.string.plan_message_not_found)
            PlanMessage.SaveFailed -> stringResource(R.string.plan_message_save_failed)
            PlanMessage.UnitRemoved -> stringResource(R.string.workout_message_unit_removed)
            PlanMessage.Exported -> stringResource(R.string.plan_export_saved)
            PlanMessage.ExportFailed -> stringResource(R.string.plan_export_failed)
            is PlanMessage.Imported -> stringResource(
                R.string.plan_import_done,
                message.summary.addedExercises,
                message.summary.updatedExercises,
                message.summary.planRows,
            )

            is PlanMessage.ImportFailed -> stringResource(message.reason.textRes)
        }
    }
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
                    onEditUnits = onOpenUnits,
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
            suggestions = state.unitSuggestions,
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
                units = row?.units.orEmpty(),
                onDismiss = onDismissDialog,
                onSave = { progress -> row?.let { onSaveProgress(it, progress) } },
            )
        }

        is PlanDialog.Units -> {
            val row = allExercises.firstOrNull { it.exerciseId == dialog.exerciseId }
            if (row != null) {
                UnitsEditorDialog(
                    units = row.units,
                    suggestions = state.unitSuggestions,
                    onAdd = { name -> onAddUnit(row.exerciseId, name) },
                    onRename = { index, name -> onRenameUnit(row.exerciseId, index, name) },
                    onRemove = { index -> onRemoveUnit(row.exerciseId, index) },
                    onDismiss = onDismissDialog,
                )
            }
        }

        is PlanDialog.ConfirmImport -> PlanImportDialog(
            summary = dialog.summary,
            onDismiss = onDismissDialog,
            onConfirm = onConfirmImport,
        )

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
    onEditUnits: (PlanExerciseUi) -> Unit,
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
                            onEditUnits = { onEditUnits(exercise) },
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
    onEditUnits: () -> Unit,
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
                if (exercise.progress.isNotBlank() || exercise.units.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    ProgressSummary(
                        units = exercise.units,
                        progress = exercise.progress,
                        valueColor = colors.primary,
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
            onEditUnits = onEditUnits,
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
    onEditUnits: () -> Unit,
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
                text = { Text(stringResource(R.string.plan_action_edit_units)) },
                onClick = {
                    expanded = false
                    onEditUnits()
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
private val PlanFileException.Reason.textRes: Int
    get() = when (this) {
        PlanFileException.Reason.CORRUPT -> R.string.plan_import_corrupt
        PlanFileException.Reason.VERSION -> R.string.plan_import_version
        PlanFileException.Reason.DATA -> R.string.plan_import_invalid
        PlanFileException.Reason.TOO_LARGE -> R.string.plan_import_too_large
    }
