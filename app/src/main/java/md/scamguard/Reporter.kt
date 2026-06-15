package md.scamguard

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Отправка отчётов об ошибочных срабатываниях в Google Forms.
 * Пользователь ничего не видит — POST на /formResponse молча.
 *
 * Чтобы это заработало — заполни константы ниже после создания формы.
 * См. гайд: «Настройка отчётов через Google Forms».
 */
object Reporter {

    // Подставь после создания формы (см. гайд):
    private const val FORM_ID = "REPLACE_WITH_FORM_ID"
    private const val ENTRY_BODY  = "entry.REPLACE_BODY"
    private const val ENTRY_LEVEL = "entry.REPLACE_LEVEL"
    private const val ENTRY_REASON = "entry.REPLACE_REASON"

    private fun enabled() = FORM_ID != "REPLACE_WITH_FORM_ID"

    fun report(ctx: Context, smsBody: String, level: String, reason: String) {
        if (!enabled()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = URL("https://docs.google.com/forms/d/e/$FORM_ID/formResponse")
                val data = listOf(
                    ENTRY_BODY to smsBody,
                    ENTRY_LEVEL to level,
                    ENTRY_REASON to reason
                ).joinToString("&") { (k, v) ->
                    "$k=${URLEncoder.encode(v, "UTF-8")}"
                }
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded")
                    outputStream.use { it.write(data.toByteArray()) }
                    inputStream.use { it.readBytes() }  // прочитать чтобы закрыть соединение
                    disconnect()
                }
            }
        }
    }
}
