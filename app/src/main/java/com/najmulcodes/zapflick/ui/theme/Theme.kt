package com.najmulcodes.zapflick.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryTint,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondaryTint,
    tertiary = Amber,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    onSurfaceVariant = PaperVariant,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkContainerHigh,
    surfaceContainerHighest = InkContainerHighest,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF000F5C),
    secondary = Color(0xFF6B3FC4),
    tertiary = Color(0xFF8A5100),
    background = LightBackground,
    onBackground = Color(0xFF171B22),
    surface = LightBackground,
    onSurface = Color(0xFF171B22),
    surfaceVariant = Color(0xFFE1E4EC),
    onSurfaceVariant = Color(0xFF4B5160),
    surfaceContainer = Color(0xFFEDEFF6),
    surfaceContainerHigh = Color(0xFFE7E9F1),
    surfaceContainerHighest = Color(0xFFE1E4EC),
    outline = Color(0xFF7A8090),
    outlineVariant = Color(0xFFC6CAD6),
)

/** Dark-first with the brand palette. Dynamic color is opt-in so the brand colors win by default. */
@Composable
fun ZapFlickTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
