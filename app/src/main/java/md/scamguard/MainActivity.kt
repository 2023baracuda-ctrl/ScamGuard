package md.scamguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { App() } }
    }
}

private data class PermState(val sms: Boolean, val phone: Boolean,
                             val notif: Boolean, val sendSms: Boolean,
                             val overlay: Boolean) {
    val allCritical = sms && phone && notif && sendSms
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(check(ctx)) }
    var lang by remember { mutableStateOf("ru") }
    var history by remember { mutableStateOf<List<History.Event>>(emptyList()) }
    var nums by remember { mutableStateOf<Set<String>>(emptySet()) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var screen by remember { mutableStateOf("home") }  // home | numbers | settings

    fun L(ru: String, ro: String) = if (lang == "ru") ru else ro

    LaunchedEffect(Unit) {
        lang = Prefs.lang(ctx)
        nums = Prefs.nums(ctx)
        history = History.list(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        update = Updater.check(BuildConfig.VERSION_NAME)
    }

    val refresh: () -> Unit = {
        state = check(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        scope.launch {
            history = History.list(ctx)
            nums = Prefs.nums(ctx)
        }
    }

    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val anyResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { refresh() }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7FB)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛡️ ScamGuard", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f))
                // Переключатель языка
                AssistChip(onClick = {
                    val nv = if (lang == "ru") "ro" else "ru"
                    lang = nv; scope.launch { Prefs.setLang(ctx, nv) }
                }, label = { Text(if (lang == "ru") "RU" else "RO") })
            }
            Text(L("Защита от телефонных мошенников",
                   "Protecție împotriva escrocilor telefonici"),
                 color = Color(0xFF6B7280))
            Spacer(Modifier.height(14.dp))

            // ---- Обновление ----
            update?.takeIf { it.available }?.let { u ->
                Card(colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE0E7FF))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(L("Доступна новая версия", "Versiune nouă disponibilă") +
                             " ${u.latestVersion}",
                             fontWeight = FontWeight.Bold)
                        if (u.notes.isNotBlank())
                            Text(u.notes, fontSize = 13.sp, color = Color(0xFF374151))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { Updater.openInBrowser(ctx, u.downloadUrl) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5))) {
                            Text(L("Скачать", "Descarcă"))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Разрешения / готовность ----
            if (!state.allCritical) {
                PermCard(L("Один раз настроим защиту", "Configurăm protecția"),
                         L("Нажмите — система спросит разрешения, нажимайте «Разрешить».",
                           "Apăsați — sistemul va cere permisiuni, alegeți «Permite».")) {
                    val need = mutableListOf<String>()
                    if (!state.sms) need += Manifest.permission.RECEIVE_SMS
                    if (!state.phone) need += Manifest.permission.READ_PHONE_STATE
                    if (!state.sendSms) need += Manifest.permission.SEND_SMS
                    if (!state.notif && Build.VERSION.SDK_INT >= 33)
                        need += Manifest.permission.POST_NOTIFICATIONS
                    if (need.isNotEmpty()) multi.launch(need.toTypedArray())
                }
                Spacer(Modifier.height(12.dp))
            }
            if (state.allCritical && !state.overlay) {
                PermCard(L("Показ поверх других окон",
                           "Afișare peste alte ferestre"),
                         L("Нужно чтобы красное предупреждение всплывало над звонком",
                           "Necesar pentru afișarea avertismentului peste apel")) {
                    anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (state.allCritical && state.overlay) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("✓ " + L("Защита активна", "Protecția este activă"),
                            color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                        Text(L("Приложение работает в фоне.",
                               "Aplicația rulează în fundal."),
                            fontSize = 12.sp, color = Color(0xFF065F46))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Навигация ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                TabBtn(screen == "home", L("История", "Istoric")) { screen = "home"; refresh() }
                TabBtn(screen == "numbers", L("Контакты", "Contacte")) { screen = "numbers" }
            }
            Spacer(Modifier.height(12.dp))

            when (screen) {
                "home" -> HistoryScreen(ctx, history, lang, onRefresh = refresh)
                "numbers" -> NumbersScreen(ctx, nums, lang, onChange = refresh)
            }
        }
    }
}

@Composable
private fun PermCard(title: String, desc: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, color = Color(0xFF92400E), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))) {
                Text("OK")
            }
        }
    }
}

@Composable
private fun TabBtn(active: Boolean, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick,
        colors = if (active) ButtonDefaults.buttonColors(
            containerColor = Color(0xFF7C3AED), contentColor = Color.White)
                 else ButtonDefaults.outlinedButtonColors()) { Text(label) }
}

