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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        // Статус-бар / навигейшн-бар всегда следуют СИСТЕМНОЙ теме телефона,
        // независимо от темы, выбранной внутри приложения (Settings → Тема).
        val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = if (systemDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window.navigationBarColor = if (systemDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = !systemDark
            isAppearanceLightNavigationBars = !systemDark
        }

        // Восстанавливаем выбранную вкладку (для случая пересоздания активити)
        val startTab = savedInstanceState?.getString(KEY_TAB)
            ?: intent.getStringExtra(KEY_TAB)
            ?: Tab.Home.name

        setContent { 
            var themeMode by remember { mutableStateOf("system") }
    LaunchedEffect(Unit) {
        themeMode = withContext(Dispatchers.IO) { Prefs.themeMode(this@MainActivity) }
    }
            SgTheme(themeMode = themeMode) {
        Root(
            initialTab = startTab,
            onThemeChange = { newMode -> themeMode = newMode }  // ← это ключ
        )
    }
        } 
    }

    companion object {
        const val KEY_TAB = "current_tab"
    }
}

@Composable
private fun Root(initialTab: String, onThemeChange: (String) -> Unit) {
    val ctx = LocalContext.current
    var accepted by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        accepted = Prefs.acceptedEula(ctx)
    }

    when (accepted) {
        null -> Surface(Modifier.fillMaxSize(), color = Sg.Background) {}
        false -> ConsentScreen(onAccepted = { accepted = true })
        true -> App(initialTab = initialTab, onThemeChange = onThemeChange)
    }
}

private data class PermState(
    val sms: Boolean, val phone: Boolean, val notif: Boolean,
    val answerCalls: Boolean, val overlay: Boolean,
) {
    val allCritical = sms && phone && notif
    val all         = allCritical && overlay && answerCalls
}

private enum class Tab(val labelId: Int) {
    Home(R.string.nav_home),
    History(R.string.nav_history),
    Faq(R.string.nav_faq),
    Settings(R.string.nav_settings);
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(initialTab: String, onThemeChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(check(ctx)) }
    var lang by remember { mutableStateOf(LocaleHelper.readSavedLang(ctx)) }
    var tab by remember { mutableStateOf(
        runCatching { Tab.valueOf(initialTab) }.getOrDefault(Tab.Home)
    ) }
    var history by remember { mutableStateOf<List<History.Event>>(emptyList()) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    val refresh: () -> Unit = {
        state = check(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        scope.launch {
            history = History.list(ctx)
        }
    }

    val anyResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { refresh() }

    // Объединённая цепочка: SMS/телефон/уведомления → сразу оверлей
    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        // если критичные дали, а оверлея ещё нет — открываем системный экран
        val fresh = check(ctx)
        if (fresh.allCritical && !fresh.overlay) {
            anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")))
        }
    }

    LaunchedEffect(Unit) {
        history = History.list(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
        update = Updater.check(BuildConfig.VERSION_NAME)
    }
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

    val askEverythingAtOnce: () -> Unit = {
        val need = mutableListOf<String>()
        if (!state.sms) need += Manifest.permission.RECEIVE_SMS
        if (!state.phone) need += Manifest.permission.READ_PHONE_STATE
        if (!state.notif && Build.VERSION.SDK_INT >= 33)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (!state.answerCalls && Build.VERSION.SDK_INT >= 28)
            need += Manifest.permission.ANSWER_PHONE_CALLS
        if (need.isNotEmpty()) {
            multi.launch(need.toTypedArray())
        } else if (!state.overlay) {
            anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")))
        }
    }

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
    var langMenuOpen by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { langMenuOpen = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lang.uppercase())
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
        DropdownMenu(
            expanded = langMenuOpen,
            onDismissRequest = { langMenuOpen = false }
        ) {
            listOf("ru" to "🇷🇺 Русский", "ro" to "🇷🇴 Română", "en" to "🇬🇧 English").forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        langMenuOpen = false
                        if (code != lang) {
                            lang = code
                            LocaleHelper.cache(ctx, code)
                            scope.launch { Prefs.setLang(ctx, code) }
                            val act = (ctx as? ComponentActivity)
                            act?.intent = act?.intent?.apply { putExtra(MainActivity.KEY_TAB, tab.name) }
                            act?.recreate()
                        }
                    }
                )
            }
        }
    }
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
                        onClick = { tab = t; if (t == Tab.History) refresh() },
                        icon = {
                            Icon(when (t) {
                                Tab.Home -> Icons.Filled.Home
                                Tab.History -> Icons.Filled.List
                                Tab.Faq -> Icons.Outlined.Info
                                Tab.Settings -> Icons.Filled.Settings
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
            Column(Modifier.fillMaxSize()
                .padding(horizontal = Sg.PaddingScreen, vertical = 12.dp)) {

                when (tab) 
                {
                    Tab.Home -> HomeScreen(state, update,
                        onSetup = askEverythingAtOnce,
                        onOpenSms = {
                            anyResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${ctx.packageName}")))
                        },
                        onOpenOverlay = {
                            anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")))
                        },
                        onOpenFaq = { tab = Tab.Faq },
                    )
                    Tab.History  -> HistoryScreen(history)
                    Tab.Faq      -> FaqScreen()
                    Tab.Settings -> SettingsScreen(
        onProtectionChanged = { refresh() },
        onThemeChanged = { newMode ->
            scope.launch {
                withContext(Dispatchers.IO) { Prefs.setThemeMode(ctx, newMode) }
                // тема применится при следующей перерисовке
            }
            onThemeChange(newMode)
        }
    )
                }
            }
        }
    }
}

