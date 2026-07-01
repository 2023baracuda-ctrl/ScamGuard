package md.scamguard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Тема приложения. Сейчас цвета `Sg` хранят значения для **активной** темы —
 * Compose сам ничего не пересоздаст при смене темы, поэтому мы держим
 * два набора (Light и Dark) и подменяем поля Sg.* через переменную.
 *
 * Compose читает цвета лениво — пересборка происходит каждый раз когда
 * меняется state. Чтобы простая реализация работала и не требовала
 * рефакторинга всех экранов, делаем var-поля + переключение через mutator.
 */

object Sg {
    // Бренд (одинаков в обеих темах)
    val Purple        = Color(0xFF7C3AED)
    val PurpleDark    = Color(0xFF6D28D9)
    val PurpleLight   = Color(0xFFEDE9FE)

    // Фон / поверхности — переключаются темой
    var Background    = Color(0xFFF7F7FB)
    var Surface       = Color(0xFFFFFFFF)
    var SurfaceMuted  = Color(0xFFF3F3F8)

    // Текст
    var Ink           = Color(0xFF1F2330)
    var Muted         = Color(0xFF6B7280)
    var Line          = Color(0xFFE5E7EB)

    // Состояния (фоны статусных блоков)
    var SuccessBg     = Color(0xFFD1FAE5)
    var SuccessTx     = Color(0xFF065F46)
    var WarnBg        = Color(0xFFFEF3C7)
    var WarnTx        = Color(0xFF92400E)
    var DangerBg      = Color(0xFFFEE2E2)
    var DangerTx      = Color(0xFFB91C1C)
    var InfoBg        = Color(0xFFE0E7FF)

    // Алерты — не меняются по теме (всегда красный/жёлтый)
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

    // Типографика — пересоздаются при смене темы (т.к. в них зашит Ink)
    var H1 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    var H2 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,      color = Ink)
    var H3 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold,  color = Ink)
    var Body      = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Ink)
    var BodySmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Ink)
    var Caption   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Muted)
    val Button    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)

    /** Перенастраивает все динамические цвета и стили на тёмную палитру. */
    fun applyDark() {
        Background    = Color(0xFF0F1117)
        Surface       = Color(0xFF1A1D26)
        SurfaceMuted  = Color(0xFF222632)
        Ink           = Color(0xFFEFEFF4)
        Muted         = Color(0xFF9CA3AF)
        Line          = Color(0xFF2D3142)
        SuccessBg     = Color(0xFF064E3B)
        SuccessTx     = Color(0xFFA7F3D0)
        WarnBg        = Color(0xFF78350F)
        WarnTx        = Color(0xFFFCD34D)
        DangerBg      = Color(0xFF7F1D1D)
        DangerTx      = Color(0xFFFCA5A5)
        InfoBg        = Color(0xFF312E81)
        rebuildTextStyles()
    }

    /** Перенастраивает все динамические цвета и стили на светлую палитру. */
    fun applyLight() {
        Background    = Color(0xFFF7F7FB)
        Surface       = Color(0xFFFFFFFF)
        SurfaceMuted  = Color(0xFFF3F3F8)
        Ink           = Color(0xFF1F2330)
        Muted         = Color(0xFF6B7280)
        Line          = Color(0xFFE5E7EB)
        SuccessBg     = Color(0xFFD1FAE5)
        SuccessTx     = Color(0xFF065F46)
        WarnBg        = Color(0xFFFEF3C7)
        WarnTx        = Color(0xFF92400E)
        DangerBg      = Color(0xFFFEE2E2)
        DangerTx      = Color(0xFFB91C1C)
        InfoBg        = Color(0xFFE0E7FF)
        rebuildTextStyles()
    }

    private fun rebuildTextStyles() {
        H1        = H1.copy(color = Ink)
        H2        = H2.copy(color = Ink)
        H3        = H3.copy(color = Ink)
        Body      = Body.copy(color = Ink)
        BodySmall = BodySmall.copy(color = Ink)
        Caption   = Caption.copy(color = Muted)
    }
}

private val SgLightColors = lightColorScheme(
    primary       = Sg.Purple,
    onPrimary     = Color.White,
    background    = Color(0xFFF7F7FB),
    surface       = Color(0xFFFFFFFF),
    onSurface     = Color(0xFF1F2330),
    surfaceVariant= Color(0xFFF3F3F8),
)

private val SgDarkColors = darkColorScheme(
    primary       = Sg.Purple,
    onPrimary     = Color.White,
    background    = Color(0xFF0F1117),
    surface       = Color(0xFF1A1D26),
    onSurface     = Color(0xFFEFEFF4),
    surfaceVariant= Color(0xFF222632),
)

/**
 * Тема приложения.
 * @param themeMode "system" / "light" / "dark"
 */
@Composable
fun SgTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> systemDark
    }
    if (isDark) Sg.applyDark() else Sg.applyLight()

    val typo = Typography(
        titleLarge  = Sg.H1,
        titleMedium = Sg.H2,
        titleSmall  = Sg.H3,
        bodyLarge   = Sg.Body,
        bodyMedium  = Sg.BodySmall,
        bodySmall   = Sg.Caption,
        labelLarge  = Sg.Button,
    )
    MaterialTheme(
        colorScheme = if (isDark) SgDarkColors else SgLightColors,
        typography  = typo,
        content     = content
    )
}
