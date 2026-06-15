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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { App() } }
    }
}

private data class PermState(val sms: Boolean, val phone: Boolean,
                             val notif: Boolean, val overlay: Boolean) {
    val allCritical = sms && phone && notif
    val all = allCritical && overlay
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(check(ctx)) }
    LaunchedEffect(Unit) { if (state.allCritical) runCatching { CallWatchService.start(ctx) } }
    // обновлять при возврате с системного экрана
    val refresh = {
        state = check(ctx)
        if (state.allCritical) runCatching { CallWatchService.start(ctx) }
    }

    val multi = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    val anyResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7FB)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("🛡️ ScamGuard", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Защита от телефонных мошенников", color = Color(0xFF6B7280))
            Spacer(Modifier.height(20.dp))

            if (!state.allCritical) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Один раз настроим защиту", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Нажмите кнопку — система спросит разрешения, нажимайте «Разрешить».",
                            color = Color(0xFF92400E), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val needed = mutableListOf<String>()
                                if (!state.sms)   needed += Manifest.permission.RECEIVE_SMS
                                if (!state.phone) needed += Manifest.permission.READ_PHONE_STATE
                                if (!state.notif && Build.VERSION.SDK_INT >= 33)
                                    needed += Manifest.permission.POST_NOTIFICATIONS
                                if (needed.isNotEmpty()) multi.launch(needed.toTypedArray())
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) { Text("Включить защиту", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // дополнительные доступы (нельзя выдать пачкой — они открывают системные настройки)
            if (state.allCritical && !state.all) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Доп. настройки для максимальной защиты", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (!state.overlay) {
                            ExtraRow("Показ поверх других окон",
                                "Чтобы предупреждение всплыло поверх звонка") {
                                anyResult.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}")))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (state.allCritical) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("✓ Защита активна", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Приложение в фоне следит за входящими SMS и звонками. " +
                            "Если придёт код во время звонка — увидите красное предупреждение.",
                            fontSize = 13.sp, color = Color(0xFF065F46))
                    }
                }
                Spacer(Modifier.height(16.dp))
                HistoryBlock()
            }
        }
    }
}

@Composable
private fun ExtraRow(title: String, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
        OutlinedButton(onClick = onClick) { Text("Включить") }
    }
}

@Composable
private fun HistoryBlock() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    LaunchedEffect(Unit) { scope.launch { items = History.list(ctx) } }

    Text("История предупреждений", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) {
        Text("Пока пусто — подозрительных событий не было.",
            color = Color(0xFF6B7280), fontSize = 13.sp)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items) { (t, line) ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(SimpleDateFormat("dd MMM, HH:mm", Locale("ru")).format(Date(t)),
                            fontSize = 12.sp, color = Color(0xFF6B7280))
                        Text(line, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun check(ctx: Context): PermState = PermState(
    sms     = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS)
              == PackageManager.PERMISSION_GRANTED,
    phone   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
              == PackageManager.PERMISSION_GRANTED,
    notif   = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx,
              Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    overlay = Settings.canDrawOverlays(ctx)
)
