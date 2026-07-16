package md.scamguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

private const val EMAIL_FEEDBACK = "scamguardrm@gmail.com"
private const val TELEGRAM_URL = ""  // TODO: добавь свой канал когда создашь

@Composable
fun SettingsScreen(
    onProtectionChanged: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = LocaleHelper.readSavedLang(ctx)

    var protectionOn by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        protectionOn = withContext(Dispatchers.IO) { Prefs.protectionEnabled(ctx) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = Sg.H1,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        /* === Защита === */
        SectionCard(stringResource(R.string.settings_section_protection)) {
            Text(
                if (protectionOn) stringResource(R.string.settings_protection_running)
                else              stringResource(R.string.settings_protection_stopped),
                style = Sg.Body
            )
            Spacer(Modifier.height(8.dp))
            if (protectionOn) {
                OutlinedButton(onClick = {
                    CallWatchService.stop(ctx)
                    protectionOn = false
                    scope.launch {
                        withContext(Dispatchers.IO) { Prefs.setProtectionEnabled(ctx, false) }
                    }
                    onProtectionChanged()
                }) { Text(stringResource(R.string.settings_protection_btn_stop)) }
            } else {
                Button(onClick = {
                    runCatching { CallWatchService.start(ctx) }
                    protectionOn = true
                    scope.launch {
                        withContext(Dispatchers.IO) { Prefs.setProtectionEnabled(ctx, true) }
                    }
                    onProtectionChanged()
                }) { Text(stringResource(R.string.settings_protection_btn_start)) }
            }
        }

        /* === История === */
        SectionCard(stringResource(R.string.settings_section_history)) {
            Text(stringResource(R.string.settings_clear_history_desc), style = Sg.BodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showClearDialog = true }) {
                Text(stringResource(R.string.settings_clear_history))
            }
        }

        /* === Документы === */
        SectionCard(stringResource(R.string.settings_section_documents)) {
            LinkRow(stringResource(R.string.consent_link_privacy)) {
                openUrl(ctx, "https://scamguardrm.pages.dev/privacy-$lang")
            }
            LinkRow(stringResource(R.string.consent_link_eula)) {
                openUrl(ctx, "https://scamguardrm.pages.dev/eula-$lang")
            }
        }

        /* === Связь === */
        SectionCard(stringResource(R.string.settings_section_contact)) {
            LinkRow(stringResource(R.string.settings_email_label) + ": " + EMAIL_FEEDBACK) {
                openEmail(ctx, EMAIL_FEEDBACK,
                    "ScamGuard — обратная связь", "")
            }
            if (TELEGRAM_URL.isNotBlank()) {
                LinkRow(stringResource(R.string.settings_telegram)) { openUrl(ctx, TELEGRAM_URL) }
            }
            LinkRow(stringResource(R.string.settings_report_bug)) {
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val androidVer = android.os.Build.VERSION.RELEASE
                val body = "Опишите проблему:\n\n\n---\n" +
                    "Версия приложения: ${BuildConfig.VERSION_NAME}\n" +
                    "Android: $androidVer\n" +
                    "Устройство: $device"
                openEmail(ctx, EMAIL_FEEDBACK, "ScamGuard — сообщение об ошибке", body)
            }
        }

       /* === О приложении === */
        SectionCard(stringResource(R.string.settings_section_about)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_version), style = Sg.Body)
                Spacer(Modifier.width(8.dp))
                Text(BuildConfig.VERSION_NAME, style = Sg.Body, fontWeight = FontWeight.Bold)
            }
        }

        /* === DEBUG: тестовые кнопки алертов (только в debug-сборке) === */
        if (BuildConfig.DEBUG) {
            SectionCard("🛠 Debug (только в debug-сборке)") {
                Text("Быстрая проверка дизайна алертов без реальной SMS/звонка",
                    style = Sg.Caption)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val ev = History.Event(
                            time = System.currentTimeMillis(),
                            level = Threat.HIGH.name,
                            sender = "MAIB",
                            callNumber = "+373 6X XXX XXX",
                            reason = ReasonCategory.OTP_GENERIC.ru,
                            smsBody = "",
                            bankName = "Банк MAIB",
                            reasonCategory = ReasonCategory.OTP_GENERIC.name,
                            bankCategory = "bank"
                        )
                        AlertActivity.show(ctx, Threat.HIGH, ev, "1313")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Показать КРАСНЫЙ алерт") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val bank = BankMatch(
                            id = "maib", displayName = "Банк MAIB", displayNameRo = "Banca MAIB",
                            category = "bank", matchedBy = "test", phone = "1313"
                        )
                        RingOverlayService.show(ctx, 3, ReasonCategory.OTP_GENERIC, bank)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCA8A04)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Показать ЖЁЛТЫЙ баннер") }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    /* === Диалог подтверждения очистки истории === */
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_history_confirm_title)) },
            text  = { Text(stringResource(R.string.settings_clear_history_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { History.clearAll(ctx) }
                        snackbar = ctx.getString(R.string.settings_history_cleared)
                    }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    snackbar?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2200)
            snackbar = null
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Surface(color = Sg.SuccessBg, shape = RoundedCornerShape(8.dp)) {
                Text(msg, color = Sg.SuccessTx, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = Sg.Surface,
        shape = RoundedCornerShape(Sg.CardRadius),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(title, style = Sg.H3, color = Sg.Muted)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// Простая нажимная строка для разделов "Документы", "Связь"
@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Sg.Purple),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = Sg.Body, modifier = Modifier.fillMaxWidth())
    }
}

private fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openEmail(ctx: Context, to: String, subject: String, body: String) {
    val uri = Uri.parse("mailto:$to" +
        "?subject=" + Uri.encode(subject) +
        "&body=" + Uri.encode(body))
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
