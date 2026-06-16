package md.scamguard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ВСЯ ВИЗУАЛЬНАЯ ТЕМА В ОДНОМ ФАЙЛЕ.
 * Меняй цвета здесь — изменится во всём приложении.
 */

object Sg {
    // Бренд
    val Purple        = Color(0xFF7C3AED)
    val PurpleDark    = Color(0xFF6D28D9)
    val PurpleLight   = Color(0xFFEDE9FE)

    // Фон
    val Background    = Color(0xFFF7F7FB)
    val Surface       = Color(0xFFFFFFFF)
    val SurfaceMuted  = Color(0xFFF3F3F8)

    // Текст
    val Ink           = Color(0xFF1F2330)
    val Muted         = Color(0xFF6B7280)
    val Line          = Color(0xFFE5E7EB)

    // Состояния
    val SuccessBg     = Color(0xFFD1FAE5)
    val SuccessTx     = Color(0xFF065F46)
    val WarnBg        = Color(0xFFFEF3C7)
    val WarnTx        = Color(0xFF92400E)
    val DangerBg      = Color(0xFFFEE2E2)
    val DangerTx      = Color(0xFFB91C1C)
    val InfoBg        = Color(0xFFE0E7FF)

    val AlertHighBg   = Color(0xFFB91C1C)
    val AlertLowBg    = Color(0xFFB45309)
    val ScreenScrim   = Color(0xCC000000)

    // Размеры
    val PaddingScreen: Dp = 16.dp
    val PaddingCard:   Dp = 14.dp
    val CardRadius:    Dp = 14.dp
    val BigRadius:     Dp = 20.dp
    val GapL:          Dp = 16.dp
    val GapM:          Dp = 10.dp
    val GapS:          Dp = 6.dp

    // Типографика
    val H1 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    val H2 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,      color = Ink)
    val H3 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold,  color = Ink)
    val Body  = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Ink)
    val BodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Ink)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Muted)
    val Button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

private val SgColors = lightColorScheme(
    primary       = Sg.Purple,
    onPrimary     = Color.White,
    background    = Sg.Background,
    surface       = Sg.Surface,
    onSurface     = Sg.Ink,
    surfaceVariant= Sg.SurfaceMuted,
)

private val SgTypography = Typography(
    titleLarge  = Sg.H1,
    titleMedium = Sg.H2,
    titleSmall  = Sg.H3,
    bodyLarge   = Sg.Body,
    bodyMedium  = Sg.BodySmall,
    bodySmall   = Sg.Caption,
    labelLarge  = Sg.Button,
)

@Composable
fun SgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SgColors,
        typography  = SgTypography,
        content     = content
    )
}
