package com.denispodpovetniy.traininglog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCodecTest {

    // --- разбор и сборка ---

    @Test
    fun `decodes units example`() {
        assertEquals(
            listOf(listOf("20", "4.6", "6"), listOf("30", "4.5", "5")),
            ProgressCodec.decode("20/4.6/6-30/4.5/5"),
        )
    }

    @Test
    fun `decodes single value sets`() {
        assertEquals(
            listOf(listOf("10"), listOf("10"), listOf("10")),
            ProgressCodec.decode("10-10-10"),
        )
    }

    @Test
    fun `blank progress decodes to no sets`() {
        assertEquals(emptyList<List<String>>(), ProgressCodec.decode(""))
        assertEquals(emptyList<List<String>>(), ProgressCodec.decode("   "))
    }

    @Test
    fun `encodes back to the same string`() {
        assertEquals(
            "20/4.6/6-30/4.5/5",
            ProgressCodec.encode(listOf(listOf("20", "4.6", "6"), listOf("30", "4.5", "5"))),
        )
    }

    @Test
    fun `keeps interior empty values`() {
        assertEquals("20//6", ProgressCodec.encode(listOf(listOf("20", "", "6"))))
        assertEquals(listOf(listOf("20", "", "6")), ProgressCodec.decode("20//6"))
    }

    @Test
    fun `empty values keep their structure`() {
        assertEquals("-", ProgressCodec.encode(listOf(listOf(""), listOf(""))))
        assertEquals("", ProgressCodec.encode(emptyList()))
        assertEquals("5-", ProgressCodec.encode(listOf(listOf("5"), listOf(""))))
        assertEquals("5-", ProgressCodec.decode("5-").let(ProgressCodec::encode))
    }

    @Test
    fun `hasValues ignores empty structure`() {
        assertFalse(ProgressCodec.hasValues(""))
        assertFalse(ProgressCodec.hasValues("/-/"))
        assertTrue(ProgressCodec.hasValues("//-5/"))
    }

    @Test
    fun `preserves decimal comma`() {
        assertEquals("4,6/6", ProgressCodec.encode(listOf(listOf("4,6", "6"))))
        assertEquals("4,6", ProgressCodec.decode("4,6").single().single())
    }

    // --- режимы ---

    @Test
    fun `structured mode with units`() {
        assertEquals(ProgressCodec.Mode.STRUCTURED, ProgressCodec.mode("20/4.6/6-30/4.5/5", 3))
        assertEquals(ProgressCodec.Mode.STRUCTURED, ProgressCodec.mode("20", 3))
        assertEquals(ProgressCodec.Mode.STRUCTURED, ProgressCodec.mode("", 3))
    }

    @Test
    fun `more values than units is legacy`() {
        assertEquals(ProgressCodec.Mode.LEGACY, ProgressCodec.mode("1/2/3/4", 3))
    }

    @Test
    fun `single value mode without units`() {
        assertEquals(ProgressCodec.Mode.SINGLE_VALUE, ProgressCodec.mode("10-10-10", 0))
        assertEquals(ProgressCodec.Mode.SINGLE_VALUE, ProgressCodec.mode("", 0))
    }

    @Test
    fun `several values without units is legacy`() {
        assertEquals(ProgressCodec.Mode.LEGACY, ProgressCodec.mode("10/20", 0))
    }

    @Test
    fun `free text is legacy`() {
        assertEquals(ProgressCodec.Mode.LEGACY, ProgressCodec.mode("5 подходов по 10", 2))
        assertEquals(ProgressCodec.Mode.LEGACY, ProgressCodec.mode("10 кг", 0))
    }

    @Test
    fun `blank set between separators is still a set`() {
        assertEquals(ProgressCodec.Mode.SINGLE_VALUE, ProgressCodec.mode("5--5", 0))
    }

    // --- редактирование ---

    @Test
    fun `sets a value inside a set`() {
        val result = ProgressCodec.withValue("20/4.6/6-30/4.5/5", 3, 1, 0, "35")
        assertEquals("20/4.6/6-35/4.5/5", result)
    }

    @Test
    fun `fills missing trailing values`() {
        val result = ProgressCodec.withValue("20", 3, 0, 2, "6")
        assertEquals("20//6", result)
    }

    @Test
    fun `sanitizes typed value`() {
        val result = ProgressCodec.withValue("", 2, 0, 0, " 4,6 кг ")
        assertEquals("4,6", result)
    }

    @Test
    fun `adds set with one value per unit`() {
        assertEquals("20/6-", ProgressCodec.addSet("20/6", 2))
        assertEquals("10-", ProgressCodec.addSet("10", 0))
    }

    @Test
    fun `adds first set to empty progress`() {
        assertEquals("-", ProgressCodec.addSet("", 2))
        assertEquals("-", ProgressCodec.addSet("", 0))
    }

    @Test
    fun `removes set by index`() {
        assertEquals("30/4.5/5", ProgressCodec.removeSet("20/4.6/6-30/4.5/5", 0))
        assertEquals("", ProgressCodec.removeSet("5", 0))
        assertEquals("5", ProgressCodec.removeSet("5", 4))
    }

    @Test
    fun `removes unit position from every set`() {
        assertEquals(
            "20/6-30/5",
            ProgressCodec.removeUnitValues("20/4.6/6-30/4.5/5", 1),
        )
    }

    @Test
    fun `removes unit position from single value sets`() {
        assertEquals("-", ProgressCodec.removeUnitValues("5-5", 0))
    }

    // --- значения ---

    @Test
    fun `valid values`() {
        assertTrue(ProgressCodec.isValidValue(""))
        assertTrue(ProgressCodec.isValidValue("10"))
        assertTrue(ProgressCodec.isValidValue("-5"))
        assertTrue(ProgressCodec.isValidValue("4.6"))
        assertTrue(ProgressCodec.isValidValue("4,6"))
    }

    @Test
    fun `invalid values`() {
        assertFalse(ProgressCodec.isValidValue("4.6.1"))
        assertFalse(ProgressCodec.isValidValue("4 кг"))
        assertFalse(ProgressCodec.isValidValue("--5"))
        assertFalse(ProgressCodec.isValidValue("."))
    }

    @Test
    fun `sanitize drops spaces and extra separators`() {
        assertEquals("46", ProgressCodec.sanitizeValue("4 6"))
        assertEquals("4.6", ProgressCodec.sanitizeValue("4.6.1"))
        assertEquals("-4,6", ProgressCodec.sanitizeValue("-4,6"))
        assertEquals("", ProgressCodec.sanitizeValue("-"))
        assertEquals("", ProgressCodec.sanitizeValue("кг"))
    }

    @Test
    fun `display sets pads to unit count`() {
        assertEquals(
            listOf(listOf("5", ""), listOf("", "")),
            ProgressCodec.displaySets("5-", 2),
        )
        assertEquals(listOf(listOf("")), ProgressCodec.displaySets("", 0))
    }

    // --- единицы ---

    @Test
    fun `units round trip`() {
        val units = listOf("повторы", "кг", "наклон")
        assertEquals(units, ProgressCodec.decodeUnits(ProgressCodec.encodeUnits(units)))
        assertEquals(emptyList<String>(), ProgressCodec.decodeUnits(""))
    }

    @Test
    fun `unit name is trimmed and validated`() {
        assertEquals("кг", ProgressCodec.normalizeUnit("  кг ", emptyList()))
        assertNull(ProgressCodec.normalizeUnit("   ", emptyList()))
        assertNull(ProgressCodec.normalizeUnit("кг", listOf("КГ")))
        assertNull(ProgressCodec.normalizeUnit("a".repeat(21), emptyList()))
    }

    @Test
    fun `suggestions start with history then built in without duplicates`() {
        val suggestions = ProgressCodec.unitSuggestions(listOf("кг", "шаги"))
        assertEquals("кг", suggestions.first())
        assertEquals(1, suggestions.count { it.equals("кг", ignoreCase = true) })
        assertTrue(suggestions.contains("шаги"))
        assertTrue(suggestions.contains("повторы"))
    }
}
