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

        val a = Detector.analyze(body)
        val threat = Detector.threatLevel(a)
        if (threat == Threat.NONE) return

        val now = System.currentTimeMillis()
        val ev = History.Event(
            time = now, level = threat.name,
            sender = sender.ifBlank { "?" },
            callNumber = if (CallContext.activeOrRecent(120_000)) CallContext.lastNumber else "",
            reason = a.reason, smsBody = body
        )
        CoroutineScope(Dispatchers.IO).launch { History.add(ctx, ev) }

        if (threat == Threat.HIGH) {
            // авто-SMS доверенным номерам (если есть)
            CoroutineScope(Dispatchers.IO).launch {
                Prefs.nums(ctx).forEach { num ->
                    val txt = "ScamGuard: внимание! На номер владельца поступил " +
                              "звонок с признаками мошенничества" +
                              (if (ev.callNumber.isNotBlank()) " (${ev.callNumber})" else "") +
                              ". Свяжитесь с ним."
                    SmsSender.send(ctx, num, txt) { _, _ -> /* тихо */ }
                }
            }
        }

        AlertActivity.show(ctx, threat, ev)
    }
}
