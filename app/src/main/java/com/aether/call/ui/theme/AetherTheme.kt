package com.aether.call.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aether.call.data.AppThemeMode

data class AetherColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val isDark: Boolean,
)

val DarkAetherColors = AetherColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1E2024),
    surfaceVariant = Color(0xFF141619),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    divider = Color(0xFF2E3138),
    accent = Color(0xFF10B981),
    isDark = true,
)

val LightAetherColors = AetherColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFEFF4),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF6C6C70),
    divider = Color(0xFFE5E5EA),
    accent = Color(0xFF10B981),
    isDark = false,
)

val LocalAetherColors = staticCompositionLocalOf { DarkAetherColors }

object AetherTheme {
    val colors: AetherColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAetherColors.current
}

@Composable
fun isAppInDarkTheme(themeModeId: String): Boolean {
    val mode = AppThemeMode.fromId(themeModeId)
    return when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

@Composable
fun AetherTheme(
    appThemeMode: String = AppThemeMode.SYSTEM.id,
    content: @Composable () -> Unit,
) {
    val isDark = isAppInDarkTheme(appThemeMode)
    val customColors = if (isDark) DarkAetherColors else LightAetherColors

    val materialColors = if (isDark) {
        darkColorScheme(
            background = customColors.background,
            surface = customColors.surface,
            surfaceVariant = customColors.surfaceVariant,
            onBackground = customColors.textPrimary,
            onSurface = customColors.textPrimary,
            onSurfaceVariant = customColors.textSecondary,
            outline = customColors.divider,
            primary = customColors.accent,
        )
    } else {
        lightColorScheme(
            background = customColors.background,
            surface = customColors.surface,
            surfaceVariant = customColors.surfaceVariant,
            onBackground = customColors.textPrimary,
            onSurface = customColors.textPrimary,
            onSurfaceVariant = customColors.textSecondary,
            outline = customColors.divider,
            primary = customColors.accent,
        )
    }

    CompositionLocalProvider(LocalAetherColors provides customColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}
