package md.scamguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Показывается при первом запуске. Возвращает true когда пользователь принял EULA.
 *
 * После принятия:
 *   - флаг сохраняется в Prefs (acceptedEula = true)
 *   - отправляется consent receipt на сервер
 *   - вызывается onAccepted() — главный экран сможет рендериться
 */
@Composable
fun ConsentScreen(onAccepted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    val lang = LocaleHelper.readSavedLang(ctx)
    val isRu = lang == "ru"

    fun urlPrivacy() = if (isRu)
        "https://2023baracuda-ctrl.github.io/ScamGuard/privacy_ru.html"
    else
        "https://2023baracuda-ctrl.github.io/ScamGuard/privacy_ro.html"
    fun urlEula() = if (isRu)
        "https://2023baracuda-ctrl.github.io/ScamGuard/eula_ru.html"
    else
        "https://2023baracuda-ctrl.github.io/ScamGuard/eula_ro.html"

    fun open(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Surface(Modifier.fillMaxSize(), color = Sg.Background) {
        Column(Modifier.fillMaxSize().padding(24.dp)
            .verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(40.dp))
            Text("🛡️", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text(if (isRu) "Добро пожаловать в ScamGuard"
                 else "Bun venit la ScamGuard",
                 style = Sg.H1)
            Spacer(Modifier.height(8.dp))
            Text(if (isRu)
                "Прежде чем начать — короткое соглашение."
                else "Înainte de a începe — un acord scurt.",
                style = Sg.BodySmall, color = Sg.Muted)
            Spacer(Modifier.height(24.dp))

            // Краткое объяснение
            Card(colors = CardDefaults.cardColors(containerColor = Sg.SurfaceMuted)) {
                Column(Modifier.padding(16.dp)) {
                    Bullet(if (isRu) "✅ Всё работает локально на вашем устройстве."
                                else "✅ Totul rulează local pe dispozitiv.")
                    Bullet(if (isRu) "📵 Содержимое ваших SMS никуда не передаётся."
                                else "📵 Conținutul SMS-urilor nu părăsește dispozitivul.")
                    Bullet(if (isRu) "📤 Только при нажатии «Ошибочное уведомление» текст SMS отправляется анонимно."
                                else "📤 Doar la apăsarea «Avertisment eronat» textul SMS se trimite anonim.")
                    Bullet(if (isRu) "⚠️ Это вспомогательное средство, не заменяет вашу бдительность."
                                else "⚠️ Este un instrument auxiliar, nu înlocuiește vigilența dvs.")
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isRu) "Документы:" else "Documente:",
                    style = Sg.BodySmall, color = Sg.Muted)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { open(urlPrivacy()) }) {
                    Text(if (isRu) "Политика конфиденциальности" else "Politica de confidențialitate")
                }
                TextButton(onClick = { open(urlEula()) }) {
                    Text("EULA")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text(if (isRu)
                    "Я прочитал(а) Политику конфиденциальности и Соглашение и принимаю их."
                    else "Am citit Politica de confidențialitate și Acordul, le accept.",
                    style = Sg.BodySmall, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        Prefs.setAcceptedEula(ctx, true)
                        Reporter.consent(ctx)
                        onAccepted()
                    }
                },
                enabled = checked,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sg.Purple)
            ) {
                Text(if (isRu) "Принимаю и продолжаю" else "Accept și continui",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(text, style = Sg.BodySmall, modifier = Modifier.padding(vertical = 3.dp))
}
