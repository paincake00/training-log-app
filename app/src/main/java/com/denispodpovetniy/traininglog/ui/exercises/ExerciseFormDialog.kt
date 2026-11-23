package com.denispodpovetniy.traininglog.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R

/**
 * Диалог создания нового упражнения (название, описание, прогрессия).
 *
 * Название обязательно: пока оно пустое, кнопка добавления неактивна.
 * [initialName], [initialDescription] и [initialProgress] позволяют переиспользовать диалог
 * для предзаполнения полей; по умолчанию все поля пустые.
 */
@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, progress: String) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    initialProgress: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var progress by rememberSaveable { mutableStateOf(initialProgress) }
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
                onClick = { onSave(name, description, progress) },
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

/** Поле названия с обязательной валидацией. */
@Composable
internal fun NameField(
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

/** Многострочное необязательное описание. */
@Composable
internal fun DescriptionField(
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

/** Поле прогрессии: свободный текст без автоформатирования, моноширинный шрифт. */
@Composable
internal fun ProgressField(
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
