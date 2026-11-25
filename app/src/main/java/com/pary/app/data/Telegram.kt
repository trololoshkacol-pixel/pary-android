package com.pary.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Простой клиент Telegram Bot API: забирает сообщения из групп, где добавлен бот. */
object TelegramApi {

    data class Update(val id: Long, val chatId: Long, val chatTitle: String, val text: String)
    data class Res<T>(val value: T? = null, val error: String = "")

    private fun call(token: String, method: String, query: String = ""): String? {
        if (token.isBlank()) return null
        val url = "https://api.telegram.org/bot$token/$method" + if (query.isBlank()) "" else "?$query"
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = "GET"
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            if (code in 200..299) text else null
        } catch (e: Exception) {
            null
        }
    }

    fun getMe(token: String): Res<String> {
        val t = call(token, "getMe") ?: return Res(error = "Telegram не ответил. Проверь токен и интернет")
        val ok = runCatching { JSONObject(t).optBoolean("ok", false) }.getOrDefault(false)
        if (!ok) return Res(error = "Токен не принят Telegram")
        val name = runCatching {
            JSONObject(t).optJSONObject("result")?.optString("username").orEmpty()
        }.getOrDefault("")
        return Res(value = name)
    }

    fun getUpdates(token: String, offset: Long): Res<Pair<List<Update>, Long>> {
        val q = "timeout=0&allowed_updates=%5B%22message%22%5D" + if (offset > 0) "&offset=$offset" else ""
        val t = call(token, "getUpdates", q)
            ?: return Res(error = "Не удалось получить сообщения")
        val root = runCatching { JSONObject(t) }.getOrNull()
            ?: return Res(error = "Неожиданный ответ Telegram")
        if (!root.optBoolean("ok", false)) {
            val desc = root.optString("description", "ошибка")
            return Res(error = "Telegram: $desc")
        }
        val arr: JSONArray = root.optJSONArray("result") ?: JSONArray()
        val out = mutableListOf<Update>()
        var nextOffset = offset
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val uid = u.optLong("update_id", 0)
            if (uid > nextOffset) nextOffset = uid + 1
            val msg = u.optJSONObject("message") ?: continue
            val text = msg.optString("text").ifBlank { msg.optString("caption") }
            if (text.isBlank()) continue
            val chat = msg.optJSONObject("chat") ?: continue
            val title = chat.optString("title").ifBlank {
                listOf(chat.optString("first_name"), chat.optString("last_name"))
                    .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Telegram" }
            }
            out.add(Update(uid, chat.optLong("id"), title, text))
        }
        return Res(value = out to nextOffset)
    }
}
