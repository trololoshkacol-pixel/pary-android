package com.pary.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime

/**
 * A route-level access/method error does not prove that every schedule endpoint
 * is unavailable: Platonus deployments expose different route sets.
 */
internal fun shouldTryNextScheduleEndpoint(code: Int): Boolean = code == 403 || code == 404 || code == 405

/**
 * Неофициальный клиент REST API Платонуса (тот же, что использует
 * мобильное приложение Platonus Student).
 *
 * Вход: POST {base}/rest/api/login {login, password} → auth_token,
 * дальше все запросы с заголовком token.
 */
class Platonus(private val baseUrlRaw: String, private val login: String, private val password: String) {

    private var token: String? = null
    private val cookieManager = java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)

    /**
     * Приводим адрес к виду https://host[:port]: пользователь может вставить с путём
     * (/#/login, /login), со слэшем, с пробелами или с http:// — пароль по открытому
     * каналу не шлём, да и Android блокирует http, поэтому всегда https.
     */
    val baseUrl: String
        get() {
            var u = baseUrlRaw.trim()
            if (u.isEmpty()) return ""
            if (!u.contains("://")) u = "https://$u"
            val parsed = runCatching { URL(u) }.getOrNull() ?: return ""
            val host = parsed.host.orEmpty()
            if (host.isBlank()) return ""
            val port = if (parsed.port != -1) ":${parsed.port}" else ""
            return "https://$host$port"
        }

    data class Result<T>(val value: T? = null, val error: String = "")

    data class Schedule(val lessons: List<Lesson>, val version: String, val week: Int?)

    private class Net(val code: Int, val text: String, val transient: Boolean = false)

    private fun chainOf(e: Throwable): List<Throwable> = generateSequence(e) { it.cause }.take(6).toList()

    /** Понятное описание сетевой ошибки — по цепочке причин, а не по первому исключению. */
    private fun explain(e: Throwable): String {
        val chain = chainOf(e)
        val all = chain.joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
        return when {
            e is SecurityException -> "нет доступа в интернет (проверь разрешение)"
            e is java.net.MalformedURLException -> "некорректный адрес"
            all.contains("Cleartext", ignoreCase = true) -> "адрес http не разрешён, нужен https"
            chain.any { it is java.net.UnknownHostException } -> "адрес не найден (проверь написание и интернет)"
            chain.any { it is java.net.SocketTimeoutException } -> "сервер не ответил вовремя"
            chain.any { it is java.net.ConnectException } -> "сервер не принял соединение (недоступен или неверный порт)"
            chain.any { it is java.security.cert.CertificateExpiredException } -> "у сервера истёк сертификат"
            chain.any { it is java.security.cert.CertificateException || it is java.security.cert.CertPathValidatorException } ->
                "сертификат сервера не доверенный"
            all.contains("Trust anchor", ignoreCase = true) -> "сертификат сервера не доверенный"
            chain.any { it is javax.net.ssl.SSLException } -> "ошибка защищённого соединения"
            chain.any { it is java.net.SocketException } -> "соединение оборвалось"
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    /** Временный сбой сети, который имеет смысл повторить один раз. */
    private fun isTransient(e: Throwable): Boolean {
        val chain = chainOf(e)
        if (chain.any {
                it is java.net.UnknownHostException ||
                    it is java.security.cert.CertificateException ||
                    it is java.security.cert.CertPathValidatorException
            }
        ) return false
        return chain.any {
            it is java.net.SocketTimeoutException || it is java.net.SocketException || it is java.io.EOFException
        }
    }

    private fun httpHint(code: Int): String = when (code) {
        400 -> "неверный запрос (400)"
        401 -> "сессия истекла или неверный логин/пароль (401)"
        403 -> "сервер запретил запрос (403); проверь доступ к API Платонуса у администратора вуза"
        404 -> "по этому адресу нет Платонуса или у него другая версия (404)"
        408, 504 -> "сервер не ответил вовремя ($code)"
        429 -> "слишком много попыток, подожди пару минут (429)"
        in 500..599 -> "сервер Платонуса сейчас недоступен ($code)"
        else -> "код $code"
    }

    /** Текст ошибки по результату open(): для -1 в text лежит причина сбоя сети. */
    private fun failText(code: Int, text: String): String =
        if (code < 0) "нет связи — $text" else httpHint(code)

    /** Redirects carrying credentials or a session token stay on the exact HTTPS origin. */
    private fun sameSecureOrigin(a: String, b: String): Boolean = runCatching {
        val from = URL(a)
        val to = URL(b)
        fun effectivePort(url: URL): Int = if (url.port != -1) url.port else 443
        from.protocol.equals("https", ignoreCase = true) &&
            to.protocol.equals("https", ignoreCase = true) &&
            from.host.equals(to.host, ignoreCase = true) &&
            effectivePort(from) == effectivePort(to)
    }.getOrDefault(false)

    private fun request(fullUrl: String, method: String, body: String?, withToken: Boolean, hops: Int): Net {
        val conn = try {
            URL(fullUrl).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Net(-1, explain(e))
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4240.198 Safari/537.36")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            runCatching {
                cookieManager.get(java.net.URI(fullUrl), emptyMap()).forEach { (key, values) ->
                    if (values.isNotEmpty()) conn.setRequestProperty(key, values.joinToString("; "))
                }
            }
            conn.setRequestProperty("language", "1")
            token?.takeIf { withToken }?.let { conn.setRequestProperty("token", it) }
            conn.instanceFollowRedirects = false
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            runCatching { cookieManager.put(java.net.URI(fullUrl), conn.headerFields) }
            if ((code == 301 || code == 302 || code == 307 || code == 308) && hops < 3) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                val next = if (loc.isNullOrBlank()) null
                else runCatching { URL(URL(fullUrl), loc).toString() }.getOrNull()
                // за перенаправлением идём только на тот же хост — токен и пароль на чужой сайт не уходят
                if (next != null && sameSecureOrigin(fullUrl, next)) {
                    return request(next, method, body, withToken, hops + 1)
                }
                return Net(code, "")
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            conn.disconnect()
            return Net(code, text)
        } catch (e: Exception) {
            conn.disconnect()
            return Net(-1, explain(e), isTransient(e))
        }
    }

    /** Запрос с одним повтором при временном сбое сети. При сбое возвращает -1 и причину текстом. */
    private fun open(path: String, method: String, body: String?, withToken: Boolean): Pair<Int, String> {
        var net = request("$baseUrl$path", method, body, withToken, 0)
        if (net.transient) {
            Thread.sleep(700)
            net = request("$baseUrl$path", method, body, withToken, 0)
        }
        return net.code to net.text
    }

    /** GET с токеном: при 401 один раз заново входит и повторяет запрос. */
    private fun getAuthed(path: String): Pair<Int, String> {
        var r = open(path, "GET", null, true)
        if (r.first == 401) {
            token = null
            if (auth().isEmpty()) r = open(path, "GET", null, true)
        }
        return r
    }

    fun version(): String {
        val (code, text) = open("/rest/api/version", "GET", null, false)
        if (code != 200) return ""
        return runCatching { JSONObject(text).optString("VERSION") }.getOrDefault("")
    }

    fun auth(): String {
        if (baseUrl.isEmpty()) return "Не указан адрес Платонуса или он введён неверно"
        if (login.isBlank() || password.isBlank()) return "Нужны логин и пароль"
        val body = JSONObject()
            .put("login", login.trim())
            .put("password", password)
            .put("IIN", JSONObject.NULL)
            .put("icNumber", JSONObject.NULL)
            .put("authForDeductedStudentsAndGraduates", false)
            .toString()
        val (code, text) = open("/rest/api/login", "POST", body, false)
        if (code < 0) return "Не удалось связаться с сервером ${baseUrl.removePrefix("https://")}: $text"
        val obj = runCatching { JSONObject(text) }.getOrNull()
            ?: return if (code in 200..299) "Сервер ответил не JSON — адрес не похож на Платонус"
            else if (code == 403) "Сервер запретил запрос авторизации (403). Это ограничение доступа к API, а не подтверждение неверного пароля."
            else "Вход не удался: ${httpHint(code)}"
        val status = obj.optString("login_status")
        if (status != "success") {
            val msg = obj.optString("message").ifBlank {
                if (status.isBlank()) httpHint(code) else "статус «$status», проверь логин и пароль"
            }
            return "Вход не удался: $msg"
        }
        val t = obj.optString("auth_token")
        if (t.isBlank()) return "Сервер не выдал токен"
        token = t
        return ""
    }

    /** Расписание студента. Возвращает ошибку текстом либо разобранное расписание. */
    fun schedule(): Result<Schedule> {
        if (token == null) {
            val err = auth()
            if (err.isNotEmpty()) return Result(error = err)
        }

        // Разные сборки Platonus используют разные маршруты расписания.
        // Сначала проверяем основной mobile endpoint, затем известные варианты.
        val paths = listOf(
            "/rest/schedule/userSchedule/student/initial/",
            "/rest/mobile/schedule/student/ru",
            "/rest/mobile/student/schedule/ru",
            "/rest/mobile/schedule/ru",
            "/rest/mobile/student/timetable/ru",
            "/rest/mobile/timetable/ru",
            "/rest/schedule/student/ru",
        )
        var lastError = "Сервер не вернул расписание"
        var gotScheduleResponse = false
        val deniedPaths = mutableListOf<String>()
        val unavailablePaths = mutableListOf<String>()

        for (path in paths) {
            val (code, body) = getAuthed(path)
            if (code == 401) return Result(error = "Сессия истекла, войди ещё раз")
            if (shouldTryNextScheduleEndpoint(code)) {
                if (code == 403) deniedPaths += path else unavailablePaths += path
                lastError = when (code) {
                    403 -> "Доступ к маршруту расписания запрещён: $path"
                    404 -> "Маршрут расписания не найден: $path"
                    else -> "Маршрут расписания не поддерживает GET: $path"
                }
                continue
            }
            if (code != 200) {
                return Result(error = "Расписание недоступно: ${failText(code, body)}")
            }

            gotScheduleResponse = true
            val root = runCatching { JSONObject(body) }.getOrNull()
            if (root == null) {
                lastError = "Ответ $path не является JSON"
                continue
            }
            val parsed = runCatching { parse(root) }.getOrElse {
                lastError = "Неожиданный формат ответа $path: ${it.message}"
                null
            } ?: continue

            if (parsed.lessons.isNotEmpty()) return Result(value = parsed)
            lastError = "Ответ $path получен, но пары не распознаны (${keysOf(body)})"
        }

        val finalError = when {
            gotScheduleResponse -> lastError
            deniedPaths.isNotEmpty() -> "Доступ запрещён для ${deniedPaths.size} маршрутов расписания; остальные не найдены или не поддерживают GET"
            unavailablePaths.isNotEmpty() -> "Проверено ${unavailablePaths.size} альтернативных маршрутов, ни один не подошёл. Последний ответ: $lastError"
            else -> "Не найден доступный маршрут расписания. Последняя ошибка: $lastError"
        }
        return Result(error = finalError)
    }

    private fun keysOf(text: String): String = runCatching {
        val o = JSONObject(text)
        "поля: " + o.keys().asSequence().joinToString(",").take(200)
    }.getOrElse {
        runCatching {
            val a = JSONArray(text)
            "массив ${a.length()}" + (a.optJSONObject(0)?.let { "; поля: " + it.keys().asSequence().joinToString(",").take(160) } ?: "")
        }.getOrDefault("не JSON")
    }

    /** Диагностика: имена полей первого экзамена — по ним видно, как парсить дату и вид. */
    private fun examKeysOf(text: String): String = runCatching {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val exams = o.optJSONArray("exams") ?: continue
            val first = exams.optJSONObject(0)
                ?: return@runCatching "экзамены: ${exams.length()} шт, у первого нет полей"
            return@runCatching "экзамены: ${exams.length()} шт; поля: " +
                first.keys().asSequence().joinToString(",").take(200)
        }
        "экзамены: не найдены"
    }.getOrDefault("экзамены: не разобрать")

    /** Диагностика: коды ответов и имена полей, без личных данных. */
    fun diagnose(): String {
        val sb = StringBuilder()
        sb.appendLine("адрес: " + baseUrl.ifBlank { "не указан" })
        val v = runCatching { version() }.getOrDefault("")
        sb.appendLine("версия: " + v.ifBlank { "нет ответа" })
        val err = runCatching { auth() }.getOrElse { "ошибка: ${it.message}" }
        if (err.isNotEmpty()) return sb.append("вход: $err").toString()
        sb.appendLine("вход: ок")

        val (yc, yt) = open("/rest/mobile/student/studyYears/ru", "GET", null, true)
        var year = 2025
        var term = 1
        sb.appendLine("годы: код $yc, ${yt.length} б")
        runCatching {
            val o = JSONObject(yt)
            year = o.optInt("defaultYear", year)
            term = o.optInt("defaultTerm", term)
            val list = o.optJSONArray("studyYearList")
            val first = list?.optJSONObject(0)
            sb.appendLine("  year=$year term=$term, лет: ${list?.length() ?: 0}")
            sb.appendLine("  поля года: " + (first?.keys()?.asSequence()?.joinToString(",")?.take(160) ?: "-"))
            if (first?.optInt("year", 0)?.let { it > 0 } == true) year = first!!.optInt("year")
        }.onFailure { sb.appendLine("  разбор не удался") }

        for (t in listOf(term, if (term == 1) 2 else 1)) {
            val (jc, jt) = open("/rest/api/journal/$year/$t/ru", "GET", null, true)
            sb.appendLine("журнал $year/$t: код $jc, ${jt.length} б | " + keysOf(jt))
            if (jc == 200) sb.appendLine("  " + examKeysOf(jt))
        }

        val candidates = listOf(
            "/rest/mobile/schedule/student/ru",
            "/rest/mobile/student/schedule/ru",
            "/rest/mobile/schedule/ru",
            "/rest/mobile/student/timetable/ru",
            "/rest/mobile/timetable/ru",
            "/rest/schedule/student/ru",
            "/rest/schedule/userSchedule/student/initial/",
        )
        for (c in candidates) {
            val (code, text) = open(c, "GET", null, true)
            sb.appendLine(c.removePrefix("/rest/") + ": $code, ${text.length} б" +
                if (code == 200) " | " + keysOf(text) else "")
        }
        return sb.toString()
    }

    /**
     * Достаёт из JSON «человеческий» текст по куску имени поля.
     *
     * Фрагменты важнее по порядку: при вызове pickFrag(o, "date", "period")
     * ключ с "date" будет взят раньше ключа с "period". Ключи-идентификаторы (…ID) пропускаются,
     * иначе вместо названия экзамена прилетит «1».
     * Если значение — вложенный объект/массив (бывает у period, type, form),
     * поиск повторяется внутри него.
     */
    private fun pickFrag(o: JSONObject, vararg frags: String): String {
        for (frag in frags) {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (isIdKey(k) || !k.contains(frag, ignoreCase = true)) continue
                val v = textValue(o.opt(k))
                if (v.isNotEmpty()) return v
            }
        }
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (isIdKey(k)) continue
            val nested = when (val child = o.opt(k)) {
                is JSONObject -> child
                is JSONArray -> child.optJSONObject(0)
                else -> null
            } ?: continue
            val found = pickFrag(nested, *frags)
            if (found.isNotEmpty()) return found
        }
        return ""
    }

    private fun isIdKey(key: String): Boolean =
        key.endsWith("id", ignoreCase = true) || key.endsWith("ids", ignoreCase = true)

    /** Строковое/числовое значение поля; объекты и массивы на этом уровне не строки. */
    private fun textValue(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is String -> v.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
        is Number -> normalizeNum(v.toString())
        else -> ""
    }

    /** "85.0" → "85", "4.5" → "4.5", пусто/"null" → "", прочее — как пришло. */
    private fun normalizeNum(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t.equals("null", ignoreCase = true)) return ""
        val d = t.toDoubleOrNull() ?: return t
        if (d.isNaN() || d.isInfinite()) return t
        return if (d % 1.0 == 0.0) d.toLong().toString() else t
    }

    /** Учебный год и семестр по умолчанию из профиля Платонуса. */
    fun years(): Pair<Int, Int>? {
        if (token == null) return null
        val (code, text) = getAuthed("/rest/mobile/student/studyYears/ru")
        if (code != 200) return null
        return runCatching {
            val o = JSONObject(text)
            var y = o.optInt("defaultYear", 0)
            val t = o.optInt("defaultTerm", 1)
            val first = o.optJSONArray("studyYearList")?.optJSONObject(0)
            if (y <= 0) y = first?.optInt("year", 0) ?: 0
            if (y > 0) y to t else null
        }.getOrNull()
    }


    /** Профиль обучающегося: ФИО, группа, курс и официальный GPA из Platonus. */
    fun profileInfo(): JSONObject? {
        if (token == null && auth().isNotEmpty()) return null
        val (code, body) = getAuthed("/rest/mobile/personInfo/ru")
        if (code != 200) return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    /** Задания студента. Маршрут и POST-параметры подтверждены публичной реализацией API-wrapper. */
    fun studentTasks(): Result<List<Task>> {
        if (token == null) {
            val error = auth()
            if (error.isNotEmpty()) return Result(error = error)
        }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val today = java.time.LocalDate.now()
        val payload = JSONObject()
            .put("countInPart", "100")
            .put("endDate", today.plusYears(2).format(formatter))
            .put("partNumber", "0")
            .put("recipientStatus", "-1")
            .put("startDate", today.minusYears(1).format(formatter))
            .put("studyGroupID", "-1")
            .put("subjectID", "-1")
            .put("term", "-1")
            .put("topic", "")
            .put("tutorID", "-1")
            .put("year", "-1")
        val (code, body) = open("/rest/assignments/studentTasks/-1/1", "POST", payload.toString(), true)
        if (code != 200) return Result(error = "Задания недоступны: ${failText(code, body)}")
        val root = runCatching { JSONObject(body) }.getOrNull()
        val array = runCatching { JSONArray(body) }.getOrNull()
            ?: root?.let { findTaskArray(it) }
            ?: return Result(error = "Platonus вернул задания в неизвестном формате")
        val parsed = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val title = pickFrag(item, "taskName", "assignmentName", "topicName", "title", "name", "topic", "description")
            if (title.isBlank()) return@mapNotNull null
            val subject = pickFrag(item, "subjectName", "disciplineName", "courseName", "subject")
            val rawDue = pickFrag(item, "endDate", "deadline", "dueDate", "dateTo", "finishDate", "dateEnd")
            val due = parseTaskDate(rawDue)
            Task(
                title = title.take(180),
                subject = subject.take(100),
                due = due,
                priority = if (due != null && due.isBefore(java.time.LocalDateTime.now().plusDays(2))) 2 else 1,
                note = pickFrag(item, "description", "comment").take(500),
                source = "platonus",
            )
        }.distinctBy { "${it.title.lowercase()}|${it.subject.lowercase()}|${it.due}" }
        return Result(value = parsed)
    }

    /** Подробные записи журнала по предмету; могут содержать ежедневные отметки и пропуски. */
    fun journalRecords(year: Int, term: Int, subjectId: String): Result<String> {
        if (token == null) {
            val error = auth()
            if (error.isNotEmpty()) return Result(error = error)
        }
        if (subjectId.isBlank() || subjectId == "0") return Result(error = "У предмета нет ID для запроса записей")
        val (code, body) = getAuthed("/rest/mobile/journal/records/$year/$term?subjectID=$subjectId")
        return if (code == 200) Result(value = body)
        else Result(error = "Записи журнала недоступны: ${failText(code, body)}")
    }

    private fun findTaskArray(root: JSONObject): JSONArray? {
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = root.opt(key)
            if (value is JSONArray && key.contains("task", ignoreCase = true)) return value
            if (value is JSONObject) findTaskArray(value)?.let { return it }
            if (value is JSONArray) {
                for (i in 0 until value.length()) {
                    val child = value.optJSONObject(i) ?: continue
                    findTaskArray(child)?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseTaskDate(raw: String): java.time.LocalDateTime? {
        val value = raw.trim()
        if (value.isEmpty() || value == "null") return null
        runCatching { return java.time.LocalDateTime.parse(value) }
        runCatching { return java.time.LocalDate.parse(value).atTime(23, 59) }
        runCatching {
            return java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")).atTime(23, 59)
        }
        runCatching {
            return java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")).atTime(23, 59)
        }
        return value.toLongOrNull()?.takeIf { it > 1_000_000_000L }?.let {
            runCatching {
                val millis = if (it > 100_000_000_000L) it else it * 1000L
                java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            }.getOrNull()
        }
    }


    /** Записи посещаемости берём только из явных полей статуса, не принимая обычные оценки за пропуски. */
    fun attendanceRecords(year: Int, term: Int, subjects: List<JournalSubject>): List<PlatonusAttendance> {
        val output = mutableListOf<PlatonusAttendance>()
        for (subject in subjects) {
            val id = subject.remoteId
            if (id.isBlank() || id == "0") continue
            val response = journalRecords(year, term, id).value ?: continue
            val objects = collectObjects(response)
            for (entry in objects) {
                val keys = entry.keys().asSequence().toList()
                val statusKey = keys.firstOrNull { key ->
                    listOf("attendance", "presence", "visit", "absence").any { key.contains(it, true) }
                } ?: keys.firstOrNull { it.equals("status", true) }
                val rawStatus = statusKey?.let { textValue(entry.opt(it)) }.orEmpty().trim()
                if (rawStatus.isBlank()) continue
                val normalized = rawStatus.lowercase()
                val absent = normalized in setOf("н", "нб", "n", "absent", "absence", "missed", "пропуск", "неявка", "false", "0")
                val present = normalized in setOf("п", "б", "y", "yes", "present", "visited", "посетил", "явка", "true", "1")
                if (!absent && !present) continue
                val rawDate = pickFrag(entry, "lessonDate", "visitDate", "date", "day")
                val dateText = formatJournalDate(rawDate)
                val date = runCatching { java.time.LocalDate.parse(dateText, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull() ?: continue
                val timeText = pickFrag(entry, "lessonTime", "startTime", "time")
                val time = timeOf(timeText) ?: LocalTime.NOON
                output += PlatonusAttendance("${date}@${time}", subject.name, present, rawStatus)
            }
        }
        return output.distinctBy { it.key + it.subject }
    }

    private fun collectObjects(raw: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    out += value
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
            }
        }
        runCatching { visit(JSONObject(raw)) }.onFailure { runCatching { visit(JSONArray(raw)) } }
        return out
    }

    fun journal(year: Int, term: Int): Result<List<JournalSubject>> {
        if (token == null) {
            val err = auth()
            if (err.isNotEmpty()) return Result(error = err)
        }
        val (code, text) = getAuthed("/rest/api/journal/$year/$term/ru")
        if (code == 401) return Result(error = "Сессия истекла, войди ещё раз")
        if (code != 200) return Result(error = "Журнал недоступен: ${failText(code, text)}")
        val arr = runCatching { JSONArray(text) }.getOrNull()
            ?: runCatching { JSONObject(text).optJSONArray("journal") }.getOrNull()
            ?: return Result(error = "Неожиданный формат журнала")
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("subjectName").trim()
            if (name.isEmpty()) return@mapNotNull null
            JournalSubject(
                name = name,
                total = normalizeNum(o.optString("totalMark")),
                center = normalizeNum(o.optString("centerMark")),
                tutors = tutorsOf(o),
                exams = examsOf(o),
                color = normalizeColor(o.optString("color")),
                remoteId = listOf("subjectID", "subjectId", "disciplineID", "ID", "id")
                    .firstNotNullOfOrNull { key -> o.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" } }.orEmpty(),
            )
        }
        return Result(value = list)
    }

    /** Преподаватели предмета: tutorList[].fio / name / tutorName. */
    private fun tutorsOf(o: JSONObject): String {
        val arr = o.optJSONArray("tutorList") ?: return ""
        return (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i) ?: return@mapNotNull null
            t.optString("fio").ifBlank { t.optString("name").ifBlank { t.optString("tutorName") } }.trim()
        }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
    }

    /**
     * Экзамены предмета. Сервер отдаёт массив, но встречается и один объект,
     * и строка с JSON — все три вида разбираем.
     */
    private fun examsOf(o: JSONObject): List<JournalExam> {
        val raw = o.opt("exams") ?: return emptyList()
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            is String -> runCatching { JSONArray(raw) }.getOrNull()
            else -> null
        } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> examOf(arr, i) }
    }

    private fun examOf(arr: JSONArray, i: Int): JournalExam? = when (val raw = runCatching { arr.get(i) }.getOrNull()) {
        is JSONObject -> {
            val title = examTitle(raw)
            val date = formatJournalDate(pickFrag(raw, "examDate", "date", "period", "begin", "start", "day"))
            val mark = normalizeNum(pickFrag(raw, "mark", "grade", "ball", "point", "score", "result"))
            if (title.isEmpty() && date.isEmpty() && mark.isEmpty()) null
            else JournalExam(title = title.ifEmpty { "Экзамен" }, date = date, mark = mark)
        }
        is String -> {
            val date = formatJournalDate(raw)
            if (date.isEmpty()) null else JournalExam(title = "Экзамен", date = date)
        }
        else -> null
    }

    /** Вид экзамена: сначала «тип»/«форма», затем название; чисто числовые ID не берём. */
    private fun examTitle(e: JSONObject): String = listOf(
        pickFrag(e, "examTypeName", "typeName", "examType", "type", "controlFormName", "controlForm", "form"),
        pickFrag(e, "examName", "title", "name"),
    ).firstOrNull { it.isNotEmpty() && it.toDoubleOrNull() == null }.orEmpty()

    private val isoDateRe = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val dottedDateRe = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

    /** Дата экзамена → «дд.ММ.гггг»: epoch, ISO, «15.01.2025» или период из двух дат. */
    private fun formatJournalDate(raw: String): String {
        val t = raw.trim().trim('"')
        if (t.isEmpty() || t.equals("null", ignoreCase = true)) return ""
        t.toLongOrNull()?.takeIf { it > 1_000_000_000L }?.let { epoch ->
            val ms = if (epoch > 100_000_000_000L) epoch else epoch * 1000L
            runCatching {
                java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            }.getOrNull()?.let { return it }
        }
        isoDateRe.find(t)?.let { m ->
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            return "%02d.%02d.%04d".format(d, mo, y)
        }
        dottedDateRe.find(t)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: return@let
            return "%02d.%02d.%04d".format(d, mo, y)
        }
        return t
    }

    /** Цвет из журнала → "#RRGGBB"/"#AARRGGBB"; мусор, имя цвета или пусто → "". */
    private fun normalizeColor(raw: String): String {
        var s = raw.trim().trim('"')
        if (s.isEmpty() || s.equals("null", ignoreCase = true)) return ""
        s = s.removePrefix("0x").removePrefix("0X").removePrefix("#")
        // сервер может прислать цвет числом ARGB, например -65536
        if (s.isNotEmpty() && s.dropWhile { it == '-' }.all { it.isDigit() }) {
            val n = s.toLongOrNull() ?: return ""
            return "#%06X".format(n and 0xFFFFFF)
        }
        if (s.length != 6 && s.length != 8) return ""
        if (s.any { it.digitToIntOrNull(16) == null }) return ""
        return "#" + s.uppercase()
    }

    private fun parse(root: JSONObject): Schedule {
        val hours = mutableMapOf<Int, Pair<LocalTime, LocalTime>>()
        val hourArr = root.optJSONArray("lessonHours")
        if (hourArr != null) {
            for (i in 0 until hourArr.length()) {
                val h = hourArr.optJSONObject(i) ?: continue
                val number = h.optInt("number", 0)
                val start = timeOf(h.optString("start"))
                val finish = timeOf(h.optString("finish"))
                if (start != null && finish != null) hours[number] = start to finish
            }
        }

        val out = mutableListOf<Lesson>()
        val days = root.optJSONObject("timetable")?.optJSONObject("days")
        if (days != null) {
            for (d in 1..7) {
                val dayObj = days.optJSONObject(d.toString()) ?: continue
                val lessonsObj = dayObj.optJSONObject("lessons") ?: continue
                val keys = lessonsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val slotNumber = key.toIntOrNull() ?: 0
                    val slotObj = lessonsObj.optJSONObject(key) ?: continue
                    val list = slotObj.optJSONArray("lessons") ?: continue
                    for (i in 0 until list.length()) {
                        val l = list.optJSONObject(i) ?: continue
                        if (l.optBoolean("empty", false)) continue
                        val name = l.optString("subjectName").trim()
                        if (name.isEmpty()) continue
                        val number = l.optInt("lessonHour", 0).let { if (it > 0) it else slotNumber }
                        val range = hours[number] ?: continue
                        val room = listOf(l.optString("auditory"), l.optString("building"))
                            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                        val teacher = l.optString("tutorShortName").ifBlank { l.optString("tutorName") }
                        out.add(
                            Lesson(
                                day = d - 1,
                                parity = Parity.BOTH,
                                start = range.first,
                                end = range.second,
                                name = name,
                                room = room,
                                teacher = teacher.trim(),
                                quiet = true,
                                source = "platonus",
                            ),
                        )
                    }
                }
            }
        }

        val sorted = out.distinctBy { "${it.day}-${it.start}-${it.name}" }
            .sortedWith(compareBy({ it.day }, { it.start }))
        return Schedule(sorted, "", root.optInt("activeWeekNumber", 0).takeIf { it > 0 })
    }

    private fun timeOf(raw: String): LocalTime? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return runCatching { LocalTime.parse(t.take(8)) }.getOrNull()
            ?: runCatching { LocalTime.parse(t.take(5)) }.getOrNull()
    }

    companion object {
        /** Учебный год и семестр по дате, если сервер не отдал свои. */
        fun guessYear(): Pair<Int, Int> {
            val now = java.time.LocalDate.now()
            return if (now.monthValue >= 9) now.year to 1 else (now.year - 1) to 2
        }
    }
}