@Composable
private fun HistoryScreen(ctx: Context, items: List<History.Event>,
                          lang: String, onRefresh: () -> Unit) {
    fun L(ru: String, ro: String) = if (lang == "ru") ru else ro
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(L("История предупреждений", "Istoric avertismente"),
             fontSize = 18.sp, fontWeight = FontWeight.Bold,
             modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "refresh")
        }
    }
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) {
        Text(L("Пока пусто. Подозрительных событий не было.",
               "Gol. Nu au fost evenimente suspecte."),
            color = Color(0xFF6B7280), fontSize = 13.sp)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.time }) { e ->
                EventRow(e, lang)
            }
        }
    }
}

@Composable
private fun EventRow(e: History.Event, lang: String) {
    var open by remember { mutableStateOf(false) }
    val color = if (e.level == "HIGH") Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
    Card(colors = CardDefaults.cardColors(containerColor = color),
        onClick = { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(SimpleDateFormat("dd MMM, HH:mm",
                    Locale(if (lang == "ru") "ru" else "ro")).format(Date(e.time)),
                    fontSize = 12.sp, color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f))
                AssistChip(onClick = {},
                    label = { Text(if (e.level == "HIGH") "HIGH" else "LOW",
                        fontSize = 11.sp) })
            }
            if (e.callNumber.isNotBlank())
                Text("📞 ${e.callNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (e.sender.isNotBlank())
                Text("✉️ ${e.sender}", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(if (open) e.smsBody else e.smsBody.take(80) +
                if (e.smsBody.length > 80) "…" else "",
                fontSize = 13.sp)
            if (open && e.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("ℹ️ ${e.reason}", fontSize = 12.sp, color = Color(0xFF374151))
            }
        }
    }
}

@Composable
private fun NumbersScreen(ctx: Context, nums: Set<String>, lang: String,
                          onChange: () -> Unit) {
    fun L(ru: String, ro: String) = if (lang == "ru") ru else ro
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingNum by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Text(L("Кому отправлять предупреждения", "Cui să trimitem alerte"),
        fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(Modifier.height(4.dp))
    Text(L("Максимум 2 номера. При угрозе на них автоматически уйдёт SMS.",
           "Maximum 2 numere. La amenințare se trimite SMS automat."),
        fontSize = 12.sp, color = Color(0xFF6B7280))
    Spacer(Modifier.height(10.dp))

    nums.forEach { n ->
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(n, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch { Prefs.removeNum(ctx, n); onChange() }
            }) { Text(L("Удалить", "Șterge"), color = Color.Red) }
        }
    }

    if (nums.size < 2 && pendingCode == null) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it },
            label = { Text(L("Номер (+373…)", "Număr (+373…)")) },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            err = ""
            if (phone.length < 6) { err = L("Введите номер", "Introduceți numărul"); return@Button }
            val code = (1000..9999).random().toString()
            runCatching {
                android.telephony.SmsManager.getDefault().sendTextMessage(phone, null,
                    if (lang == "ru") "ScamGuard: код подтверждения $code. Сообщите его владельцу телефона."
                    else "ScamGuard: cod de confirmare $code. Comunicați-l proprietarului telefonului.",
                    null, null)
            }.onFailure { err = L("Не удалось отправить SMS", "Nu s-a putut trimite SMS") }
            if (err.isBlank()) { pendingCode = code; pendingNum = phone }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(L("Отправить код для проверки", "Trimite cod de verificare"))
        }
    }

    if (pendingCode != null) {
        Spacer(Modifier.height(10.dp))
        Text(L("Введите код, который пришёл на номер $pendingNum",
               "Introduceți codul primit pe $pendingNum"),
            fontSize = 13.sp)
        OutlinedTextField(value = typed, onValueChange = { typed = it.filter { c -> c.isDigit() } },
            label = { Text(L("Код", "Cod")) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (typed == pendingCode) {
                    scope.launch {
                        Prefs.addNum(ctx, pendingNum)
                        pendingCode = null; pendingNum = ""; typed = ""; phone = ""
                        onChange()
                    }
                } else err = L("Неверный код", "Cod incorect")
            }, modifier = Modifier.weight(1f)) { Text(L("Подтвердить", "Confirmă")) }
            OutlinedButton(onClick = {
                pendingCode = null; pendingNum = ""; typed = ""; err = ""
            }) { Text(L("Отмена", "Anulează")) }
        }
    }

    if (err.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(err, color = Color.Red, fontSize = 12.sp)
    }
}

private fun check(ctx: Context): PermState = PermState(
    sms = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS)
          == PackageManager.PERMISSION_GRANTED,
    phone = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
          == PackageManager.PERMISSION_GRANTED,
    notif = Build.VERSION.SDK_INT < 33 ||
          ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
          == PackageManager.PERMISSION_GRANTED,
    sendSms = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
          == PackageManager.PERMISSION_GRANTED,
    overlay = Settings.canDrawOverlays(ctx),
)
