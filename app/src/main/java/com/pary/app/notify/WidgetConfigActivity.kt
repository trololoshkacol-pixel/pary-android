package com.pary.app.notify

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Настройка каждого экземпляра отдельно: тип данных, собственный заголовок и тема.
 * Конфигурация хранится по appWidgetId, поэтому виджеты не перезаписывают настройки друг друга.
 */
class WidgetConfigActivity : Activity() {
    private val modes = listOf(
        "Ближайшая пара" to "next",
        "Расписание на сегодня" to "today",
        "Задачи и дедлайны" to "tasks",
        "Расписание на несколько дней" to "week",
        "Оценки и посещаемость" to "stats",
        "Последняя заметка" to "notes",
        "Задания из чатов" to "drafts",
        "Свой текст" to "custom",
    )
    private val themes = listOf("Графит" to "dark", "Чёрный AMOLED" to "amoled", "Светлый" to "light")
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("pary_widgets", MODE_PRIVATE)
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@WidgetConfigActivity,
                android.R.layout.simple_spinner_dropdown_item,
                modes.map { it.first },
            )
            setSelection(modes.indexOfFirst { it.second == prefs.getString("mode_$widgetId", "next") }.coerceAtLeast(0))
        }
        val titleInput = EditText(this).apply {
            hint = "Свой заголовок (необязательно)"
            setSingleLine(true)
            setText(prefs.getString("title_$widgetId", ""))
        }
        val bodyInput = EditText(this).apply {
            hint = "Текст виджета: заметка, напоминание или любой текст"
            minLines = 3
            maxLines = 6
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(prefs.getString("body_$widgetId", ""))
        }
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@WidgetConfigActivity,
                android.R.layout.simple_spinner_dropdown_item,
                themes.map { it.first },
            )
            setSelection(themes.indexOfFirst { it.second == prefs.getString("theme_$widgetId", "dark") }.coerceAtLeast(0))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "Что показывать"
                textSize = 14f
            })
            addView(modeSpinner, matchWrap())
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "Название виджета"
                textSize = 14f
                setPadding(0, dp(14), 0, 0)
            })
            addView(titleInput, matchWrap())
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "Оформление"
                textSize = 14f
                setPadding(0, dp(14), 0, 0)
            })
            addView(themeSpinner, matchWrap())
            addView(TextView(this@WidgetConfigActivity).apply {
                text = "Свой текст (для режима «Свой текст»)"
                textSize = 14f
                setPadding(0, dp(14), 0, 0)
            })
            addView(bodyInput, matchWrap())
            addView(Button(this@WidgetConfigActivity).apply {
                text = "Сохранить виджет"
                setOnClickListener {
                    val mode = modes.getOrNull(modeSpinner.selectedItemPosition)?.second ?: "next"
                    val theme = themes.getOrNull(themeSpinner.selectedItemPosition)?.second ?: "dark"
                    prefs.edit()
                        .putString("mode_$widgetId", mode)
                        .putString("title_$widgetId", titleInput.text.toString().trim().take(36))
                        .putString("theme_$widgetId", theme)
                        .putString("body_$widgetId", bodyInput.text.toString().trim().take(1200))
                        .apply()
                    val update = Intent(this@WidgetConfigActivity, WidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                    }
                    sendBroadcast(update)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }
            }, matchWrap())
        }
        setContentView(root)
        title = "Настроить виджет Pary"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
