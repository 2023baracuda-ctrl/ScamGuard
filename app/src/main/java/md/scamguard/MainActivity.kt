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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, LocaleHelper.readSavedLang(newBase)))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { SgTheme { App() } }
    }
}

private data class PermState(
    val sms: Boolean, val phone: Boolean, val notif: Boolean,
    val sendSms: Boolean, val overlay: Boolean
) {
    val allCritical = sms && phone && notif        // защита базово работает
    val canSendSms  = sendSms                      // может ли добавлять номера / отправлять
    val all         = allCritical && sendSms && overlay
}

private enum class Tab(val labelId: Int) {
    Home(R.string.nav_home),
    History(R.string.nav_history),
    Contacts(R.string.nav_contacts),
    Faq(R.string.nav_faq);
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(check(ctx)) }
    var lang by remember { mutableStateOf(LocaleHelper.readSavedLang(ctx)) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var history by remember { mutableStateOf<List<History.Event>>(emptyList()) }
    var nums by remember { mutableStateOf<Set<String>>(emptySet()) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    val refresh: () -> Unit = {
        state = check(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        scope.launch {
            history = History.list(ctx)
            nums = Prefs.nums(ctx)
        }
    }

    LaunchedEffect(Unit) {
        history = History.list(ctx); nums = Prefs.nums(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        update = Updater.check(BuildConfig.VERSION_NAME)
    }
    // регулярная пере-проверка разрешений (после возврата из настроек)
    LaunchedEffect(Unit) {
        while (true) {
            val fresh = check(ctx)
            if (fresh != state) {
                state = fresh
                if (state.allCritical) runCatching { CallWatchService.start(ctx) }
            }
            delay(900)
        }
    }

    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val anyResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = Sg.H1)
                        Text(stringResource(R.string.app_tagline), style = Sg.Caption)
                    }
                },
                actions = {
                    IconButton(onClick = refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "refresh")
                    }
                    AssistChip(onClick = {
                        val nv = if (lang == "ru") "ro" else "ru"
                        lang = nv
                        LocaleHelper.cache(ctx, nv)
                        scope.launch { Prefs.setLang(ctx, nv) }
                        // мгновенно перезапускаем активити чтобы применить локаль
                        (ctx as? ComponentActivity)?.recreate()
                    }, label = { Text(if (lang == "ru") "RU" else "RO") })
                    Spacer(Modifier.width(6.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Sg.Surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Sg.Surface) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; if (t == Tab.History || t == Tab.Contacts) refresh() },
                        icon = {
                            Icon(when (t) {
                                Tab.Home -> Icons.Filled.Home
                                Tab.History -> Icons.Filled.List
                                Tab.Contacts -> Icons.Filled.Person
                                Tab.Faq -> Icons.Outlined.Info
                            }, contentDescription = null)
                        },
                        label = { Text(stringResource(t.labelId), fontSize = 11.sp,
                                       maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Sg.Background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = Sg.PaddingScreen,
                                                  vertical   = 12.dp)) {
                update?.takeIf { it.available }?.let { u ->
                    UpdateBanner(u); Spacer(Modifier.height(Sg.GapM))
                }
                when (tab) {
                    Tab.Home -> HomeScreen(ctx, state,
                        onAskCritical = {
                            val need = mutableListOf<String>()
                            if (!state.sms) need += Manifest.permission.RECEIVE_SMS
                            if (!state.phone) need += Manifest.permission.READ_PHONE_STATE
                            if (!state.sendSms) need += Manifest.permission.SEND_SMS
                            if (!state.notif && Build.VERSION.SDK_INT >= 33)
                                need += Manifest.permission.POST_NOTIFICATIONS
                            if (need.isNotEmpty()) multi.launch(need.toTypedArray())
                        },
                        onOpenSms = {
                            anyResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${ctx.packageName}")))
                        },
                        onOpenOverlay = {
                            anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")))
                        },
                        onOpenFaq = { tab = Tab.Faq }
                    )
                    Tab.History  -> HistoryScreen(history)
                    Tab.Contacts -> ContactsScreen(ctx, state.canSendSms, nums, onChange = refresh,
                                                   onOpenFaq = { tab = Tab.Faq })
                    Tab.Faq      -> FaqScreen()
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(u: UpdateInfo) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Sg.InfoBg)) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(stringResource(R.string.update_title, u.latestVersion),
                style = Sg.H3)
            if (u.notes.isNotBlank())
                Text(u.notes, style = Sg.Caption, color = Sg.Ink)
            Spacer(Modifier.height(Sg.GapS))
            Button(onClick = { Updater.openInBrowser(ctx, u.downloadUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Sg.Purple)) {
                Text(stringResource(R.string.update_download))
            }
        }
    }
}

