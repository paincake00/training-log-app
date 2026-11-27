package com.denispodpovetniy.traininglog.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Снимок недельного плана и пресетов для экспорта и импорта (см. `PLAN.md` §3.10).
 *
 * Локальные `id` не экспортируются: на другом устройстве они другие. Пресет
 * описывается [PlanFileExercise.key], строки плана ссылаются на него.
 */
data class PlanFile(
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val exercises: List<PlanFileExercise>,
    val plan: List<PlanFileRow>,
)

/** Пресет в файле плана. [key] — уникальный идентификатор внутри файла. */
data class PlanFileExercise(
    val key: String,
    val name: String,
    val description: String,
    val units: List<String>,
    val lastProgress: String,
)

/** Строка недельного плана в файле. [weekday] — ISO 1..7. */
data class PlanFileRow(
    val weekday: Int,
    val position: Int,
    val exerciseKey: String,
    val progress: String,
)

/** Файл плана не подходит; [reason] переводится в текст в UI. */
class PlanFileException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {

    enum class Reason {
        /** Битый JSON или отсутствуют обязательные поля. */
        CORRUPT,

        /** `formatVersion` не поддерживается этой версией приложения. */
        VERSION,

        /** Структура читается, но данные недопустимы (дни, дубли, единицы, прогрессия). */
        DATA,

        /** Файл больше допустимого размера. */
        TOO_LARGE,
    }
}

/**
 * Чистые функции разбора и сборки файла плана. Покрыты JUnit.
 *
 * Разбор выполняется целиком до любых изменений в БД: либо файл принят,
 * либо импорт отклоняется без последствий.
 */
object PlanFileCodec {

    const val FORMAT_VERSION = 1

    /** Верхняя граница размера файла: 1 МиБ байт UTF-8. Больше — не наш файл. */
    const val MAX_FILE_BYTES = 1 shl 20

    private const val FIELD_FORMAT_VERSION = "formatVersion"
    private const val FIELD_EXPORTED_AT = "exportedAt"
    private const val FIELD_APP_VERSION = "appVersion"
    private const val FIELD_EXERCISES = "exercises"
    private const val FIELD_PLAN = "plan"
    private const val FIELD_KEY = "key"
    private const val FIELD_NAME = "name"
    private const val FIELD_DESCRIPTION = "description"
    private const val FIELD_UNITS = "units"
    private const val FIELD_LAST_PROGRESS = "lastProgress"
    private const val FIELD_WEEKDAY = "weekday"
    private const val FIELD_POSITION = "position"
    private const val FIELD_EXERCISE = "exercise"
    private const val FIELD_PROGRESS = "progress"

    /** Дни недели в ISO-нумерации: понедельник 1 … воскресенье 7. */
    private val WEEKDAYS = 1..7

    /** Ключ сопоставления пресетов между устройствами: название без регистра. */
    fun keyOf(name: String): String = name.trim().lowercase(Locale.ROOT)

    /** Имя файла для сохранения: `training-log-plan-2026-11-27.json`. */
    fun fileName(date: LocalDate): String =
        "training-log-plan-${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.json"

    fun encode(file: PlanFile): String {
        val root = JSONObject()
        root.put(FIELD_FORMAT_VERSION, file.formatVersion)
        root.put(FIELD_EXPORTED_AT, file.exportedAt)
        root.put(FIELD_APP_VERSION, file.appVersion)
        root.put(
            FIELD_EXERCISES,
            JSONArray().apply {
                file.exercises.forEach { exercise ->
                    put(
                        JSONObject().apply {
                            put(FIELD_KEY, exercise.key)
                            put(FIELD_NAME, exercise.name)
                            put(FIELD_DESCRIPTION, exercise.description)
                            put(FIELD_UNITS, JSONArray(exercise.units))
                            put(FIELD_LAST_PROGRESS, exercise.lastProgress)
                        },
                    )
                }
            },
        )
        root.put(
            FIELD_PLAN,
            JSONArray().apply {
                file.plan.forEach { row ->
                    put(
                        JSONObject().apply {
                            put(FIELD_WEEKDAY, row.weekday)
                            put(FIELD_POSITION, row.position)
                            put(FIELD_EXERCISE, row.exerciseKey)
                            put(FIELD_PROGRESS, row.progress)
                        },
                    )
                }
            },
        )
        return root.toString(2)
    }

