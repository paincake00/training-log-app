package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.stringResource
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.util.ProgressCodec

/** Переименование единицы с проверкой длины и дубликатов. */
@Composable
fun RenameUnitDialog(
    initialName: String,
    existingUnits: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val others = existingUnits.filterNot { it.equals(initialName, ignoreCase = true) }
    val normalized = ProgressCodec.normalizeUnit(name, others)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unit_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.units_name_label)) },
                singleLine = true,
                isError = normalized == null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(onSave) },
                enabled = normalized != null,
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

/** Подтверждение удаления единицы: значения исчезнут из всех подходов. */
@Composable
fun ConfirmRemoveUnitDialog(
    unitName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmActionDialog(
        title = stringResource(R.string.unit_delete_title, unitName),
        text = stringResource(R.string.unit_delete_text),
        confirmText = stringResource(R.string.dialog_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** Универсальное подтверждение destructive-действия. */
@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
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
