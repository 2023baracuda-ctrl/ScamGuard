package md.scamguard

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Безопасная отправка SMS с обратной связью.
 * onResult(true, null) — отправлено.
 * onResult(false, "причина") — не отправлено.
 */
object SmsSender {

    fun send(ctx: Context, phone: String, text: String,
             onResult: (Boolean, String?) -> Unit) {
        try {
            // Актуальный способ получения SmsManager
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Если текст длинный — разбиваем
            val parts = sm.divideMessage(text)

            // PendingIntent для получения статуса отправки
            val SENT_ACTION = "md.scamguard.SMS_SENT_${System.currentTimeMillis()}"
            val sentIntent = PendingIntent.getBroadcast(
                ctx, 0, Intent(SENT_ACTION).setPackage(ctx.packageName),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    try { c.unregisterReceiver(this) } catch (_: Throwable) {}
                    when (resultCode) {
                        Activity.RESULT_OK -> onResult(true, null)
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> onResult(false, "общая ошибка отправки")
                        SmsManager.RESULT_ERROR_NO_SERVICE -> onResult(false, "нет сети")
                        SmsManager.RESULT_ERROR_NULL_PDU -> onResult(false, "пустое сообщение")
                        SmsManager.RESULT_ERROR_RADIO_OFF -> onResult(false, "режим полёта или радио выключено")
                        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> onResult(false, "превышен лимит отправки")
                        SmsManager.RESULT_ERROR_NO_DEFAULT_SMS_APP -> onResult(false, "не выбрано стандартное приложение SMS")
                        else -> onResult(false, "код ошибки $resultCode")
                    }
                }
            }
            val filter = IntentFilter(SENT_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ContextCompat.registerReceiver(ctx, receiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED)
            }

            if (parts.size == 1) {
                sm.sendTextMessage(phone, null, text, sentIntent, null)
            } else {
                val sentList = ArrayList<PendingIntent>(parts.size).apply {
                    repeat(parts.size) { add(sentIntent) }
                }
                sm.sendMultipartTextMessage(phone, null, parts, sentList, null)
            }
        } catch (t: Throwable) {
            onResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
