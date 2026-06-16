package md.scamguard

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, LocaleHelper.readSavedLang(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })

        val time = intent.getLongExtra(EX_TIME, 0L)
        val level = intent.getStringExtra(EX_LEVEL) ?: "HIGH"
        val sender = intent.getStringExtra(EX_SENDER) ?: ""
        val callNumber = intent.getStringExtra(EX_CALL) ?: ""
        val reason = intent.getStringExtra(EX_REASON) ?: ""
        val body = intent.getStringExtra(EX_BODY) ?: ""

        vibrateAttention(level == "HIGH")

        setContent {
            SgTheme {
                AlertUi(level, sender, callNumber, reason, body,
                    onClose = { dismiss(time); finishAndRemoveTask() },
                    onFalse = {
                        Reporter.report(applicationContext, body, level, reason)
                        CoroutineScope(Dispatchers.IO).launch {
                            History.delete(applicationContext, time)
                        }
                        finishAndRemoveTask()
                    })
            }
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
        } else { @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator }
        val pattern = if (high) longArrayOf(0, 600, 200, 600, 200, 600)
                      else      longArrayOf(0, 300, 200, 300)
        if (Build.VERSION.SDK_INT >= 26) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            v.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs)
        } else { @Suppress("DEPRECATION") v.vibrate(pattern, -1) }
    }
    companion object {
        const val EX_TIME="t"; const val EX_LEVEL="lvl"; const val EX_SENDER="snd"
        const val EX_CALL="call"; const val EX_REASON="rsn"; const val EX_BODY="bd"

        fun show(ctx: Context, level: Threat, ev: History.Event) {
            val i = Intent(ctx, AlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EX_TIME, ev.time); putExtra(EX_LEVEL, level.name)
                putExtra(EX_SENDER, ev.sender); putExtra(EX_CALL, ev.callNumber)
                putExtra(EX_REASON, ev.reason); putExtra(EX_BODY, ev.smsBody)
            }
            ctx.startActivity(i)
        }
    }
}

@Composable
private fun AlertUi(level: String, sender: String, callNumber: String, reason: String,
                    body: String, onClose: () -> Unit, onFalse: () -> Unit) {
    val high = level == "HIGH"
    val bg = if (high) Sg.AlertHighBg else Sg.AlertLowBg

    Box(Modifier.fillMaxSize().background(Sg.ScreenScrim),
        contentAlignment = Alignment.Center) {
        Surface(color = bg, shape = RoundedCornerShape(Sg.BigRadius),
            modifier = Modifier.fillMaxWidth(0.93f).fillMaxHeight(0.72f)) {
            Column(Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {

                Text("⚠️", fontSize = 56.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (high) R.string.alert_high_title else R.string.alert_low_title),
                    color = Color.White, fontSize = 21.sp,
                    style = Sg.H1.copy(color = Color.White))
                Spacer(Modifier.height(12.dp))

                Text(if (high) stringResource(R.string.alert_high_body)
                     else stringResource(R.string.alert_low_body, sender),
                    color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.alert_main_warning),
                    color = Color.White, fontSize = 14.sp,
                    style = Sg.H3.copy(color = Color.White))

                if (reason.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.alert_reason, reason),
                        color = Color(0xFFFFE4E6), fontSize = 12.sp)
                }
                if (callNumber.isNotBlank())
                    Text(stringResource(R.string.alert_call_from, callNumber),
                        color = Color(0xFFFFE4E6), fontSize = 12.sp)
                if (sender.isNotBlank())
                    Text(stringResource(R.string.alert_sms_from, sender),
                        color = Color(0xFFFFE4E6), fontSize = 12.sp)
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
                ) { Text(stringResource(R.string.alert_close), fontSize = 17.sp) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFalse, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.alert_false),
                        color = Color(0xFFFFE4E6), fontSize = 13.sp)
                }
            }
        }
    }
}
