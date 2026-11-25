package com.pary.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Импорт экспортированной переписки WhatsApp / Telegram и текста из меню «Поделиться». */
object ChatImport {

    private const val MAX_BYTES = 3_000_000

    /** Принимает обычный текст, которым поделились из Telegram, WhatsApp, VK и других приложений. */
    fun parseShared(text: String, source: String = "Общий доступ"): List<ChatDraft> {
        val clipped = text.trim().take(30_000)
        if (clipped.length < 4) return emptyList()

        val chunks = clipped.split(Regex("""\n\s*\n+"""))
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.length >= 4 }
        val candidates = if (chunks.isEmpty()) listOf(clipped.replace(Regex("""\s+"""), " ").trim()) else chunks
        val out = mutableListOf<ChatDraft>()
        val seen = HashSet<String>()

        for (body in candidates) {
            if (body.length < 4 || body.length > 2_000) continue
            if (!Store.isChatHomework(body)) continue
            val norm = body.lowercase()
            if (!seen.add(norm)) continue
            out += ChatDraft(
                text = body.take(200),
                chat = source.ifBlank { "Общий доступ" },
                pkg = "share",
                due = ChatParser.dueFrom(body),
            )
            if (out.size >= 20) break
        }
        return out
    }

    /** Возвращает найденные черновики задач из экспортированной переписки. */
    fun parse(text: String, chatName: String): List<ChatDraft> {
        val clipped = if (text.length > MAX_BYTES) text.take(MAX_BYTES) else text
        val msgs = when {
            clipped.trimStart().startsWith("{") || clipped.trimStart().startsWith("[") ->
                parseTelegramJson(clipped)
            clipped.contains("class=\"message") || clipped.contains("class=\"text") ->
                parseTelegramHtml(clipped)
            else -> parseWhatsApp(clipped)
        }
        val out = mutableListOf<ChatDraft>()
        val seen = HashSet<String>()
        for (m in msgs) {
            val body = m.second.replace("\n", " ").trim()
            if (body.length < 4) continue
            if (!Store.isChatHomework(body)) continue
            val norm = body.lowercase()
            if (!seen.add(norm)) continue
            out.add(
                ChatDraft(
                    text = body.take(200),
                    chat = m.first.ifBlank { chatName },
                    pkg = "import",
                    due = ChatParser.dueFrom(body),
                ),
            )
            if (out.size >= 40) break
        }
        return out
    }

    // ---------- WhatsApp ----------

    private val WA = Regex(
        """^\[?(\d{1,2})[./](\d{1,2})[./](\d{2,4}),?\s+(\d{1,2}):(\d{2})(?::\d{2})?]?\s*(?:[–-]\s*)?([^:]{1,40}):\s*(.*)$""",
        RegexOption.MULTILINE,
    )

    private fun parseWhatsApp(text: String): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        var lastAuthor = ""
        for (line in text.lineSequence()) {
            val m = WA.find(line)
            if (m != null) {
                lastAuthor = m.groupValues[6].trim()
                val body = m.groupValues[7]
                if (body.isNotBlank()) res.add(lastAuthor to body)
            } else if (res.isNotEmpty() && line.isNotBlank() && !line.startsWith("\u200e")) {
                val prev = res.removeAt(res.size - 1)
                res.add(prev.first to (prev.second + " " + line.trim()))
            }
        }
        return res
    }

    // ---------- Telegram JSON ----------

    private fun parseTelegramJson(text: String): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        val root = runCatching { JSONObject(text) }.getOrNull()
        val arr = root?.optJSONArray("messages")
            ?: runCatching { JSONArray(text) }.getOrNull()
            ?: return res
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type", "message") != "message") continue
            val from = o.optString("from").ifBlank { o.optString("from_name") }
            val t = o.opt("text") ?: continue
            val body = when (t) {
                is String -> t
                is JSONArray -> (0 until t.length()).joinToString("") { k ->
                    val part = t.opt(k)
                    when (part) {
                        is String -> part
                        is JSONObject -> part.optString("text")
                        else -> ""
                    }
                }
                else -> ""
            }
            if (body.isNotBlank()) res.add(from to body)
        }
        return res
    }

    // ---------- Telegram HTML ----------

    private val TG_FROM = Regex("""class="from_name">\s*([^<]+)""")
    private val TG_TEXT = Regex("""class="text">\s*(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    private fun parseTelegramHtml(text: String): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        val blocks = text.split("class=\"message").drop(1)
        for (b in blocks) {
            val from = TG_FROM.find(b)?.groupValues?.get(1)?.trim().orEmpty()
            val raw = TG_TEXT.find(b)?.groupValues?.get(1) ?: continue
            val body = raw.replace(Regex("""<br\s*/?>"""), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (body.isNotBlank()) res.add(from to body)
        }
        return res
    }
}
