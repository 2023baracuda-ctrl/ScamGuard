package md.scamguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, LocaleHelper.readSavedLang(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* блокируем Back */ }
        })

        val time = intent.getLongExtra(EX_TIME, 0L)
        val level = intent.getStringExtra(EX_LEVEL) ?: "HIGH"
        val sender = intent.getStringExtra(EX_SENDER) ?: ""
        val callNumber = intent.getStringExtra(EX_CALL) ?: ""
        val reason = intent.getStringExtra(EX_REASON) ?: ""
        val body = intent.getStringExtra(EX_BODY) ?: ""
        val bankName = intent.getStringExtra(EX_BANK) ?: ""
        val reasonCategory = intent.getStringExtra(EX_CATEGORY) ?: "OTHER"
        val bankCategory = intent.getStringExtra(EX_BANK_CATEGORY) ?: ""

        startContinuousVibration()

        setContent {
            SgTheme {
                AlertUi(
                    level = level,
                    sender = sender,
                    callNumber = callNumber,
                    bankName = bankName,
                    reasonCategory = reasonCategory,
                    body = body,
                    onHangUp = {
                        endActiveCall()
                        stopVibrationAndFinish(time)
                    },
                    onClose = {
                        stopVibrationAndFinish(time)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        stopVibration()
        super.onDestroy()
    }

    private fun stopVibrationAndFinish(time: Long) {
        stopVibration()
        if (time > 0) CoroutineScope(Dispatchers.IO).launch {
            History.markDismissed(applicationContext, time)
        }
        finishAndRemoveTask()
    }

    /**
     * Запускает повторяющуюся вибрацию паттерном "брр... брр... брр"
     * (400мс вибрация → 800мс пауза → повтор) до тех пор пока
     * пользователь не отреагирует.
     *
     * USAGE_ALARM выбран намеренно: вибрация громче чем у обычных
     * уведомлений и не подавляется режимом «Не беспокоить» — это
     * критичное предупреждение, оно должно дойти до пользователя.
     */
    private fun startContinuousVibration() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else { @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator }

        if (!v.hasVibrator()) return  // устройство без вибромотора
        vibrator = v

        val pattern = longArrayOf(0, 400, 800)  // [off, on=400ms, off=800ms] — loop с index 0

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Android 13+: VibrationAttributes (новый рекомендованный API)
                val attrs = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                    .build()
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else if (Build.VERSION.SDK_INT >= 26) {
                // Android 8-12: AudioAttributes с USAGE_ALARM
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else {
                @Suppress("DEPRECATION") v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
            // Если что-то пошло не так с новым API — пробуем самый простой
            try { @Suppress("DEPRECATION") v.vibrate(pattern, 0) } catch (_: Exception) {}
        }
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    /**
     * Программно завершает текущий звонок через TelecomManager.endCall().
     * Требует runtime-разрешение ANSWER_PHONE_CALLS (API 28+).
     * Если разрешения нет — тихо ничего не делаем, юзер кладёт трубку сам.
     */
    @SuppressLint("MissingPermission")
    private fun endActiveCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        } catch (_: SecurityException) { /* нет роли — игнорим */ }
        catch (_: Exception) { /* любая другая ошибка — пользователь сам */ }
    }

    companion object {
        const val EX_TIME = "t"
        const val EX_LEVEL = "lvl"
        const val EX_SENDER = "snd"
        const val EX_CALL = "call"
        const val EX_REASON = "rsn"
        const val EX_BODY = "bd"
        const val EX_BANK = "bnk"
        const val EX_CATEGORY = "cat"
        const val EX_BANK_CATEGORY = "bcat"

        fun show(ctx: Context, level: Threat, ev: History.Event) {
            val i = Intent(ctx, AlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EX_TIME, ev.time)
                putExtra(EX_LEVEL, level.name)
                putExtra(EX_SENDER, ev.sender)
                putExtra(EX_CALL, ev.callNumber)
                putExtra(EX_REASON, ev.reason)
                putExtra(EX_BODY, ev.smsBody)
                putExtra(EX_BANK, ev.bankName)
                putExtra(EX_CATEGORY, ev.reasonCategory)
                putExtra(EX_BANK_CATEGORY, ev.bankCategory)
            }
            ctx.startActivity(i)
        }
    }
}

@Composable
private fun AlertUi(
    level: String,
    sender: String,
    callNumber: String,
    bankName: String,
    reasonCategory: String,
    bankCategory: String,
    body: String,
    onHangUp: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val high = level == "HIGH"
    val bg = if (high) Color(0xFFB91C1C) else Color(0xFFCA8A04)  // красный или жёлтый
    val accentBtnBg = Color(0xFF1D4ED8)                          // ярко-красная кнопка

    // Определяем что показать в "Контакт:"
    // Если БД нашла организацию по sender'у — показываем оба:
    // например "Банк MAIB (от: MAIB)" — юзер видит и бренд, и сырого отправителя
    val contactDisplay = when {
        bankName.isNotBlank() && sender.isNotBlank() && sender != bankName ->
            "$bankName (от: $sender)"
        bankName.isNotBlank() -> bankName
        sender.isNotBlank() -> sender
        else -> "—"
    }
    val activityDisplay = remember(bankCategory) { BankCategoryLabels.get(ctx, bankCategory) }

    // Текст причины по локали
    val reasonDisplay = remember(reasonCategory) {
        runCatching { ReasonCategory.valueOf(reasonCategory) }
            .getOrDefault(ReasonCategory.OTHER).let { cat ->
                val lang = LocaleHelper.readSavedLang(ctx)
                if (lang == "ro") cat.ro else cat.ru
            }
    }

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f)
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ====== Заголовок ======
                Text(if (high) "🚨" else "⚠️", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alert_title_main),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // ====== ЖИРНЫЕ предупреждения ======
                Text(
                    stringResource(R.string.alert_warn_no_code),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.alert_warn_hangup),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.alert_warn_never_ask),
                    color = Color(0xFFFFE4E6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                // ====== Метаданные (СМС от, Текст СМС, Звонок с) ======
                Surface(
                    color = Color(0x33000000),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        MetaRow(stringResource(R.string.alert_meta_contact), contactDisplay)
                        if (activityDisplay.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            MetaRow(stringResource(R.string.alert_meta_activity), activityDisplay)
                        }
                        Spacer(Modifier.height(6.dp))
                        MetaRow(stringResource(R.string.alert_meta_reason), reasonDisplay)
                        if (callNumber.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            MetaRow(stringResource(R.string.alert_meta_call_from), callNumber)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ====== Большая красная кнопка "Положить трубку" ======
                Button(
                    onClick = onHangUp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentBtnBg,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(62.dp)
                ) {
                    Text(
                        stringResource(R.string.alert_btn_hangup),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ====== Маленькая бледная кнопка "Закрыть" ======
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.alert_btn_close),
                        color = Color(0xFFFCD5CE),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            color = Color(0xFFFFE4E6),
            fontSize = 13.sp,
            modifier = Modifier.width(90.dp)
        )
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
