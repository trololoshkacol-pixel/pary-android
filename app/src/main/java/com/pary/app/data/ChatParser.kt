package com.pary.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Достаёт из сообщений чата намёки на домашку и срок. Полностью локально. */
object ChatParser {

    private val KEYS = listOf(
        "дз", "д/з", "домашк", "сдать", "сдай", "сдаем", "сдаём", "сдава", "сдач",
        "принес", "принеси", "присла",
        "доклад", "реферат", "контрольн", "тест", "лаборатор", "семинар", "эссе",
        "презентац", "выучить", "подготов", "задани", "задач", "упражнени",
        "курсов", "чертеж", "чертёж", "отчет", "отчёт", "параграф", "конспект",
        "защит", "коллоквиум", "зачёт", "зачет", "выступлени",
        "выполн", "решить", "решите", "оформи",
    )

    /** Эти стебли ловят лишнее («практически», «главное») — нужны формы слова. */
    private val KEY_RE = listOf(
        Regex("""практическ(?:ая|ой|ую|ое|ие|их|ого|ому)"""),
        Regex("""глав(?:а|ы|е|у|ах|ой)(?![а-яё])"""),
        Regex("""ответ(?:ить|ьте|ил|или)?\s+на\s+вопрос"""),
        Regex("""отправ(?:ить|ьте|ил|или|лю)\s+на\s+почту"""),
    )

    private val MONTHS = listOf(
        "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4,
        "июн" to 6, "июл" to 7, "август" to 8, "сентябр" to 9, "октябр" to 10,
        "ноябр" to 11, "декабр" to 12,
    )

    /** Короткие формы: «25 ноя», «5 мар», «1 мая». */
    private val MONTH_SHORT = mapOf(
        "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4, "ма" to 5, "май" to 5,
        "мая" to 5, "июн" to 6, "июл" to 7, "авг" to 8, "сен" to 9, "сент" to 9,
        "окт" to 10, "ноя" to 11, "дек" to 12,
    )

