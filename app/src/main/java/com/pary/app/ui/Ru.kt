package com.pary.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Русские подписи дат и времени. */
object Ru {
    val dowShort = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    val dowFull = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val months = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val monthsNom = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    fun dow(d: LocalDate): String = dowShort[d.dayOfWeek.value - 1]

    fun date(d: LocalDate): String = "${dow(d)}, ${d.dayOfMonth} ${months[d.monthValue - 1]}"

    fun dateShort(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    fun month(m: Int): String = monthsNom[(m - 1).coerceIn(0, 11)]

    fun time(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    fun time(dt: LocalDateTime): String = time(dt.toLocalTime())

    fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        today.plusDays(2) -> "Послезавтра"
        else -> dowFull[d.dayOfWeek.value - 1]
    }

    fun dueLabel(dt: LocalDateTime?, today: LocalDate): String {
        if (dt == null) return "без срока"
        val d = dt.toLocalDate()
        return when (d) {
            today -> "сегодня ${time(dt)}"
            today.plusDays(1) -> "завтра ${time(dt)}"
            else -> "${d.dayOfMonth} ${months[d.monthValue - 1]} ${time(dt)}"
        }
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    }
}