/** Один проход синхронизации: логин, версия, расписание. Без Android UI. */
object PlatonusSync {
    data class Out(val message: String, val lessons: List<Lesson>?, val version: String = "")

    fun fetch(url: String, login: String, password: String): Out {
        if (url.isBlank()) return Out("Укажи адрес Платонуса, например edu.vuz.kz", null)
        if (login.isBlank() || password.isBlank()) return Out("Нужны логин и пароль", null)
        val p = Platonus(url, login, password)
        if (p.baseUrl.isEmpty()) return Out("Адрес «${url.trim()}» не похож на адрес сайта. Пример: platonus.kstu.kz", null)
        // Вход без предварительной проверки версии: на некоторых серверах /version закрыт,
        // а вход работает; точная причина сбоя сети приходит из auth().
        val authError = runCatching { p.auth() }.getOrElse { "Ошибка сети: ${it.message}" }
        if (authError.isNotEmpty()) return Out(authError, null)
        val version = runCatching { p.version() }.getOrDefault("")
        val vText = if (version.isBlank()) "" else " (Platonus $version)"
        val res = runCatching { p.schedule() }.getOrElse { return Out("Ошибка сети: ${it.message}", null, version) }
        val sch = res.value ?: return Out(res.error.ifBlank { "Расписание не пришло" }, null, version)
        if (sch.lessons.isEmpty()) {
            return Out("Вход прошёл$vText, но в расписании нет пар. Возможно, каникулы или другой формат.", null, version)
        }
        return Out(
            "Готово:$vText пар в расписании — ${sch.lessons.size}",
            sch.lessons,
            version,
        )
    }
}

