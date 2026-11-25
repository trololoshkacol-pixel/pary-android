package com.pary.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Kept for compatibility with the existing appearance settings UI.
val AccentHex = listOf(0xFFE5484D, 0xFF4778B8, 0xFF27845A, 0xFFB7791F, 0xFF8B6CC1)
val AccentNames = listOf("Swiss Red", "Cobalt", "Forest", "Amber", "Violet")

data class ExtraColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val cardAlt: Color,
    val accentSoft: Color,
    val isLight: Boolean,
    val heroEnd: Color,
    val animationsEnabled: Boolean,
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(
        success = Color(0xFF27845A),
        warning = Color(0xFFD99A32),
        danger = Color(0xFFD94343),
        cardAlt = Color(0xFF252729),
        accentSoft = Color(0x1FE53935),
        isLight = false,
        heroEnd = Color(0xFF252729),
        animationsEnabled = true,
    )
}

private val ParyTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp, lineHeight = 42.sp),
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontSize = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp, lineHeight = 35.sp),
    headlineLarge = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.65).sp, lineHeight = 33.sp),
    headlineMedium = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.45).sp, lineHeight = 29.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.25.sp),
)

private val ParyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun ParyTheme(theme: String, accentIndex: Int, animationsEnabled: Boolean = true, content: @Composable () -> Unit) {
    val isLight = theme == "light"
    val amoled = theme == "amoled"
    val accent = Color(AccentHex[accentIndex.coerceIn(0, AccentHex.lastIndex)])
    val accentDark = if (isLight) accent else accent.copy(red = (accent.red * 0.82f).coerceIn(0f, 1f), green = (accent.green * 0.82f).coerceIn(0f, 1f), blue = (accent.blue * 0.82f).coerceIn(0f, 1f))

    val scheme = if (isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.13f),
            onPrimaryContainer = accent.copy(red = 0.48f, green = 0.12f, blue = 0.12f),
            secondary = Color(0xFF62645F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAEAE5),
            onSecondaryContainer = Color(0xFF252622),
            tertiary = Color(0xFF4778B8),
            background = Color(0xFFF3F4F6),
            onBackground = Color(0xFF181818),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF181818),
            surfaceVariant = Color(0xFFE9ECF1),
            onSurfaceVariant = Color(0xFF777777),
            outline = Color(0xFFE1E5EB),
            outlineVariant = Color(0xFFEAEDF2),
            error = Color(0xFFD94343),
            onError = Color.White,
            errorContainer = Color(0xFFFFE8E6),
            onErrorContainer = Color(0xFF7D211D),
        )
    } else {
        val background = if (amoled) Color.Black else Color(0xFF101318)
        val surface = if (amoled) Color(0xFF090B0E) else Color(0xFF191F27)
        val surfaceVariant = if (amoled) Color(0xFF14181E) else Color(0xFF232C37)
        darkColorScheme(
            primary = accentDark,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF492321),
            onPrimaryContainer = Color(0xFFFFDAD7),
            secondary = Color(0xFFA4A5A5),
            onSecondary = Color(0xFF202122),
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = Color(0xFFF4F4F1),
            tertiary = Color(0xFF83A9D8),
            background = background,
            onBackground = Color(0xFFF4F4F1),
            surface = surface,
            onSurface = Color(0xFFF4F4F1),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFA4A5A5),
            outline = Color(0xFF383A3B),
            outlineVariant = Color(0xFF303233),
            error = Color(0xFFF04444),
            onError = Color.White,
            errorContainer = Color(0xFF492321),
            onErrorContainer = Color(0xFFFFDAD7),
        )
    }

    val extra = ExtraColors(
        success = Color(0xFF27845A),
        warning = Color(0xFFD99A32),
        danger = Color(0xFFD94343),
        cardAlt = scheme.surfaceVariant,
        accentSoft = scheme.primary.copy(alpha = if (isLight) 0.11f else 0.19f),
        isLight = isLight,
        heroEnd = scheme.surface,
        animationsEnabled = animationsEnabled,
    )

    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = ParyTypography, shapes = ParyShapes, content = content)
    }
}
