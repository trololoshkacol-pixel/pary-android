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

// Swiss Modern accents. Indices are persisted in Store and kept stable for backups.
val AccentHex = listOf(0xFFE53935, 0xFF356AE6, 0xFF27845A, 0xFFD28A24, 0xFF8056C8)
val AccentNames = listOf("Swiss Red", "Cobalt", "Forest", "Amber", "Violet")

private data class AccentPalette(
    val light: Color,
    val dark: Color,
    val lightContainer: Color,
    val darkContainer: Color,
    val lightOnContainer: Color,
    val darkOnContainer: Color,
    val tertiary: Color,
)

private val AccentPalettes = listOf(
    AccentPalette(Color(0xFFE53935), Color(0xFFFF625B), Color(0xFFFCE7E5), Color(0xFF492321), Color(0xFF7D211D), Color(0xFFFFDAD7), Color(0xFF4778B8)),
    AccentPalette(Color(0xFF285CCB), Color(0xFF82A8FF), Color(0xFFE4ECFF), Color(0xFF20365F), Color(0xFF183A83), Color(0xFFD9E2FF), Color(0xFF27845A)),
    AccentPalette(Color(0xFF20764C), Color(0xFF72D6A0), Color(0xFFDDF4E6), Color(0xFF1D4430), Color(0xFF164B30), Color(0xFFBDF2D0), Color(0xFF4778B8)),
    AccentPalette(Color(0xFF98600E), Color(0xFFFFC46B), Color(0xFFFFEFD5), Color(0xFF4B351A), Color(0xFF68400A), Color(0xFFFFE2B5), Color(0xFF4778B8)),
    AccentPalette(Color(0xFF7043B5), Color(0xFFC3A0FF), Color(0xFFEFE5FF), Color(0xFF3D2A5A), Color(0xFF4D287F), Color(0xFFEAD7FF), Color(0xFF4778B8)),
)

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
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(13.dp),
    large = RoundedCornerShape(17.dp),
    extraLarge = RoundedCornerShape(21.dp),
)

@Composable
fun ParyTheme(theme: String, accentIndex: Int, animationsEnabled: Boolean = true, content: @Composable () -> Unit) {
    val isLight = theme == "light"
    val amoled = theme == "amoled"
    val accent = AccentPalettes.getOrElse(accentIndex) { AccentPalettes.first() }
    val scheme = if (isLight) {
        lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.lightOnContainer,
            secondary = Color(0xFF62645F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFEAEAE5),
            onSecondaryContainer = Color(0xFF252622),
            tertiary = accent.tertiary,
            background = Color(0xFFF5F5F2),
            onBackground = Color(0xFF181818),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF181818),
            surfaceVariant = Color(0xFFF0F0EC),
            onSurfaceVariant = Color(0xFF777777),
            outline = Color(0xFFE5E5E0),
            outlineVariant = Color(0xFFECECE7),
            error = Color(0xFFD94343),
            onError = Color.White,
            errorContainer = Color(0xFFFFE8E6),
            onErrorContainer = Color(0xFF7D211D),
        )
    } else {
        val background = if (amoled) Color.Black else Color(0xFF171819)
        val surface = if (amoled) Color(0xFF101112) else Color(0xFF252729)
        val surfaceVariant = if (amoled) Color(0xFF191B1C) else Color(0xFF2D3032)
        darkColorScheme(
            primary = accent.dark,
            onPrimary = Color(0xFF171819),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.darkOnContainer,
            secondary = Color(0xFFA4A5A5),
            onSecondary = Color(0xFF202122),
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = Color(0xFFF4F4F1),
            tertiary = accent.tertiary.copy(alpha = 0.9f),
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
        accentSoft = scheme.primary.copy(alpha = if (isLight) 0.10f else 0.17f),
        isLight = isLight,
        heroEnd = scheme.surface,
        animationsEnabled = animationsEnabled,
    )

    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = ParyTypography, shapes = ParyShapes, content = content)
    }
}
