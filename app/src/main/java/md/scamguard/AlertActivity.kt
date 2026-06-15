package md.scamguard

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        // Блокируем кнопку «Назад»
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* ничего */ }
        })

        val time = intent.getLongExtra(EX_TIME, 0L)
        val level = intent.getStringExtra(EX_LEVEL) ?: "HIGH"
        val sender = intent.getStringExtra(EX_SENDER) ?: ""
        val callNumber = intent.getStringExtra(EX_CALL) ?: ""
        val reason = intent.getStringExtra(EX_REASON) ?: ""
        val body = intent.getStringExtra(EX_BODY) ?: ""

        vibrateAttention(level == "HIGH")

        setContent {
            AlertUi(level, sender, callNumber, reason, body,
                onClose = { dismiss(time); finishAndRemoveTask() },
                onFalse = {
                    Reporter.report(applicationContext, body, level, reason)
                    CoroutineScope(Dispatchers.IO).launch { History.delete(applicationContext, time) }
                    finishAndRemoveTask()
                })
        }
    }

    private fun dismiss(time: Long) {
        if (time > 0) CoroutineScope(Dispatchers.IO).launch {
            History.markDismissed(applicationContext, time)
        }
    }

    private fun vibrateAttention(high: Boolean) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = if (high) longArrayOf(0, 600, 200, 600, 200, 600)
                      else      longArrayOf(0, 300, 200, 300)
        if (Build.VERSION.SDK_INT >= 26) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            v.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs)
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    companion object {
        const val EX_TIME = "t"; const val EX_LEVEL = "lvl"; const val EX_SENDER = "snd"
        const val EX_CALL = "call"; const val EX_REASON = "rsn"; const val EX_BODY = "bd"

        fun show(ctx: Context, level: Threat, ev: History.Event) {
            val i = Intent(ctx, AlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EX_TIME, ev.time)
                putExtra(EX_LEVEL, level.name)
                putExtra(EX_SENDER, ev.sender)
                putExtra(EX_CALL, ev.callNumber)
                putExtra(EX_REASON, ev.reason)
                putExtra(EX_BODY, ev.smsBody)
            }
            ctx.startActivity(i)
        }
    }
}

@Composable
private fun AlertUi(level: String, sender: String, callNumber: String,
                    reason: String, body: String,
                    onClose: () -> Unit, onFalse: () -> Unit) {
    val high = level == "HIGH"
    val bg = if (high) Color(0xFFB91C1C) else Color(0xFFB45309)

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center) {
        Surface(color = bg, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.93f).fillMaxHeight(0.72f)) {
            Column(Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {

                Text("⚠️", fontSize = 56.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (high) "ВНИМАНИЕ: ПОХОЖЕ НА МОШЕННИКОВ"
                     else      "Подозрительное сообщение",
                     color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))

                Text(if (high) "Вам пришёл одноразовый код во время звонка."
                     else "Сообщение от $sender вызывает сомнения.",
                     color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("НИКОМУ не диктуйте код. Банк, госуслуги и полиция " +
                     "никогда не запрашивают код по телефону.",
                     color = Color.White, fontSize = 14.sp,
                     fontWeight = FontWeight.SemiBold)

                if (reason.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Почему: $reason", color = Color(0xFFFFE4E6), fontSize = 12.sp)
                }
                if (callNumber.isNotBlank()) {
                    Text("Звонок с номера: $callNumber",
                         color = Color(0xFFFFE4E6), fontSize = 12.sp)
                }
                if (sender.isNotBlank()) {
                    Text("SMS от: $sender",
                         color = Color(0xFFFFE4E6), fontSize = 12.sp)
                }
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(10.dp)) {
                        Text(body, color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = bg),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ЗАКРЫТЬ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFalse,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Это ошибочное уведомление",
                        color = Color(0xFFFFE4E6), fontSize = 13.sp)
                }
            }
        }
    }
}
