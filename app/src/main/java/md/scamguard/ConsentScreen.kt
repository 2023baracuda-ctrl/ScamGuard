package md.scamguard

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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

@Composable
fun ConsentScreen(onAccepted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkAge by remember { mutableStateOf(false) }
    var checkTerms by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(LocaleHelper.readSavedLang(ctx)) }
    

   fun urlPrivacy() = "https://scamguardrm.pages.dev//privacy-$lang"
   fun urlEula() = "https://scamguardrm.pages.dev//eula-$lang"

    fun open(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Surface(Modifier.fillMaxSize(), color = Sg.Background) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {

            /* === Переключатель языка (вверху справа) === */
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                                        (ctx as? ComponentActivity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("🛡️", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.consent_welcome), style = Sg.H1)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.consent_intro), style = Sg.BodySmall, color = Sg.Muted)
            Spacer(Modifier.height(20.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Sg.SurfaceMuted)) {
                Column(Modifier.padding(16.dp)) {
                    Bullet(stringResource(R.string.consent_bullet1))
                    Bullet(stringResource(R.string.consent_bullet2))
                    Bullet(stringResource(R.string.consent_bullet3))
                    Bullet(stringResource(R.string.consent_bullet4))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.consent_docs_title),
                style = Sg.BodySmall, color = Sg.Muted)
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = { open(urlPrivacy()) }) {
                    Text(stringResource(R.string.consent_link_privacy))
                }
                TextButton(onClick = { open(urlEula()) }) {
                    Text(stringResource(R.string.consent_link_eula))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkAge, onCheckedChange = { checkAge = it })
                Text(stringResource(R.string.consent_check_age),
                    style = Sg.BodySmall, modifier = Modifier.padding(start = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkTerms, onCheckedChange = { checkTerms = it })
                Text(stringResource(R.string.consent_check_terms),
                    style = Sg.BodySmall, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        Prefs.setAcceptedEula(ctx, true)
                        Prefs.setAcceptedAge(ctx, true)
                        Reporter.consent(ctx)
                        onAccepted()
                    }
                },
                enabled = checkAge && checkTerms,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sg.Purple),
            ) {
                Text(stringResource(R.string.consent_button),
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
