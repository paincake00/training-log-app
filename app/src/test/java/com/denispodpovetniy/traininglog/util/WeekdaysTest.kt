package com.denispodpovetniy.traininglog.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekdaysTest {

    @Test
    fun `monday maps to 1`() {
        assertEquals(1, isoWeekday(LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun `sunday maps to 7`() {
        assertEquals(7, isoWeekday(LocalDate.of(2026, 11, 29)))
    }

    @Test
    fun `full week maps to 1 through 7`() {
        val monday = LocalDate.of(2026, 11, 23)
        val weekdays = (0L..6L).map { isoWeekday(monday.plusDays(it)) }

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), weekdays)
    }
}
