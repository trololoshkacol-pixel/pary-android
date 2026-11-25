package com.pary.app.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pary.app.data.ChatDraft
import com.pary.app.data.ChatParser
import com.pary.app.data.Store

/**
 * Читает уведомления выбранных мессенджеров и предлагает задачи.
 * Всё остаётся на телефоне: текст не уходит в сеть.
 */
class ChatListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        runCatching { sbn?.let { handle(it) } }
    }

    private fun handle(s: StatusBarNotification) {
        Store.init(applicationContext)
        if (!Store.watchChats) return
        if (s.packageName !in Store.watchedPkgs) return
        val n = s.notification ?: return
        val extras = n.extras ?: return
        val short = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = (if (big.length > short.length) big else short).replace("\n", " ").trim()
        if (body.length < 4) return
        if (!Store.isChatHomework(body)) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            .ifBlank {
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()
            }
        val chat = title.ifBlank { appName(s.packageName) }
        val draft = ChatDraft(
            text = body.take(200),
            chat = chat,
            pkg = s.packageName,
            due = ChatParser.dueFrom(body),
        )
        if (Store.addChatDraft(draft)) {
            Sync.refresh(applicationContext)
            Sync.notifyDraft(applicationContext, draft)
        }
    }

    private fun appName(pkg: String): String = when (pkg) {
        "com.whatsapp" -> "WhatsApp"
        "org.telegram.messenger" -> "Telegram"
        "com.viber.voip" -> "Viber"
        "com.vkontakte.android" -> "ВКонтакте"
        "com.discord" -> "Discord"
        else -> "Чат"
    }
}
