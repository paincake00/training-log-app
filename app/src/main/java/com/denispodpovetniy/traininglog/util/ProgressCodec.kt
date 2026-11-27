package com.denispodpovetniy.traininglog.util

/**
 * Канонический формат прогрессии: подходы разделены `-`, значения внутри подхода — `/`.
 * Значение с индексом i относится к единице измерения с индексом i.
 *
 * Пример: `20/4.6/6-30/4.5/5` — два подхода по три значения.
 *
 * Хранение и отображение не меняют регистр и не форматируют числа: значение
 * сохраняется так, как его ввели (допустимы цифры, один разделитель `.` или `,`,
 * ведущий `-`).
 */
object ProgressCodec {

    const val SET_SEPARATOR = "-"
    const val VALUE_SEPARATOR = "/"

    /** Стартовые подсказки единиц. Подсказки не сохраняются в БД. */
    val BUILT_IN_UNITS = listOf(
        "повторы",
        "кг",
        "сек",
        "мин",
        "км/ч",
        "наклон",
        "м",
        "км",
        "%",
    )

    private const val MAX_UNIT_LENGTH = 20
    private val VALUE_REGEX = Regex("^-?\\d+([.,]\\d+)?$")

    enum class Mode { STRUCTURED, SINGLE_VALUE, LEGACY }

    // --- подходы ---

    fun decode(raw: String): List<List<String>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SET_SEPARATOR).map { set -> set.split(VALUE_SEPARATOR) }
    }

    /**
     * Структура подходов сохраняется, пустые значения в конце подхода
     * отбрасываются: `20/6-/` и `20/6-` описывают одно и то же.
     * Полностью отсутствующая прогрессия превращается в `""`.
     */
    fun encode(sets: List<List<String>>): String {
        if (sets.isEmpty()) return ""
        return sets.joinToString(SET_SEPARATOR) { set ->
            set.map { it.trim() }.dropLastWhile { it.isEmpty() }.joinToString(VALUE_SEPARATOR)
        }
    }

    /** Есть ли хотя бы одно введённое значение (для доступности «В план»). */
    fun hasValues(raw: String): Boolean =
        decode(raw).any { set -> set.any { it.isNotEmpty() } }

    fun mode(raw: String, unitCount: Int): Mode {
        val sets = decode(raw)
        if (sets.isEmpty()) return if (unitCount > 0) Mode.STRUCTURED else Mode.SINGLE_VALUE
        if (sets.any { set -> set.any { !isValidValue(it) } }) return Mode.LEGACY
        if (unitCount > 0) {
            return if (sets.all { it.size in 1..unitCount }) Mode.STRUCTURED else Mode.LEGACY
        }
        return if (sets.all { it.size == 1 }) Mode.SINGLE_VALUE else Mode.LEGACY
    }

    /** Подходы, дополненные до [unitCount] пустыми значениями. Лишние значения отбрасываются. */
    fun withUnitCount(sets: List<List<String>>, unitCount: Int): List<List<String>> {
        if (unitCount <= 0) return sets
        return sets.map { set ->
            when {
                set.size == unitCount -> set
                set.size > unitCount -> set.take(unitCount)
                else -> set + List(unitCount - set.size) { "" }
            }
        }
    }

    /** Подходы для отображения: пустая прогрессия показывает один пустой подход. */
    fun displaySets(raw: String, unitCount: Int): List<List<String>> {
        val sets = decode(raw)
        if (sets.isEmpty()) return listOf(List(if (unitCount > 0) unitCount else 1) { "" })
        return withUnitCount(sets, unitCount)
    }

    fun valueAt(raw: String, setIndex: Int, valueIndex: Int): String =
        decode(raw).getOrNull(setIndex)?.getOrNull(valueIndex).orEmpty()

    fun withValue(
        raw: String,
        unitCount: Int,
        setIndex: Int,
        valueIndex: Int,
        value: String,
    ): String {
        val sets = withUnitCount(displaySets(raw, unitCount), unitCount).toMutableList()
        if (setIndex !in sets.indices || valueIndex !in sets[setIndex].indices) return raw
        sets[setIndex] = sets[setIndex].toMutableList().also { it[valueIndex] = sanitizeValue(value) }
        return encode(sets)
    }

    fun addSet(raw: String, unitCount: Int): String {
        val sets = withUnitCount(displaySets(raw, unitCount), unitCount)
        return encode(sets + listOf(List(if (unitCount > 0) unitCount else 1) { "" }))
    }

    fun removeSet(raw: String, setIndex: Int): String {
        val sets = decode(raw).toMutableList()
        if (setIndex !in sets.indices) return raw
        sets.removeAt(setIndex)
        return encode(sets)
    }

    /** Удаляет значение с позицией [valueIndex] из всех подходов. */
    fun removeUnitValues(raw: String, valueIndex: Int): String {
        if (valueIndex < 0) return raw
        val sets = decode(raw).map { set ->
            if (valueIndex in set.indices) set.filterIndexed { index, _ -> index != valueIndex } else set
        }
        return encode(sets)
    }

    // --- значения ---

    fun isValidValue(value: String): Boolean = value.isEmpty() || VALUE_REGEX.matches(value)

    /**
     * Приводит ввод к допустимому виду: убирает пробелы и посторонние символы,
     * оставляет не более одного разделителя `.` или `,`, `-` — только в начале.
     */
    fun sanitizeValue(input: String): String {
        val trimmed = input.filterNot { it.isWhitespace() }
        val negative = trimmed.startsWith('-')
        val body = if (negative) trimmed.drop(1) else trimmed
        val result = StringBuilder()
        var separatorUsed = false
        for (char in body) {
            when {
                char.isDigit() -> result.append(char)
                char == '.' || char == ',' -> {
                    if (separatorUsed) break
                    separatorUsed = true
                    result.append(char)
                }
            }
        }
        while (result.isNotEmpty() && (result.last() == '.' || result.last() == ',')) {
            result.deleteCharAt(result.length - 1)
        }
        if (result.isEmpty()) return ""
        return if (negative) "-$result" else result.toString()
    }

    // --- единицы ---

    fun decodeUnits(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }

    fun encodeUnits(units: List<String>): String = units.joinToString("\n")

    /**
     * Нормализует название единицы. Возвращает null, если название пустое,
     * длиннее 20 символов или совпадает с уже существующим без учёта регистра.
     */
    fun normalizeUnit(input: String?, existing: List<String>): String? {
        val name = input?.trim()?.replace('\n', ' ') ?: return null
        if (name.isEmpty() || name.length > MAX_UNIT_LENGTH) return null
        if (existing.any { it.equals(name, ignoreCase = true) }) return null
        return name
    }

    /** Подсказки: сохранённые единицы пользователя, затем стартовый список без повторов. */
    fun unitSuggestions(history: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return (history + BUILT_IN_UNITS).filter { unit ->
            val key = unit.trim().lowercase()
            unit.isNotBlank() && seen.add(key)
        }
    }
}
