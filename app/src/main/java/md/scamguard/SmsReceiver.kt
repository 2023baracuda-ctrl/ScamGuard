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

        val a = Detector.analyze(ctx, body, sender)
        if (!a.hasOtp) return

        if (CallContext.callActive()) {
            // Код пришёл прямо во время разговора — предупреждаем немедленно (красный).
            val ev = History.Event(
                time = System.currentTimeMillis(),
                level = Threat.HIGH.name,
                sender = sender.ifBlank { "?" },
                callNumber = CallContext.lastNumber,
                reason = a.reason,
                smsBody = body,
                bankName = a.claimedBank?.displayName ?: "",
                reasonCategory = a.reasonCategory.name,
                bankCategory = a.claimedBank?.category ?: ""
            )
            CoroutineScope(Dispatchers.IO).launch { History.add(ctx, ev) }
            AlertActivity.show(ctx, Threat.HIGH, ev, a.claimedBank?.phone ?: "")
        } else {
            // Звонка сейчас нет — запоминаем сигнал. Предупреждение покажется
            // в момент входящего звонка в течение ближайших 5 минут (CallWatchService).
            PendingSignal.record(a.reasonCategory, a.claimedBank, body, sender)
        }
    }
}
