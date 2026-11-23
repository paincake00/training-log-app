package com.denispodpovetniy.traininglog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseValidatorTest {

    @Test
    fun `null name is rejected`() {
        assertNull(ExerciseValidator.normalizeName(null))
    }

    @Test
    fun `empty name is rejected`() {
        assertNull(ExerciseValidator.normalizeName(""))
    }

    @Test
    fun `blank name is rejected`() {
        assertNull(ExerciseValidator.normalizeName("   \n\t "))
    }

    @Test
    fun `valid name is trimmed`() {
        assertEquals("Подтягивания", ExerciseValidator.normalizeName("  Подтягивания  "))
    }

    @Test
    fun `inner spaces are preserved`() {
        assertEquals("Бицепс  штанга", ExerciseValidator.normalizeName(" Бицепс  штанга "))
    }
}