    /**
     * Разбирает и проверяет файл.
     *
     * @throws PlanFileException если файл не подходит; БД при этом не меняется.
     */
    fun decode(json: String): PlanFile {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
            throw PlanFileException(PlanFileException.Reason.TOO_LARGE)
        }

        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw PlanFileException(PlanFileException.Reason.CORRUPT, error)
        }

        if (root.optInt(FIELD_FORMAT_VERSION, -1) != FORMAT_VERSION) {
            throw PlanFileException(PlanFileException.Reason.VERSION)
        }

        val exercises = decodeExercises(root.optJSONArray(FIELD_EXERCISES))
        val plan = decodePlan(root.optJSONArray(FIELD_PLAN), exercises)

        return PlanFile(
            formatVersion = FORMAT_VERSION,
            exportedAt = root.optString(FIELD_EXPORTED_AT, ""),
            appVersion = root.optString(FIELD_APP_VERSION, ""),
            exercises = exercises,
            plan = plan,
        )
    }

    private fun decodeExercises(array: JSONArray?): List<PlanFileExercise> {
        if (array == null) throw corrupt()

        // Ключ уникален в файле. Названия могут совпадать без учёта регистра:
        // в БД такие пресеты существуют раздельно, и экспорт различает их ключом.
        val keys = mutableSetOf<String>()
        val result = ArrayList<PlanFileExercise>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw invalid()
            val key = item.optString(FIELD_KEY).trim()
            val name = item.optString(FIELD_NAME).trim()
            if (key.isEmpty() || name.isEmpty()) throw invalid()

            val units = ArrayList<String>()
            val unitsArray = item.optJSONArray(FIELD_UNITS) ?: throw invalid()
            for (unitIndex in 0 until unitsArray.length()) {
                val unit = ProgressCodec.normalizeUnit(unitsArray.optString(unitIndex), units)
                    ?: throw invalid()
                units += unit
            }

            val lastProgress = item.optString(FIELD_LAST_PROGRESS, "")

            if (!keys.add(key)) throw invalid()

            result += PlanFileExercise(
                key = key,
                name = name,
                description = item.optString(FIELD_DESCRIPTION, ""),
                units = units,
                lastProgress = lastProgress,
            )
        }
        return result
    }

    private fun decodePlan(
        array: JSONArray?,
        exercises: List<PlanFileExercise>,
    ): List<PlanFileRow> {
        if (array == null) throw corrupt()

        val knownKeys = exercises.mapTo(mutableSetOf()) { it.key }
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val exercisesPerDay = mutableSetOf<Pair<Int, String>>()
        val result = ArrayList<PlanFileRow>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw invalid()
            val weekday = item.optInt(FIELD_WEEKDAY, 0)
            val position = item.optInt(FIELD_POSITION, -1)
            val exerciseKey = item.optString(FIELD_EXERCISE).trim()
            val progress = item.optString(FIELD_PROGRESS, "")

            if (weekday !in WEEKDAYS) throw invalid()
            if (position < 0) throw invalid()
            // Прогрессия переносится как есть: старые строки остаются читаемыми
            // на другом устройстве в режиме LEGACY.
            if (exerciseKey !in knownKeys) throw invalid()

            if (!occupied.add(weekday to position)) throw invalid()
            if (!exercisesPerDay.add(weekday to exerciseKey)) throw invalid()

            result += PlanFileRow(
                weekday = weekday,
                position = position,
                exerciseKey = exerciseKey,
                progress = progress,
            )
        }

        return result.sortedWith(compareBy({ it.weekday }, { it.position }))
    }

    private fun invalid() = PlanFileException(PlanFileException.Reason.DATA)

    private fun corrupt() = PlanFileException(PlanFileException.Reason.CORRUPT)
}
