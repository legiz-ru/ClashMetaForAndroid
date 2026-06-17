package com.github.kr328.clash.design.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The three visual variants the app currently ships, matching the existing
 * `AppThemeLight` / `AppThemeDark` / `AppThemeSummer` XML themes.
 */
enum class ClashThemeVariant {
    Light,
    Dark,
    Summer,
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

private val SummerColors: ColorScheme = lightColorScheme(
    primary = SummerPrimary,
    onPrimary = SummerOnPrimary,
    primaryContainer = SummerPrimaryContainer,
    onPrimaryContainer = SummerOnPrimaryContainer,
    secondary = SummerSecondary,
    onSecondary = SummerOnSecondary,
    secondaryContainer = SummerSecondaryContainer,
    onSecondaryContainer = SummerOnSecondaryContainer,
    tertiary = SummerTertiary,
    onTertiary = SummerOnTertiary,
    tertiaryContainer = SummerTertiaryContainer,
    onTertiaryContainer = SummerOnTertiaryContainer,
    error = SummerError,
    onError = SummerOnError,
    background = SummerBackground,
    onBackground = SummerOnBackground,
    surface = SummerSurface,
    onSurface = SummerOnSurface,
    surfaceVariant = SummerSurfaceVariant,
    onSurfaceVariant = SummerOnSurfaceVariant,
    outline = SummerOutline,
    outlineVariant = SummerOutlineVariant,
    surfaceContainer = SummerSurfaceContainer,
    surfaceContainerHigh = SummerSurfaceContainerHigh,
    surfaceContainerHighest = SummerSurfaceContainerHighest,
)

/**
 * Palette tokens that are not part of [ColorScheme] but are needed by custom
 * components such as the ghost power button. Exposed through a CompositionLocal
 * so screens read them the same way as [MaterialTheme.colorScheme].
 */
@Immutable
data class ClashExtraColors(
    val powerOff: Color = PowerOff,
    val powerOn: Color = PowerOn,
    val clashStopped: Color = ClashStopped,
)

val LocalClashExtraColors = staticCompositionLocalOf { ClashExtraColors() }

object ClashTheme {
    val extraColors: ClashExtraColors
        @Composable
        get() = LocalClashExtraColors.current
}

/**
 * Root theme for all Compose surfaces in the app.
 *
 * @param variant explicit variant; when null it is derived from [darkTheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ClashThemeVariant? = null,
    content: @Composable () -> Unit,
) {
    val resolved = variant ?: if (darkTheme) ClashThemeVariant.Dark else ClashThemeVariant.Light

    val colorScheme = when (resolved) {
        ClashThemeVariant.Light -> LightColors
        ClashThemeVariant.Dark -> DarkColors
        ClashThemeVariant.Summer -> SummerColors
    }

    CompositionLocalProvider(
        LocalClashExtraColors provides ClashExtraColors(),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