/** Синхронизация оценок: вход, учебный год, журнал. */
object PlatonusGrades {
    data class Out(val message: String, val subjects: List<JournalSubject>?)

    fun fetch(url: String, login: String, password: String): Out {
        if (url.isBlank()) return Out("Укажи адрес Платонуса", null)
        if (login.isBlank() || password.isBlank()) return Out("Нужны логин и пароль", null)
        val p = Platonus(url, login, password)
        if (p.baseUrl.isEmpty()) return Out("Адрес «${url.trim()}» не похож на адрес сайта. Пример: platonus.kstu.kz", null)
        val err = runCatching { p.auth() }.getOrElse { "Ошибка сети: ${it.message}" }
        if (err.isNotEmpty()) return Out(err, null)
        // Если сервер не отдал учебный год — берём по дате, а не прерываем синхронизацию.
        val (year, term) = runCatching { p.years() }.getOrNull() ?: Platonus.guessYear()
        var res = runCatching { p.journal(year, term) }.getOrElse { return Out("Ошибка сети: ${it.message}", null) }
        var list = res.value.orEmpty()
        if (list.isEmpty()) {
            val other = if (term == 1) 2 else 1
            res = runCatching { p.journal(year, other) }.getOrElse { return Out("Ошибка сети: ${it.message}", null) }
            list = res.value.orEmpty()
        }
        if (list.isEmpty()) {
            return Out("Журнал за $year год пришёл пустым" + res.error.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty(), null)
        }
        val withMarks = list.count { (it.total.toDoubleOrNull() ?: 0.0) > 0.0 }
        return Out("Журнал: $year год, ${list.size} предметов, с итогом — $withMarks", list)
    }
}