    private val DOWS = listOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY,
        "сред" to DayOfWeek.WEDNESDAY, "четверг" to DayOfWeek.THURSDAY,
        "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY,
        "воскресен" to DayOfWeek.SUNDAY,
    )

    /** Дни недели с формами слова: «средства» больше не станет средой. */
    private val DOW_PAT =
        "понедельник[а-яё]*|вторник[а-яё]*|сред(?:а|ы|е|у|ой|ам|ах|ами)|" +
            "четверг[а-яё]*|пятниц(?:а|ы|е|у|ой|ам|ах|ами)|" +
            "суббот(?:а|ы|е|у|ой|ам|ах|ами)|воскрес[а-яё]*"

    private val RX_DOW = Regex(DOW_PAT)

    /** «в эту пятницу», «этот вторник» — ближайший такой день, сегодня тоже подходит. */
    private val RX_THIS_WEEK_DOW =
        Regex("""эт(?:у|от|ой)\s+(?:недел[а-яё]*\s+)?(?:в\s+|во\s+)?($DOW_PAT)""")

    /** «в следующий вторник» — уже на следующей неделе. */
    private val RX_NEXT_WEEK_DOW =
        Regex("""следующ[а-яё]*\s+(?:недел[а-яё]*\s+)?(?:в\s+|во\s+)?($DOW_PAT)""")

    /** «25-го», «25 число», «к 25-му». */
    private val RX_DAY_NUM = Regex("""(?<!\d)(\d{1,2})\s*-?\s*(?:го|е|ое|ому|му|числ[а-яё]*)(?![а-яё])""")

    /** «к концу недели», «до конца недели» — воскресенье. */
    private val RX_END_WEEK = Regex("""(?:к|до)\s+конц[а-яё]*\s+(?:следующ[а-яё]*\s+)?недел[а-яё]*""")

    /** «в начале недели» — понедельник. */
    private val RX_START_WEEK = Regex("""(?:в|с|к)\s+начал[а-яё]*\s+(?:следующ[а-яё]*\s+)?недел[а-яё]*""")

    /** «на этой неделе». */
    private val RX_THIS_WEEK = Regex("""(?:на|в)\s+эт(?:ой|у|ом)\s+недел[а-яё]*""")

    /** «на следующей неделе». */
    private val RX_NEXT_WEEK = Regex("""(?:на|в|к|до)\s+следующ[а-яё]*\s+недел[а-яё]*""")

    /** «после пар» — сегодня вечером. */
    private val RX_AFTER_CLASSES = Regex("""после\s+пар(?:ы|ам|ах|а|ой)?(?![а-яё])""")

    /** «через 2 дня», «через пару дней», «через неделю». */
    private val NUM_WORDS = mapOf(
        "один" to 1, "одну" to 1, "два" to 2, "две" to 2, "двое" to 2, "пару" to 2, "пара" to 2,
        "три" to 3, "трое" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
        "восемь" to 8, "девять" to 9, "десять" to 10,
    )

    private val RX_IN_DAYS = Regex("""через\s+(?:(\d{1,2}|[а-яё]+)\s+)?(?:дн[а-яё]*|день|сут[а-яё]*)""")
    private val RX_IN_WEEKS = Regex("""через\s+(?:(\d{1,2}|[а-яё]+)\s+)?недел[а-яё]*""")

    fun isHomework(text: String): Boolean {
        val t = text.lowercase()
        if (t.length < 6) return false
        return KEYS.any { t.contains(it) } || KEY_RE.any { it.containsMatchIn(t) }
    }

    /** Срок, если он понятен из текста. Время — 20:00 вечера. */
    fun dueFrom(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val t = text.lowercase()
        val today = now.toLocalDate()

        Regex("""(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?""").find(t)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            if (d !in 1..31 || mo !in 1..12) return@let
            var date = runCatching { LocalDate.of(today.year, mo, d) }.getOrNull() ?: return@let
            if (date.isBefore(today)) date = date.plusYears(1)
            return date.atTime(DEADLINE)
        }

        // «25 ноя», «5 апреля», «8-го марта»
        Regex("""(\d{1,2})(?:\s*[-‑]?\s*(?:го|е|ое))?\s+([а-яё]{3,})""").findAll(t).forEach { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@forEach
            val mo = monthOf(m.groupValues[2]) ?: return@forEach
            if (d !in 1..31) return@forEach
            var date = runCatching { LocalDate.of(today.year, mo, d) }.getOrNull() ?: return@forEach
            if (date.isBefore(today)) date = date.plusYears(1)
            return date.atTime(DEADLINE)
        }

        // «25-го», «25 число», «к 25-му»
        RX_DAY_NUM.find(t)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            if (d !in 1..31) return@let
            var date = runCatching { LocalDate.of(today.year, today.monthValue, d) }.getOrNull() ?: return@let
            if (date.isBefore(today)) date = date.plusMonths(1)
            return date.atTime(DEADLINE)
        }

        // «в эту пятницу» — ближайший такой день, сегодня тоже подходит
        RX_THIS_WEEK_DOW.find(t)?.let { m ->
            val dow = dowOf(m.groupValues[1]) ?: return@let
            return thisDow(today, dow).atTime(DEADLINE)
        }

        // «в следующий вторник» — уже на следующей неделе
        RX_NEXT_WEEK_DOW.find(t)?.let { m ->
            val dow = dowOf(m.groupValues[1]) ?: return@let
            return nextWeekDow(today, dow).atTime(DEADLINE)
        }

        if (t.contains("послезавтра")) return today.plusDays(2).atTime(DEADLINE)
        if (t.contains("завтра")) return today.plusDays(1).atTime(DEADLINE)
        if (t.contains("сегодня")) return today.atTime(DEADLINE)

        // «через 2 дня», «через неделю»
        daysAhead(t)?.let { return today.plusDays(it.toLong()).atTime(DEADLINE) }
        weeksAhead(t)?.let { return today.plusWeeks(it.toLong()).atTime(DEADLINE) }

        // «к концу недели» / «до конца недели» — воскресенье
        RX_END_WEEK.find(t)?.let { m ->
            val ahead = if (m.value.contains("следующ")) 1 else 0
            return endOfWeek(today, ahead).atTime(DEADLINE)
        }

        // «в начале недели» — понедельник
        RX_START_WEEK.find(t)?.let { m ->
            var date = startOfWeek(today)
            if (m.value.contains("следующ") || date.isBefore(today)) date = date.plusWeeks(1)
            return date.atTime(DEADLINE)
        }

        // «на этой неделе»
        if (RX_THIS_WEEK.containsMatchIn(t)) return endOfWeek(today, 0).atTime(DEADLINE)

        // «на следующей неделе»
        if (RX_NEXT_WEEK.containsMatchIn(t)) return endOfWeek(today, 1).atTime(DEADLINE)

        RX_DOW.find(t)?.let { m ->
            val dow = dowOf(m.value) ?: return@let
            return nextDow(today, dow).atTime(DEADLINE)
        }

        Regex("""к\s+(\d{1,2})\s*(?:числ|ому|го)""").find(t)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            if (d !in 1..31) return@let
            var date = runCatching { LocalDate.of(today.year, today.monthValue, d) }.getOrNull() ?: return@let
            if (date.isBefore(today)) date = date.plusMonths(1)
            return date.atTime(DEADLINE)
        }

        // «после пар» — сегодня вечером
        if (RX_AFTER_CLASSES.containsMatchIn(t)) return today.atTime(DEADLINE)
        return null
    }

    private fun monthOf(word: String): Int? =
        MONTHS.firstOrNull { word.startsWith(it.first) }?.second ?: MONTH_SHORT[word]

    private fun dowOf(word: String): DayOfWeek? =
        DOWS.firstOrNull { word.startsWith(it.first) }?.second

    /** Ближайший такой день недели, сегодня тоже годится. */
    private fun thisDow(today: LocalDate, dow: DayOfWeek): LocalDate {
        var d = today
        var i = 0
        while (d.dayOfWeek != dow && i < 7) {
            d = d.plusDays(1)
            i++
        }
        return d
    }

    /** Ближайший такой день строго после сегодня — прежнее поведение. */
    private fun nextDow(today: LocalDate, dow: DayOfWeek): LocalDate = thisDow(today.plusDays(1), dow)

    /** День недели на следующей календарной неделе. */
    private fun nextWeekDow(today: LocalDate, dow: DayOfWeek): LocalDate =
        startOfWeek(today).plusWeeks(1).plusDays((dow.value - 1).toLong())

    private fun startOfWeek(today: LocalDate): LocalDate =
        today.minusDays((today.dayOfWeek.value - 1).toLong())

    private fun endOfWeek(today: LocalDate, weeksAhead: Int): LocalDate =
        startOfWeek(today).plusDays(6 + weeksAhead * 7L)

    private fun daysAhead(t: String): Int? {
        val m = RX_IN_DAYS.find(t) ?: return null
        val raw = m.groupValues[1]
        if (raw.isBlank()) return 1
        val n = raw.toIntOrNull() ?: NUM_WORDS[raw] ?: return null
        return n.coerceIn(1, 31)
    }

    private fun weeksAhead(t: String): Int? {
        val m = RX_IN_WEEKS.find(t) ?: return null
        val raw = m.groupValues[1]
        if (raw.isBlank()) return 1
        val n = raw.toIntOrNull() ?: NUM_WORDS[raw] ?: return null
        return n.coerceIn(1, 8)
    }

    /** Короткое имя предмета из названия чата. */
    fun subjectFrom(chat: String): String =
        chat.substringBefore(" —").substringBefore(" |").substringBefore("·").trim().take(40)

    private val DEADLINE = LocalTime.of(20, 0)
}