/* ==================== HOME ==================== */
@Composable
private fun HomeScreen(
    state: PermState, update: UpdateInfo?,
    onSetup: () -> Unit, onOpenSms: () -> Unit,
    onOpenOverlay: () -> Unit, onOpenFaq: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            !state.allCritical -> SetupCard(
                stringResource(R.string.onb_setup_title),
                stringResource(R.string.onb_setup_desc),
                onSetup,
            )
            !state.overlay -> SetupCard(
                stringResource(R.string.onb_overlay_title),
                stringResource(R.string.onb_overlay_desc),
                onOpenOverlay,
            )
            else -> StatusCard(state, onOpenSms, onOpenFaq)
        }
                  
        
    }
}

@Composable
private fun SetupCard(title: String, desc: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Sg.WarnBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(title, style = Sg.H2)
            Spacer(Modifier.height(4.dp))
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
    val tx = if (allGreen) Sg.SuccessTx else Sg.WarnTx
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.status_active),
                    color = tx, style = Sg.H1,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.status_active_desc),
                color = tx, style = Sg.Body,
            )

            Spacer(Modifier.height(Sg.GapM))
            Surface(
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.status_safety_hint),
                    color = tx,
                    style = Sg.BodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (!state.overlay) {
                Spacer(Modifier.height(Sg.GapM))
                Text(
                    stringResource(R.string.status_partial_overlay),
                    color = tx, style = Sg.BodySmall
                )
            }
        }
    }
}
@Composable
private fun UpdateBanner(u: UpdateInfo) {
    val ctx = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Sg.InfoBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Sg.PaddingCard)) {
            Text(stringResource(R.string.update_banner_title, u.latestVersion),
                style = Sg.H3)
            if (u.notes.isNotBlank())
                Text(u.notes, style = Sg.Caption, color = Sg.Ink)
            Spacer(Modifier.height(Sg.GapS))
            Button(onClick = { Updater.openInBrowser(ctx, u.downloadUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Sg.Purple)) {
                Text(stringResource(R.string.update_banner_download))
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

/* ==================== FAQ ==================== */
@Composable
private fun FaqScreen() {
    Text(stringResource(R.string.faq_title), style = Sg.H2)
    Spacer(Modifier.height(Sg.GapM))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Sg.GapS)) {
        item { FaqItem(stringResource(R.string.faq_q1), stringResource(R.string.faq_a1)) }
        item { FaqItem(stringResource(R.string.faq_q2), stringResource(R.string.faq_a2)) }
        item { FaqItem(stringResource(R.string.faq_q3), stringResource(R.string.faq_a3)) }
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
    answerCalls = Build.VERSION.SDK_INT < 28 ||
          ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS)
          == PackageManager.PERMISSION_GRANTED,
    overlay = Settings.canDrawOverlays(ctx),
)
