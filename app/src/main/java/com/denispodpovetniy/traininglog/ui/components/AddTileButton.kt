package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R

/**
 * Кнопка «+» для строк единиц и подходов.
 *
 * Компонент общий, чтобы обе строки выглядели одинаково: 40dp визуально,
 * тач-зона расширяется до 48dp средствами Material 3.
 */
@Composable
fun AddTileButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expanded: Boolean = false,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(if (expanded) R.drawable.ic_close else R.drawable.ic_add),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}
