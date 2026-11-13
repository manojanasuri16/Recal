package app.recal.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Semantic palette. Every colour has a light and a dark value; nothing is hard-coded in screens. */
@Immutable
data class RecalColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val fill: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val positive: Color,
    val destructive: Color,
    val isDark: Boolean,
)

private val Light = RecalColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0x1F767680),
    label = Color(0xFF0B0B0F),
    secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43),
    separator = Color(0x1F3C3C43),
    accent = Color(0xFFF2600C),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1FF2600C),
    positive = Color(0xFF28A745),
    destructive = Color(0xFFE5342B),
    isDark = false,
)

private val Dark = RecalColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    fill = Color(0x3D767680),
    label = Color(0xFFF5F5F7),
    secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5),
    separator = Color(0x29EBEBF5),
    accent = Color(0xFFFF8A3D),
    onAccent = Color(0xFF1A0A00),
    accentSoft = Color(0x33FF8A3D),
    positive = Color(0xFF32D05A),
    destructive = Color(0xFFFF5147),
    isDark = true,
)

val LocalRecalColors = staticCompositionLocalOf { Light }

/**
 * Type ramp on the system font. Tracking tightens as size grows and leading loosens as
 * size shrinks, so large titles read dense and body copy stays comfortable.
 */
object Type {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.018).em)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.008).em)
    val callout = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.004).em)
    val calloutStrong = callout.copy(fontWeight = FontWeight.SemiBold)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em)
}

/**
 * Springs, expressed the way Apple specifies them: critically damped by default,
 * a little bounce only when a gesture supplied the momentum.
 */
object Motion {
    /** Default UI spring: no overshoot, response of roughly 0.35 s. */
    fun <T> smooth() = spring<T>(dampingRatio = 1f, stiffness = 320f)
    /** Snappier variant for press feedback. */
    fun <T> quick() = spring<T>(dampingRatio = 1f, stiffness = 900f)
    /** For things the finger threw: drawer/sheet feel, damping 0.8. */
    fun <T> flick() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    val gentle = Spring.StiffnessMediumLow
}

/** Resolves the saved choice ("system" / "light" / "dark") against the device setting. */
@Composable
fun isDarkFor(choice: String): Boolean = when (choice) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun RecalTheme(dark: Boolean = isDarkFor(app.recal.data.Settings(androidx.compose.ui.platform.LocalContext.current).theme), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.surface,
            onBackground = c.label, onSurface = c.label, error = c.destructive)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.surface,
            onBackground = c.label, onSurface = c.label, error = c.destructive)
    }
    CompositionLocalProvider(LocalRecalColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

val colors: RecalColors
    @Composable get() = LocalRecalColors.current
