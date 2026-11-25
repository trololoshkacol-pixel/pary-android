package com.pary.app.data

/**
 * Маленький локальный классификатор: учится на твоих решениях
 * «это ДЗ» / «это не ДЗ». Без интернета и без модели в APK.
 */
object MiniBrain {

    private val STOP = setOf(
        "это", "как", "что", "все", "всё", "или", "или", "кто", "где", "когда",
        "если", "тоже", "уже", "ещё", "еще", "при", "для", "без", "над", "под",
        "мне", "тебе", "нам", "вам", "они", "она", "оно", "его", "её", "ее",
        "есть", "был", "была", "были", "будет", "будут", "может", "можно",
        "нужно", "надо", "очень", "просто", "сейчас", "потом", "тогда", "там",
        "тут", "этот", "эта", "эти", "тот", "та", "те", "так", "также",
    )

    /** Отмена, перенос, «не надо» — сильный минус. */
    private val NEGATION = listOf(
        Regex("""не\s+над(?:о|а)(?![а-яё])"""),
        Regex("""не\s+нужн[а-яё]*"""),
        Regex("""не\s+задав[а-яё]*"""),
        Regex("""не\s+зад(?:али|ано|ают)"""),
        Regex("""не\s+будет"""),
        Regex("""не\s+сда[её]м"""),
        Regex("""не\s+сдава[а-яё]*"""),
        Regex("""не\s+принос[а-яё]*"""),
        Regex("""не\s+дела[ею]м"""),
        Regex("""отмен(?:а|ы|е|у|ой|ою|я|ят|ю|и|ил|ила|или|ило|яет|яют|яется|яются|ено|ена|ены|ён)(?![а-яё])"""),
        Regex("""перенес[а-яё]*|перенос[а-яё]*"""),
        Regex("""нельз[а-яё]*"""),
    )

    /** Срочность и близкий срок — плюс. */
    private val URGENCY = listOf(
        Regex("""срочн[а-яё]*"""),
        Regex("""завтра(?![а-яё])|завтрашн[а-яё]*"""),
        Regex("""(?:к|до|на|перед)\s+(?:перв(?:ой|ую)\s+)?пар[а-яё]*(?![а-яё])"""),
        Regex("""сегодня(?![а-яё])|сегодняшн[а-яё]*"""),
    )

    fun tokens(text: String): List<String> =
        Regex("[а-яёa-z0-9]+").findAll(text.lowercase())
            .map { it.value }
            .filter { it.length > 2 && it !in STOP }
            .distinct()
            .toList()

    /** Оценка: ключевые слова дают старт, дальше решают выученные веса и подсказки. */
    fun score(weights: Map<String, Double>, text: String): Double {
        val base = if (ChatParser.isHomework(text)) 1.0 else 0.0
        return base + tokens(text).sumOf { weights[it] ?: 0.0 } + hints(text)
    }

    /** Плюс за срочность, минус за отмену/перенос. */
    private fun hints(text: String): Double {
        val t = text.lowercase()
        var d = 0.0
        if (NEGATION.any { it.containsMatchIn(t) }) d -= 1.0
        if (URGENCY.any { it.containsMatchIn(t) }) d += 0.5
        return d
    }

    fun judge(weights: Map<String, Double>, text: String): Boolean = score(weights, text) >= 1.0

    /** Короткая человеческая причина решения — для отладки, не длиннее 120 символов. */
    fun explain(weights: Map<String, Double>, text: String): String {
        val t = text.lowercase()
        val verdict = if (judge(weights, text)) "ДЗ" else "не ДЗ"
        val bits = mutableListOf<String>()
        val neg = firstMatch(NEGATION, t)
        val urg = firstMatch(URGENCY, t)
        if (neg != null) bits.add("минус «" + neg + "»")
        if (urg != null) bits.add("плюс «" + urg + "»")
        val all = tokens(text)
        val learnt = all.filter { (weights[it] ?: 0.0) != 0.0 }.take(2)
        if (learnt.isNotEmpty()) bits.add("веса: " + learnt.joinToString(", "))
        val found = all.filter { it !in learnt && (neg == null || !neg.contains(it)) }.take(3)
        if (found.isNotEmpty()) bits.add("нашёл: " + found.joinToString(", "))
        return if (bits.isEmpty()) verdict else (verdict + "; " + bits.joinToString("; ")).take(120)
    }

    /** То же самое, если веса не под рукой (их можно не передавать). */
    fun explain(text: String): String = explain(emptyMap(), text)

    private fun firstMatch(list: List<Regex>, text: String): String? =
        list.mapNotNull { it.find(text) }.minByOrNull { it.range.first }?.value?.trim()

    /** Обновляет веса: +0.4 за «в задачи», −0.4 за «не надо». */
    fun learn(weights: MutableMap<String, Double>, text: String, positive: Boolean): MutableMap<String, Double> {
        val delta = if (positive) 0.4 else -0.4
        tokens(text).forEach { t ->
            val w = ((weights[t] ?: 0.0) + delta).coerceIn(-3.0, 3.0)
            weights[t] = w
        }
        return weights
    }
}