/* ==================== HOME ==================== */
@Composable
private fun HomeScreen(ctx: Context, state: PermState,
                       onAskCritical: () -> Unit, onOpenSms: () -> Unit,
                       onOpenOverlay: () -> Unit, onOpenFaq: () -> Unit) {
    when {
        !state.allCritical -> SetupCard(stringResource(R.string.onb_setup_title),
            stringResource(R.string.onb_setup_desc), onAskCritical)
        !state.overlay -> SetupCard(stringResource(R.string.onb_overlay_title),
            stringResource(R.string.onb_overlay_desc), onOpenOverlay)
        else -> StatusCard(state, onOpenSms, onOpenFaq)
    }
}

@Composable
private fun SetupCard(title: String, desc: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Sg.WarnBg)) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(title, style = Sg.H3)
            Text(desc, color = Sg.WarnTx, style = Sg.BodySmall)
            Spacer(Modifier.height(Sg.GapM))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Sg.Purple)) {
                Text(stringResource(R.string.action_ok), style = Sg.Button)
            }
        }
    }
}

@Composable
private fun StatusCard(state: PermState, onOpenSms: () -> Unit, onOpenFaq: () -> Unit) {
    val allGreen = state.all
    val bg = if (allGreen) Sg.SuccessBg else Sg.WarnBg
    Card(colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (allGreen) R.string.status_protected
                                else R.string.status_partial_title),
                color = if (allGreen) Sg.SuccessTx else Sg.WarnTx, style = Sg.H2)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.status_protected_desc),
                color = if (allGreen) Sg.SuccessTx else Sg.WarnTx, style = Sg.BodySmall)

            if (!state.canSendSms) {
                Spacer(Modifier.height(Sg.GapM))
                Text(stringResource(R.string.status_partial_send_sms),
                    color = Sg.WarnTx, style = Sg.BodySmall)
                Spacer(Modifier.height(Sg.GapS))
                Row {
                    OutlinedButton(onClick = onOpenSms) {
                        Text(stringResource(R.string.status_partial_fix))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onOpenFaq) {
                        Text(stringResource(R.string.status_partial_howto))
                    }
                }
            }
            if (!state.overlay) {
                Spacer(Modifier.height(Sg.GapM))
                Text(stringResource(R.string.status_partial_overlay),
                    color = Sg.WarnTx, style = Sg.BodySmall)
            }
        }
    }
}

/* ==================== HISTORY ==================== */
@Composable
private fun HistoryScreen(items: List<History.Event>) {
    Text(stringResource(R.string.history_title), style = Sg.H2)
    Spacer(Modifier.height(Sg.GapM))
    if (items.isEmpty()) {
        Text(stringResource(R.string.history_empty), style = Sg.Caption)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Sg.GapS),
            modifier = Modifier.fillMaxWidth()) {
            items(items, key = { it.time }) { EventRow(it) }
        }
    }
}

@Composable
private fun EventRow(e: History.Event) {
    var open by remember { mutableStateOf(false) }
    val color = if (e.level == "HIGH") Sg.DangerBg else Sg.WarnBg
    val locale = Locale(LocaleHelper.readSavedLang(LocalContext.current))
    Card(colors = CardDefaults.cardColors(containerColor = color),
        onClick = { open = !open }) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("dd MMM, HH:mm", locale).format(Date(e.time)),
                    style = Sg.Caption, modifier = Modifier.weight(1f))
                Text(e.level, fontSize = 10.sp,
                    color = if (e.level == "HIGH") Sg.DangerTx else Sg.WarnTx)
            }
            if (e.callNumber.isNotBlank())
                Text("📞 ${e.callNumber}", style = Sg.BodySmall)
            if (e.sender.isNotBlank())
                Text("✉️ ${e.sender}", style = Sg.BodySmall)
            Text(if (open) e.smsBody else e.smsBody.take(60) +
                if (e.smsBody.length > 60) "…" else "",
                style = Sg.BodySmall)
            if (open && e.reason.isNotBlank())
                Text("ℹ️ ${e.reason}", style = Sg.Caption)
        }
    }
}

