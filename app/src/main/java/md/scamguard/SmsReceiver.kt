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
        val ev = History.Event(now, threat.name, sender.ifBlank { "?" },
            if (CallContext.activeOrRecent(120_000)) CallContext.lastNumber else "",
            a.reason, body)
        CoroutineScope(Dispatchers.IO).launch { History.add(ctx, ev) }

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
}
