package md.scamguard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Красное полноэкранное предупреждение поверх любого экрана (включая звонок).
 * Закрывается только кнопкой «ЗАКРЫТЬ».
 *
 * Запускается через AlertActivity.show(...) из любого фона (SMS receiver и т.п.).
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        val level = intent.getStringExtra(EX_LEVEL) ?: "HIGH"
        val sender = intent.getStringExtra(EX_SENDER) ?: ""
        val duringCall = intent.getBooleanExtra(EX_DURING, false)
        val callNumber = intent.getStringExtra(EX_CALL) ?: ""
        val reason = intent.getStringExtra(EX_REASON) ?: ""

        setContent { AlertUi(level, sender, duringCall, callNumber, reason, onClose = { finish() }) }
    }

    companion object {
        const val EX_LEVEL = "lvl"; const val EX_SENDER = "snd"
        const val EX_DURING = "dur"; const val EX_CALL = "call"; const val EX_REASON = "rsn"

        fun show(ctx: Context, level: Threat, sender: String, duringCall: Boolean,
                 callNumber: String, reason: String) {
            val i = Intent(ctx, AlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EX_LEVEL, level.name)
                putExtra(EX_SENDER, sender)
                putExtra(EX_DURING, duringCall)
                putExtra(EX_CALL, callNumber)
                putExtra(EX_REASON, reason)
            }
            ctx.startActivity(i)
        }
    }
}

@Composable
private fun AlertUi(level: String, sender: String, duringCall: Boolean,
                    callNumber: String, reason: String, onClose: () -> Unit) {
    val high = level == "HIGH"
    val bg = if (high) Color(0xFFB91C1C) else Color(0xFFB45309)   // красный / янтарный

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.55f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚠️", fontSize = 64.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (high) "ВНИМАНИЕ: ПОХОЖЕ НА МОШЕННИКОВ"
                    else "Подозрительное сообщение",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    if (high)
                        "Вам пришёл одноразовый код" +
                            (if (duringCall) " во время звонка" else "") + "."
                    else "Сообщение от $sender вызывает сомнения.",
                    color = Color.White, fontSize = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "НИКОМУ не диктуйте код. Банк, госуслуги и полиция никогда не запрашивают код по телефону.",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )

                if (reason.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Почему мы вас предупреждаем: $reason",
                        color = Color(0xFFFFE4E6), fontSize = 13.sp)
                }
                if (duringCall && callNumber.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Текущий звонок: $callNumber",
                        color = Color(0xFFFFE4E6), fontSize = 13.sp)
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = bg
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ЗАКРЫТЬ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}
