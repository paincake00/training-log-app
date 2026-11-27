package com.denispodpovetniy.traininglog.ui.exercises

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.ui.components.DraftUnitsEditor
import com.denispodpovetniy.traininglog.ui.components.EditorDialog
import com.denispodpovetniy.traininglog.ui.components.SetsRow
import com.denispodpovetniy.traininglog.util.ProgressCodec

/**
 * Диалог создания нового упражнения (название, описание, единицы, подходы).
 *
 * Название обязательно: пока оно пустое, кнопка добавления неактивна.
 * [initialName], [initialDescription], [initialUnits] и [initialProgress] позволяют
 * переиспользовать диалог для предзаполнения полей; по умолчанию все поля пустые.
 */
@Composable
fun CreateExerciseDialog(
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, units: List<String>, progress: String) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    initialUnits: List<String> = emptyList(),
    initialProgress: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var units by remember { mutableStateOf(initialUnits) }
    var progress by rememberSaveable { mutableStateOf(initialProgress) }

    EditorDialog(
        title = stringResource(R.string.dialog_create_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.dialog_add),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, description, units, progress) },
    ) {
        NameField(
            value = name,
            onValueChange = { name = it },
        )
        DescriptionField(
            value = description,
            onValueChange = { description = it },
        )
        DraftUnitsEditor(
            units = units,
            suggestions = suggestions,
            onUnitsChange = { units = it },
        )
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
