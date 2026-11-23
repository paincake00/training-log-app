package com.denispodpovetniy.traininglog.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Форматирование дат для интерфейса. Локаль фиксирована — `ru`. */
object DateFormatters {

    private val locale: Locale = Locale.forLanguageTag("ru")
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy · EEEE", locale)

    /** Например, `2026-11-24` → `24.11.2026 · вторник`. */
    fun formatDate(date: LocalDate): String = date.format(dateFormatter).lowercase(locale)
}
