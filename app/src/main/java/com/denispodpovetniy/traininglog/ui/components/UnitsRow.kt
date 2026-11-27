package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.util.ProgressCodec

/**
 * Строка единиц измерения упражнения: плитки через `/`, в конце — «+».
 *
 * Единицы общие для всех тренировок. Тап по плитке открывает меню
 * «Переименовать» / «Удалить», «+» — встроенное поле ввода с подсказками.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitsRow(
    units: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRename: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draftMode: Boolean = false,
    showLabel: Boolean = true,
    onRenameInline: (Int, String) -> Unit = { _, _ -> },
) {
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<Int?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(adding) {
        if (adding) focusRequester.requestFocus()
    }

    val submit: (String?) -> Unit = { suggestion ->
        val candidate = suggestion ?: draft
        val excludeIndex = editingIndex
        val existing = if (excludeIndex == null) {
            units
        } else {
            units.filterIndexed { index, _ -> index != excludeIndex }
        }
        val normalized = ProgressCodec.normalizeUnit(candidate, existing)
        when {
            normalized != null && excludeIndex != null -> {
                onRenameInline(excludeIndex, normalized)
                adding = false
                editingIndex = null
                draft = ""
                errorText = null
            }

            normalized != null -> {
                onAdd(normalized)
                adding = false
                draft = ""
                errorText = null
            }

            units.any { it.equals(candidate.trim(), ignoreCase = true) } ->
                errorText = R.string.units_name_duplicate

            else -> errorText = R.string.units_name_invalid
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showLabel) {
            Text(
                text = stringResource(R.string.units_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            units.forEachIndexed { index, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitChip(
                        name = name,
                        enabled = enabled,
                        onRename = {
                            if (draftMode) {
                                editingIndex = index
                                draft = name
                                adding = true
                                errorText = null
                            } else {
                                onRename(index)
                            }
                        },
                        onRemove = { onRemove(index) },
                    )
                    if (index < units.lastIndex) {
                        // Разделитель внутри элемента, чтобы при переносе строки
                        // он не оказался один в конце предыдущей строки.
                        Text(
                            text = ProgressCodec.VALUE_SEPARATOR,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            if (enabled) {
                AddTileButton(
                    contentDescription = stringResource(R.string.units_add),
                    onClick = {
                        editingIndex = null
                        draft = ""
                        errorText = null
                        adding = true
                    },
                )
            }
        }

        if (units.isEmpty() && !adding) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.units_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (adding) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    errorText = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.units_name_label)) },
                singleLine = true,
                isError = errorText != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit(null) }),
                trailingIcon = {
                    TextButton(onClick = { submit(null) }) {
                        Text(stringResource(R.string.units_confirm))
                    }
                },
            )
            errorText?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val available = suggestions.filter {
                ProgressCodec.normalizeUnit(it, units) != null
            }
            if (available.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.units_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    available.take(MAX_SUGGESTIONS).forEach { suggestion ->
                        SuggestionChip(
                            onClick = { submit(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.units_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnitChip(
    name: String,
    enabled: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(enabled = enabled) { menuVisible = true },
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { menuVisible = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unit_action_rename)) },
                onClick = {
                    menuVisible = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unit_action_delete)) },
                onClick = {
                    menuVisible = false
                    onRemove()
                },
            )
        }
    }
}

private const val MAX_SUGGESTIONS = 8

/**
 * Диалог редактирования единиц упражнения (план, пресеты).
 *
 * Изменения применяются сразу: единицы общие для всех тренировок.
 */
@Composable
fun UnitsEditorDialog(
    units: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRename: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Int?>(null) }
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.units_label)) },
        text = {
            UnitsRow(
                units = units,
                suggestions = suggestions,
                showLabel = false,
                onAdd = onAdd,
                onRename = { renaming = it },
                onRemove = { pendingRemoval = it },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_done))
            }
        },
    )

    val renameIndex = renaming
    if (renameIndex != null && renameIndex in units.indices) {
        RenameUnitDialog(
            initialName = units[renameIndex],
            existingUnits = units,
            onDismiss = { renaming = null },
            onSave = { name ->
                onRename(renameIndex, name)
                renaming = null
            },
        )
    }

    val removeIndex = pendingRemoval
    if (removeIndex != null && removeIndex in units.indices) {
        ConfirmRemoveUnitDialog(
            unitName = units[removeIndex],
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                onRemove(removeIndex)
                pendingRemoval = null
            },
        )
    }
}

/** Черновик единиц для диалога создания: изменения живут только до сохранения. */
@Composable
fun DraftUnitsEditor(
    units: List<String>,
    suggestions: List<String>,
    onUnitsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    UnitsRow(
        units = units,
        suggestions = suggestions,
        onAdd = { name -> onUnitsChange(units + name) },
        onRename = {},
        onRemove = { index ->
            onUnitsChange(units.filterIndexed { i, _ -> i != index })
        },
        modifier = modifier,
        draftMode = true,
        onRenameInline = { index, name ->
            onUnitsChange(units.toMutableList().apply { this[index] = name })
        },
    )
}
