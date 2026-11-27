package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.util.ProgressCodec

/**
 * Строка подходов: плитки через `-`, значения внутри плитки — через `/`.
 *
 * В режиме `STRUCTURED` у каждого значения своя единица измерения, в
 * `SINGLE_VALUE` — одно число на подход. Свободный текст старого формата
 * показывается только для чтения с кнопкой «Переписать».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetsRow(
    progress: String,
    units: List<String>,
    onValueChange: (setIndex: Int, valueIndex: Int, value: String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (setIndex: Int) -> Unit,
    onRewrite: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val mode = ProgressCodec.mode(progress, units.size)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sets_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))

        when (mode) {
            ProgressCodec.Mode.LEGACY -> {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sets_legacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRewrite) {
                    Text(stringResource(R.string.sets_legacy_rewrite))
                }
            }

            ProgressCodec.Mode.SINGLE_VALUE,
            ProgressCodec.Mode.STRUCTURED,
            -> {
                val sets = ProgressCodec.displaySets(progress, units.size)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    sets.forEachIndexed { setIndex, values ->
                        Row(
                            modifier = Modifier.combinedClickable(
                                enabled = enabled && sets.size > 1,
                                onClick = {},
                                onLongClick = { onRemoveSet(setIndex) },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            values.forEachIndexed { valueIndex, value ->
                                if (valueIndex > 0) {
                                    Text(
                                        text = ProgressCodec.VALUE_SEPARATOR,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SetValueField(
                                    value = value,
                                    enabled = enabled,
                                    onValueChange = { text ->
                                        onValueChange(setIndex, valueIndex, text)
                                    },
                                )
                            }
                            if (setIndex < sets.lastIndex) {
                                // Разделитель живёт внутри подхода, чтобы при переносе
                                // строки он оставался рядом со своими значениями.
                                Text(
                                    text = ProgressCodec.SET_SEPARATOR,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                        }
                    }

                    if (enabled) {
                        OutlinedIconButton(
                            onClick = onAddSet,
                            modifier = Modifier.width(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = stringResource(R.string.sets_add),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Поле одного значения. Пока поле в фокусе, показываем ввод как есть —
 * иначе «4,» превращалось бы в «4» прямо во время набора.
 */
@Composable
private fun SetValueField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }

    LaunchedEffect(value, focused) {
        if (!focused) text = value
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onValueChange(ProgressCodec.sanitizeValue(raw))
        },
        modifier = Modifier
            .width(72.dp)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
    )
}
