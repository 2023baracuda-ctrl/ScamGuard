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

        val analysis = Detector.analyze(body)
        val threat = Detector.threatLevel(analysis)

        if (threat != Threat.NONE) {
            // Сохраняем кратко — никаких полных текстов SMS и кодов
            val safe = analysis.reason
            CoroutineScope(Dispatchers.IO).launch { History.add(ctx, safe) }

            AlertActivity.show(ctx,
                level = threat,
                sender = sender.ifBlank { "неизвестный" },
                duringCall = CallContext.activeOrRecent(60_000),
                callNumber = "",
                reason = analysis.reason
            )
        }
    }
}
