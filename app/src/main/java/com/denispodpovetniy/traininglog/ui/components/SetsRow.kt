package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.util.ProgressCodec

/**
 * Строка подходов: плитки значений через `/`, у каждой плитки — кнопка удаления.
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    sets.forEachIndexed { setIndex, values ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            values.forEachIndexed { valueIndex, value ->
                                if (valueIndex > 0) {
                                    Text(
                                        text = ProgressCodec.VALUE_SEPARATOR,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 2.dp),
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
                            // Подход удаляется только явной кнопкой: долгое
                            // нажатие перехватывали поля ввода.
                            if (enabled && sets.size > 1) {
                                IconButton(
                                    onClick = { onRemoveSet(setIndex) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = stringResource(R.string.sets_delete),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (enabled) {
                        AddTileButton(
                            contentDescription = stringResource(R.string.sets_add),
                            onClick = onAddSet,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Поле одного значения: компактная плитка 48dp высотой, ширина растёт
 * по длине текста. Пока поле в фокусе, показываем ввод как есть —
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
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val borderColor = if (focused) colors.primary else colors.outline

    LaunchedEffect(value, focused) {
        if (!focused) text = value
    }

    // Моноширинный 16sp: символ ≈ 10dp. Плитка растёт вместе со значением,
    // но не выходит за разумные пределы.
    val fieldWidth = (10 + text.length * 10).coerceIn(minimumValue = 44, maximumValue = 96).dp

    BasicTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onValueChange(ProgressCodec.sanitizeValue(raw))
        },
        modifier = Modifier
            .width(fieldWidth)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = colors.onSurface,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .clickable(enabled = enabled) { focusRequester.requestFocus() }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                innerTextField()
            }
        },
    )
}
