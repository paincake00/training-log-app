package com.denispodpovetniy.traininglog.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormattersTest {

    @Test
    fun `formats date with russian lowercase weekday`() {
        assertEquals(
            "24.11.2026 · вторник",
            DateFormatters.formatDate(LocalDate.of(2026, 11, 24)),
        )
    }

    @Test
    fun `formats monday`() {
        assertEquals(
            "23.11.2026 · понедельник",
            DateFormatters.formatDate(LocalDate.of(2026, 11, 23)),
        )
    }

    @Test
    fun `formats sunday`() {
        assertEquals(
            "29.11.2026 · воскресенье",
            DateFormatters.formatDate(LocalDate.of(2026, 11, 29)),
        )
    }

    @Test
    fun `pads day and month with leading zeros`() {
        assertEquals(
            "05.01.2027 · вторник",
            DateFormatters.formatDate(LocalDate.of(2027, 1, 5)),
        )
    }
}