/** Полная синхронизация только по реально существующим и проверяемым API-методам. */
object PlatonusFullSync {
    data class Out(
        val message: String,
        val version: String,
        val lessons: List<Lesson>?,
        val subjects: List<JournalSubject>?,
        val tasks: List<Task>?,
        val attendance: List<PlatonusAttendance>?,
        val gpa: String,
        val name: String,
        val group: String,
        val scheduleError: String = "",
    )

    fun fetch(url: String, login: String, password: String): Out {
        val client = Platonus(url, login, password)
        if (client.baseUrl.isBlank()) return Out("Неверный адрес Platonus", "", null, null, null, null, "", "", "")
        val authError = runCatching { client.auth() }.getOrElse { "Ошибка входа: ${it.message}" }
        if (authError.isNotBlank()) return Out(authError, "", null, null, null, null, "", "", "")
        val version = runCatching { client.version() }.getOrDefault("")
        val profile = runCatching { client.profileInfo() }.getOrNull()
        val gpa = profile?.let { client.readProfileValue(it, "gpa", "GPA", "averageScore", "gradePointAverage") }.orEmpty()
        val name = profile?.let { client.readProfileValue(it, "fio", "fullName", "studentName") }.orEmpty()
            .ifBlank { profile?.let { listOf("lastName", "firstName", "patronymic").map { key -> it.optString(key) }.filter(String::isNotBlank).joinToString(" ") }.orEmpty() }
        val group = profile?.let { client.readProfileValue(it, "groupName", "studyGroupName", "group") }.orEmpty()
        val (year, term) = runCatching { client.years() }.getOrNull() ?: Platonus.guessYear()
        val otherTerm = if (term == 1) 2 else 1
        val currentJournal = client.journal(year, term).value.orEmpty()
        val otherJournal = client.journal(year, otherTerm).value.orEmpty()
        val journal = (currentJournal + otherJournal).distinctBy { it.name.lowercase() }
        val attendance = client.attendanceRecords(year, term, currentJournal) +
            client.attendanceRecords(year, otherTerm, otherJournal)
        val tasksResult = client.studentTasks()
        val scheduleResult = client.schedule()
        val lessons = scheduleResult.value?.lessons?.takeIf { it.isNotEmpty() }
        val status = buildList {
            add("Авторизация успешна")
            if (version.isNotBlank()) add("Platonus $version")
            if (profile != null) add("Профиль загружен")
            if (gpa.isNotBlank()) add("GPA: $gpa")
            if (journal.isNotEmpty()) add("Предметов в журнале: ${journal.size}")
            if (tasksResult.value != null) add("Заданий: ${tasksResult.value.size}")
            if (attendance.isNotEmpty()) add("Отметок посещаемости: ${attendance.size}")
            if (lessons != null) add("Пар в расписании: ${lessons.size}")
            else add("Расписание: ${scheduleResult.error.ifBlank { "пока не получено" }}")
        }.joinToString(" · ")
        return Out(
            status, version, lessons, journal.takeIf { it.isNotEmpty() },
            tasksResult.value, attendance.distinctBy { it.key + it.subject }.takeIf { it.isNotEmpty() }, gpa, name, group, scheduleResult.error,
        )
    }

    private fun Platonus.readProfileValue(o: JSONObject, vararg keys: String): String {
        fun search(value: Any?): String {
            if (value !is JSONObject) return ""
            for (key in keys) {
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val field = iterator.next()
                    if (!field.equals(key, ignoreCase = true)) continue
                    val raw = value.opt(field)
                    if (raw != null && raw != JSONObject.NULL && raw !is JSONObject && raw !is JSONArray) {
                        val result = raw.toString().trim()
                        if (result.isNotBlank() && result != "null") return result
                    }
                }
            }
            val iterator = value.keys()
            while (iterator.hasNext()) {
                val nested = value.opt(iterator.next())
                val found = search(nested)
                if (found.isNotBlank()) return found
            }
            return ""
        }
        return search(o)
    }
}
