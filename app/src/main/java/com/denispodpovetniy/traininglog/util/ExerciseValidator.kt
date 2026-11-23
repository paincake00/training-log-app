package com.denispodpovetniy.traininglog.util

/** Валидация полей упражнения. Прогрессия не валидируется и не форматируется. */
object ExerciseValidator {

    /**
     * Название упражнения обрезается по краям. Пустое или состоящее из пробелов
     * название недопустимо — возвращается `null`.
     */
    fun normalizeName(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }
}
