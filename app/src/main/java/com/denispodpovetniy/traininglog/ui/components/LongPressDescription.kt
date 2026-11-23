package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Зазор между строкой упражнения и плашкой описания. */
private val DescriptionPopupGap = 6.dp

/** Минимальный отступ плашки от краёв окна. */
private val DescriptionPopupMargin = 16.dp

/** Плашка занимает не больше ширины окна минус этот отступ с обеих сторон. */
private val DescriptionPopupMaxWidthInset = 32.dp

/**
 * Показывает [description] во всплывающей плашке, пока пользователь удерживает палец
 * на [content]. Отпускание или отмена жеста сразу скрывают плашку.
 *
 * Компонент не участвует в раскладке экрана: плашка живёт в отдельном `Popup`-окне,
 * поэтому не сдвигает соседние элементы и не обрезается карточкой или `LazyColumn`.
 * Рассчитан на переиспользование в плане, тренировке и логе.
 *
 * Если [description] пустой, жест ничего не делает.
 */
@Composable
fun LongPressDescription(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDescription = description.isNotBlank()

    LaunchedEffect(hasDescription) {
        if (!hasDescription) expanded = false
    }

    Box(
        modifier = modifier.pointerInput(hasDescription) {
            if (!hasDescription) return@pointerInput
            detectTapGestures(
                onLongPress = { expanded = true },
                onPress = {
                    tryAwaitRelease()
                    expanded = false
                },
            )
        },
    ) {
        content()

        if (expanded && hasDescription) {
            val maxWidth = (LocalConfiguration.current.screenWidthDp.dp - DescriptionPopupMaxWidthInset)
                .coerceAtLeast(DescriptionPopupMaxWidthInset)
            DescriptionPlate(
                description = description,
                maxWidth = maxWidth,
            )
        }
    }
}

@Composable
private fun DescriptionPlate(
    description: String,
    maxWidth: Dp,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { DescriptionPopupGap.roundToPx() }
    val marginPx = with(density) { DescriptionPopupMargin.roundToPx() }
    val positionProvider = remember(gapPx, marginPx) {
        DescriptionPopupPositionProvider(gapPx = gapPx, marginPx = marginPx)
    }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "descriptionPlateAlpha",
    )

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .alpha(alpha),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = description,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Размещает плашку под якорем с зазором [gapPx], при нехватке места — над ним.
 * По горизонтали плашка выравнивается по левому краю якоря и прижимается к окну
 * с отступом [marginPx].
 */
internal class DescriptionPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = descriptionPopupOffset(
        anchorBounds = anchorBounds,
        windowSize = windowSize,
        popupSize = popupContentSize,
        gap = gapPx,
        margin = marginPx,
    )
}

internal fun descriptionPopupOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    gap: Int,
    margin: Int,
): IntOffset {
    val maxX = (windowSize.width - margin - popupSize.width).coerceAtLeast(margin)
    val x = anchorBounds.left.coerceIn(margin, maxX)

    val below = anchorBounds.bottom + gap
    val above = anchorBounds.top - gap - popupSize.height
    val maxY = (windowSize.height - margin - popupSize.height).coerceAtLeast(margin)

    val y = when {
        below + popupSize.height + margin <= windowSize.height -> below
        above >= margin -> above
        else -> below.coerceIn(margin, maxY)
    }

    return IntOffset(x, y)
}
