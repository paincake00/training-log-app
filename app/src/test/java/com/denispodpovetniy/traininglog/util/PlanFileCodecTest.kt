package com.denispodpovetniy.traininglog.util

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Файл плана: имя файла, ключи, сборка и разбор (см. `PLAN.md` §3.10).
 *
 * `org.json` есть только в Android, поэтому тест идёт под Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanFileCodecTest {

    @Test
    fun `encode decode round trip keeps presets and sorted plan`() {
        val file = PlanFile(
            formatVersion = PlanFileCodec.FORMAT_VERSION,
            exportedAt = "2026-11-27T18:40:00Z",
            appVersion = "1.0.0",
            exercises = listOf(
                PlanFileExercise(
                    key = "присед",
                    name = "Присед",
                    description = "Спина прямая",
                    units = listOf("повторы", "кг"),
                    lastProgress = "5/20-5/20",
                ),
                PlanFileExercise(
                    key = "жим",
                    name = "Жим",
                    description = "",
                    units = listOf("кг"),
                    lastProgress = "10/60",
                ),
            ),
            // Строки намеренно не по порядку: разбор обязан отсортировать их.
            plan = listOf(
                PlanFileRow(weekday = 3, position = 1, exerciseKey = "жим", progress = "10/60"),
                PlanFileRow(weekday = 1, position = 1, exerciseKey = "жим", progress = ""),
                PlanFileRow(weekday = 1, position = 0, exerciseKey = "присед", progress = "5/20-5/20"),
            ),
        )

        val decoded = PlanFileCodec.decode(PlanFileCodec.encode(file))

        assertEquals(
            file.copy(
                plan = listOf(
                    PlanFileRow(weekday = 1, position = 0, exerciseKey = "присед", progress = "5/20-5/20"),
                    PlanFileRow(weekday = 1, position = 1, exerciseKey = "жим", progress = ""),
                    PlanFileRow(weekday = 3, position = 1, exerciseKey = "жим", progress = "10/60"),
                ),
            ),
            decoded,
        )
    }

    @Test
    fun `file name uses ISO date and key trims and lowercases`() {
        assertEquals(
            "training-log-plan-2026-11-27.json",
            PlanFileCodec.fileName(LocalDate.of(2026, 11, 27)),
        )
        assertEquals("присед", PlanFileCodec.keyOf("  Присед  "))
        assertEquals("squat", PlanFileCodec.keyOf("SQUAT"))
    }

    @Test
    fun `decode rejects broken JSON as corrupt`() {
        assertRejected(PlanFileException.Reason.CORRUPT, "{oops")
    }

    @Test
    fun `decode rejects missing exercises array as corrupt`() {
        val json = JSONObject().apply {
            put("formatVersion", PlanFileCodec.FORMAT_VERSION)
            put("exportedAt", "2026-11-27T18:40:00Z")
            put("appVersion", "1.0.0")
            put("plan", JSONArray())
        }.toString()

        assertRejected(PlanFileException.Reason.CORRUPT, json)
    }

    @Test
    fun `decode rejects unsupported format version`() {
        assertRejected(PlanFileException.Reason.VERSION, fileJson(formatVersion = 0))
        assertRejected(PlanFileException.Reason.VERSION, fileJson(formatVersion = 2))
    }

    @Test
    fun `decode rejects file larger than limit`() {
        assertRejected(
            PlanFileException.Reason.TOO_LARGE,
            "a".repeat(PlanFileCodec.MAX_FILE_BYTES + 1),
        )
    }

    @Test
    fun `decode measures the limit in utf8 bytes`() {
        // 600 000 кириллических символов — это 1 200 000 байт UTF-8, больше лимита,
        // хотя символов меньше миллиона.
        val longValue = "я".repeat(600_000)

        assertRejected(
            PlanFileException.Reason.TOO_LARGE,
            fileJson(exercises = listOf(exerciseJson(key = "жим", name = "Жим", lastProgress = longValue))),
        )
    }

    @Test
    fun `decode rejects duplicate key`() {
        val json = fileJson(
            exercises = listOf(
                exerciseJson(key = "жим", name = "Жим"),
                exerciseJson(key = "жим", name = "Тяга"),
            ),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode keeps exercise names differing only by case apart`() {
        // Экспорт различает такие пресеты суффиксом ключа, поэтому файл должен читаться.
        val json = fileJson(
            exercises = listOf(
                exerciseJson(key = "жим", name = "Жим"),
                exerciseJson(key = "жим-2", name = "жим"),
            ),
            plan = listOf(
                rowJson(weekday = 1, position = 0, key = "жим"),
                rowJson(weekday = 1, position = 1, key = "жим-2"),
            ),
        )

        val file = PlanFileCodec.decode(json)

        assertEquals(listOf("жим", "жим-2"), file.exercises.map { it.key })
        assertEquals(listOf("Жим", "жим"), file.exercises.map { it.name })
    }

    @Test
    fun `decode rejects weekday outside one to seven`() {
        assertRejected(PlanFileException.Reason.DATA, fileWithRow(weekday = 0, position = 0))
        assertRejected(PlanFileException.Reason.DATA, fileWithRow(weekday = 8, position = 0))
    }

    @Test
    fun `decode rejects negative position`() {
        assertRejected(PlanFileException.Reason.DATA, fileWithRow(weekday = 1, position = -1))
    }

    @Test
    fun `decode rejects duplicate position inside one weekday`() {
        val json = fileJson(
            exercises = listOf(
                exerciseJson(key = "жим", name = "Жим"),
                exerciseJson(key = "тяга", name = "Тяга"),
            ),
            plan = listOf(
                rowJson(weekday = 1, position = 0, key = "жим"),
                rowJson(weekday = 1, position = 0, key = "тяга"),
            ),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode rejects same exercise twice in one weekday`() {
        val json = fileWithRows(
            rowJson(weekday = 1, position = 0, key = "жим"),
            rowJson(weekday = 1, position = 1, key = "жим"),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode rejects plan row with unknown exercise key`() {
        val json = fileJson(
            exercises = listOf(exerciseJson(key = "жим", name = "Жим")),
            plan = listOf(rowJson(weekday = 1, position = 0, key = "нет")),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode rejects empty unit name`() {
        val json = fileJson(
            exercises = listOf(exerciseJson(key = "жим", name = "Жим", units = listOf("кг", " "))),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode rejects too long unit name`() {
        val json = fileJson(
            exercises = listOf(exerciseJson(key = "жим", name = "Жим", units = listOf("a".repeat(21)))),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode rejects duplicate units in different case`() {
        val json = fileJson(
            exercises = listOf(exerciseJson(key = "жим", name = "Жим", units = listOf("кг", "КГ"))),
        )

        assertRejected(PlanFileException.Reason.DATA, json)
    }

    @Test
    fun `decode keeps legacy unstructured progress byte for byte`() {
        val json = fileJson(
            exercises = listOf(
                exerciseJson(
                    key = "ходьба",
                    name = "Ходьба",
                    units = listOf("мин", "км/ч"),
                    lastProgress = "200-200-150",
                ),
            ),
            plan = listOf(
                rowJson(weekday = 1, position = 0, key = "ходьба", progress = "200-200-150"),
            ),
        )

        val decoded = PlanFileCodec.decode(json)

        assertEquals("200-200-150", decoded.exercises.single().lastProgress)
        assertEquals(listOf("мин", "км/ч"), decoded.exercises.single().units)
        assertEquals("200-200-150", decoded.plan.single().progress)
    }

    // ------------------------------------------------------------------ helpers

    private fun fileWithRow(weekday: Int, position: Int): String =
        fileWithRows(rowJson(weekday = weekday, position = position, key = "жим"))

    private fun fileWithRows(vararg rows: JSONObject): String = fileJson(
        exercises = listOf(exerciseJson(key = "жим", name = "Жим")),
        plan = rows.toList(),
    )

    private fun fileJson(
        formatVersion: Int = PlanFileCodec.FORMAT_VERSION,
        exercises: List<JSONObject> = emptyList(),
        plan: List<JSONObject> = emptyList(),
    ): String = JSONObject().apply {
        put("formatVersion", formatVersion)
        put("exportedAt", "2026-11-27T18:40:00Z")
        put("appVersion", "1.0.0")
        put("exercises", JSONArray(exercises))
        put("plan", JSONArray(plan))
    }.toString()

    private fun exerciseJson(
        key: String,
        name: String,
        description: String = "",
        units: List<String> = emptyList(),
        lastProgress: String = "",
    ): JSONObject = JSONObject().apply {
        put("key", key)
        put("name", name)
        put("description", description)
        put("units", JSONArray(units))
        put("lastProgress", lastProgress)
    }

    private fun rowJson(
        weekday: Int,
        position: Int,
        key: String,
        progress: String = "",
    ): JSONObject = JSONObject().apply {
        put("weekday", weekday)
        put("position", position)
        put("exercise", key)
        put("progress", progress)
    }

    private fun assertRejected(expected: PlanFileException.Reason, json: String) {
        val error = assertFailsWith<PlanFileException> { PlanFileCodec.decode(json) }
        assertEquals(expected, error.reason)
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "Ожидалось ${T::class.java.simpleName}, получено ${error::class.java.name}",
                error,
            )
        }
        throw AssertionError("Ожидалось ${T::class.java.simpleName}, исключение не выброшено")
    }
}
