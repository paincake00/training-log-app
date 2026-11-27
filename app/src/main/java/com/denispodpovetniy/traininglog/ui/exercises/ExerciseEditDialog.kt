package com.denispodpovetniy.traininglog.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.ui.components.EditorDialog
import com.denispodpovetniy.traininglog.ui.components.SetsRow
import com.denispodpovetniy.traininglog.util.ProgressCodec

/**
 * Диалог редактирования названия и описания пресета.
 *
 * Название и описание глобальные: изменение видно в плане и во всех тренировках,
 * о чём диалог явно предупреждает. Пока название пустое, сохранение недоступно.
 */
@Composable
fun EditIdentityDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String) -> Unit,
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
                onClick = { onSave(name, description) },
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

/**
 * Диалог редактирования только прогрессии.
 *
 * Прогрессия сохраняется как есть, без парсинга и автоформатирования.
 */
@Composable
fun EditProgressDialog(
    initialProgress: String,
    units: List<String>,
    onDismiss: () -> Unit,
    onSave: (progress: String) -> Unit,
) {
    var progress by rememberSaveable { mutableStateOf(initialProgress) }

    EditorDialog(
        title = stringResource(R.string.dialog_progress_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.dialog_save),
        onConfirm = { onSave(progress) },
    ) {
        if (units.isNotEmpty()) {
            Text(
                text = units.joinToString(" / "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetsRow(
            progress = progress,
            units = units,
            onValueChange = { setIndex, valueIndex, value ->
                progress = ProgressCodec.withValue(
                    raw = progress,
                    unitCount = units.size,
                    setIndex = setIndex,
                    valueIndex = valueIndex,
                    value = ProgressCodec.sanitizeValue(value),
                )
            },
            onAddSet = { progress = ProgressCodec.addSet(progress, units.size) },
            onRemoveSet = { index -> progress = ProgressCodec.removeSet(progress, index) },
            onRewrite = { progress = "" },
        )
    }
}