/* ==================== CONTACTS ==================== */
@Composable
private fun ContactsScreen(ctx: Context, canSendSms: Boolean,
                           nums: Set<String>, onChange: () -> Unit, onOpenFaq: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingNum by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Text(stringResource(R.string.contacts_title), style = Sg.H2)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.contacts_subtitle), style = Sg.Caption)
    Spacer(Modifier.height(Sg.GapM))

    if (!canSendSms) {
        Card(colors = CardDefaults.cardColors(containerColor = Sg.WarnBg)) {
            Column(Modifier.padding(Sg.PaddingCard)) {
                Text(stringResource(R.string.contacts_warn_no_sms),
                    color = Sg.WarnTx, style = Sg.BodySmall)
                Spacer(Modifier.height(Sg.GapS))
                TextButton(onClick = onOpenFaq) {
                    Text(stringResource(R.string.status_partial_howto))
                }
            }
        }
        return
    }

    // существующие номера
    nums.forEach { n ->
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(n, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch { Prefs.removeNum(ctx, n); onChange() }
            }) { Text(stringResource(R.string.contacts_remove), color = Sg.DangerTx) }
        }
    }

    // отдельный лаунчер на SEND_SMS (для Android 15 / Restricted)
    val sendLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) err = ctx.getString(R.string.contacts_err_restricted)
    }

    if (nums.size < 2 && pendingCode == null) {
        Spacer(Modifier.height(Sg.GapM))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text(stringResource(R.string.contacts_phone_label)) },
            supportingText = { Text(stringResource(R.string.contacts_phone_helper)) },
            prefix = { Text("+373 ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Sg.GapS))
        Text(stringResource(R.string.contacts_tariff_warning),
            style = Sg.Caption, color = Sg.WarnTx)
        Spacer(Modifier.height(Sg.GapM))
        Button(onClick = {
            err = ""
            if (phone.length != 8) { err = ctx.getString(R.string.contacts_err_phone); return@Button }
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
                          PackageManager.PERMISSION_GRANTED
            if (!granted) { sendLauncher.launch(Manifest.permission.SEND_SMS); return@Button }

            val fullPhone = "+373$phone"
            val code = (1000..9999).random().toString()
            val txt = ctx.getString(R.string.sms_verification, code)
            SmsSender.send(ctx, fullPhone, txt) { ok, reason ->
                if (ok) { pendingCode = code; pendingNum = fullPhone; err = "" }
                else err = ctx.getString(R.string.contacts_err_send, reason ?: "")
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.contacts_send_code))
        }
    }

    if (pendingCode != null) {
        Spacer(Modifier.height(Sg.GapM))
        Text(stringResource(R.string.contacts_code_sent, pendingNum),
            style = Sg.BodySmall)
        Spacer(Modifier.height(Sg.GapS))
        OutlinedTextField(value = typed,
            onValueChange = { typed = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.contacts_code_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Sg.GapS))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (typed == pendingCode) {
                    scope.launch {
                        Prefs.addNum(ctx, pendingNum)
                        pendingCode = null; pendingNum = ""; typed = ""; phone = ""
                        onChange()
                    }
                } else err = ctx.getString(R.string.contacts_err_wrong_code)
            }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.contacts_confirm))
            }
            OutlinedButton(onClick = {
                pendingCode = null; pendingNum = ""; typed = ""; err = ""
            }) { Text(stringResource(R.string.contacts_cancel)) }
        }
    }

    if (err.isNotBlank()) {
        Spacer(Modifier.height(Sg.GapS))
        Text(err, color = Sg.DangerTx, style = Sg.Caption)
    }
}

/* ==================== FAQ ==================== */
@Composable
private fun FaqScreen() {
    Text(stringResource(R.string.faq_title), style = Sg.H2)
    Spacer(Modifier.height(Sg.GapM))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Sg.GapS)) {
        item { FaqItem(stringResource(R.string.faq_q1), stringResource(R.string.faq_a1)) }
        item { FaqItem(stringResource(R.string.faq_q2), stringResource(R.string.faq_a2)) }
        item { FaqItem(stringResource(R.string.faq_q3), stringResource(R.string.faq_a3)) }
        item { FaqItem(stringResource(R.string.faq_q4), stringResource(R.string.faq_a4)) }
        item { FaqItem(stringResource(R.string.faq_q5), stringResource(R.string.faq_a5)) }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Sg.Surface),
        onClick = { open = !open }) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(q, style = Sg.H3)
            if (open) {
                Spacer(Modifier.height(Sg.GapS))
                Text(a, style = Sg.BodySmall)
            }
        }
    }
}

/* ==================== helpers ==================== */
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
