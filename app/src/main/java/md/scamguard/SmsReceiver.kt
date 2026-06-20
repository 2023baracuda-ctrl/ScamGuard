package md.scamguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val sender = msgs.firstOrNull()?.originatingAddress.orEmpty()
        if (body.isBlank()) return

        // === 1) STOPGUARD: отвязка доверенного контакта ===
        if (isStopGuardCommand(body)) {
            CoroutineScope(Dispatchers.IO).launch { handleStopGuard(ctx, sender) }
            return
        }

        // === 2) Обычный анализ ===
        val a = Detector.analyze(body)
        val threat = Detector.threatLevel(a)
        if (threat == Threat.NONE) return

        val now = System.currentTimeMillis()
        val ev = History.Event(
            time = now,
            level = threat.name,
            sender = sender.ifBlank { "?" },
            callNumber = if (CallContext.activeOrRecent(Detector.RECENT_CALL_WINDOW_MS))
                CallContext.lastNumber else "",
            reason = a.reason,
            smsBody = body,
        )
        CoroutineScope(Dispatchers.IO).launch { History.add(ctx, ev) }

        // SMS родственникам — только при HIGH
        if (threat == Threat.HIGH) {
            CoroutineScope(Dispatchers.IO).launch {
                Prefs.nums(ctx).forEach { num ->
                    val suffix = if (ev.callNumber.isNotBlank()) " (${ev.callNumber})" else ""
                    val txt = ctx.getString(R.string.sms_alert_to_trusted, suffix)
                    SmsSender.send(ctx, num, txt) { _, _ -> }
                }
            }
        }

        AlertActivity.show(ctx, threat, ev)
    }

    private fun isStopGuardCommand(body: String): Boolean {
        val norm = body.uppercase().replace("\\s+".toRegex(), " ").trim()
        return norm.contains("STOPGUARD") || norm.contains("STOP GUARD")
    }

    private suspend fun handleStopGuard(ctx: Context, sender: String) {
        if (sender.isBlank()) return
        val nums = Prefs.nums(ctx)
        val match = nums.firstOrNull { normalize(it) == normalize(sender) }
        if (match != null) {
            Prefs.removeNum(ctx, match)
            SmsSender.send(ctx, sender, ctx.getString(R.string.sms_unsub_ok)) { _, _ -> }
        } else {
            SmsSender.send(ctx, sender, ctx.getString(R.string.sms_unsub_notfound)) { _, _ -> }
        }
    }

    /** Сравниваем номера по последним 8 цифрам — нечувствительно к формату. */
    private fun normalize(n: String): String =
        n.filter { it.isDigit() }.takeLast(8)
}
