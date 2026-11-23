package com.denispodpovetniy.traininglog.util

import java.time.LocalDate

/**
 * День недели в ISO-нумерации: понедельник — 1, воскресенье — 7.
 *
 * Используется и для `plan_exercises.weekday`, чтобы не было расхождений с датой.
 */
fun isoWeekday(date: LocalDate): Int = date.dayOfWeek.value
