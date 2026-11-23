package com.denispodpovetniy.traininglog.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressDescriptionPositionTest {

    private val window = IntSize(width = 1080, height = 2000)
    private val margin = 16
    private val gap = 6

    @Test
    fun `plate is placed below anchor when there is enough space`() {
        val offset = descriptionPopupOffset(
            anchorBounds = IntRect(left = 16, top = 100, right = 900, bottom = 150),
            windowSize = window,
            popupSize = IntSize(width = 600, height = 200),
            gap = gap,
            margin = margin,
        )

        assertEquals(IntOffset(16, 156), offset)
    }

    @Test
    fun `plate flips above anchor near the bottom of the window`() {
        val offset = descriptionPopupOffset(
            anchorBounds = IntRect(left = 16, top = 1800, right = 900, bottom = 1850),
            windowSize = window,
            popupSize = IntSize(width = 600, height = 200),
            gap = gap,
            margin = margin,
        )

        assertEquals(IntOffset(16, 1594), offset)
    }

    @Test
    fun `plate is clamped to the right window edge`() {
        val offset = descriptionPopupOffset(
            anchorBounds = IntRect(left = 800, top = 100, right = 1064, bottom = 150),
            windowSize = window,
            popupSize = IntSize(width = 600, height = 200),
            gap = gap,
            margin = margin,
        )

        assertEquals(IntOffset(464, 156), offset)
    }

    @Test
    fun `oversized plate is clamped into the window`() {
        val offset = descriptionPopupOffset(
            anchorBounds = IntRect(left = 16, top = 900, right = 900, bottom = 950),
            windowSize = window,
            popupSize = IntSize(width = 600, height = 1900),
            gap = gap,
            margin = margin,
        )

        assertEquals(IntOffset(16, 84), offset)
    }

    @Test
    fun `plate wider than the window starts at the margin`() {
        val offset = descriptionPopupOffset(
            anchorBounds = IntRect(left = 300, top = 100, right = 900, bottom = 150),
            windowSize = window,
            popupSize = IntSize(width = 1200, height = 200),
            gap = gap,
            margin = margin,
        )

        assertEquals(IntOffset(16, 156), offset)
    }
}
