package com.github.kr328.clash.design.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * Compose color tokens mirrored 1:1 from `design/src/main/res/values/colors.xml`.
 *
 * Keep these in sync with the XML palette while both UI layers coexist during the
 * Compose migration. Once the View layer is removed, this file becomes the single
 * source of truth for the color palette.
 */

// ---------------------------------------------------------------------------
// Material 3 — Light
// ---------------------------------------------------------------------------
internal val LightPrimary = Color(0xFF1A5FAC)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD4E3FF)
internal val LightOnPrimaryContainer = Color(0xFF001C3A)
internal val LightSecondary = Color(0xFF535F70)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFD7E3F8)
internal val LightOnSecondaryContainer = Color(0xFF101C2B)
internal val LightTertiary = Color(0xFF6B5778)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFF0DBFF)
internal val LightOnTertiaryContainer = Color(0xFF251432)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightBackground = Color(0xFFFAFAFA)
internal val LightOnBackground = Color(0xFF1B1B1F)
internal val LightSurface = Color(0xFFFAFAFA)
internal val LightOnSurface = Color(0xFF1B1B1F)
internal val LightSurfaceVariant = Color(0xFFE0E2EC)
internal val LightOnSurfaceVariant = Color(0xFF44474E)
internal val LightOutline = Color(0xFF74777F)
internal val LightOutlineVariant = Color(0xFFC4C6CF)
internal val LightSurfaceContainer = Color(0xFFF0F0F3)
internal val LightSurfaceContainerHigh = Color(0xFFE8E8EB)
internal val LightSurfaceContainerHighest = Color(0xFFE0E0E3)

// ---------------------------------------------------------------------------
// Material 3 — Dark
// ---------------------------------------------------------------------------
internal val DarkPrimary = Color(0xFF8AB4F8)
internal val DarkOnPrimary = Color(0xFF002D6D)
internal val DarkPrimaryContainer = Color(0xFF004397)
internal val DarkOnPrimaryContainer = Color(0xFFD4E3FF)
internal val DarkSecondary = Color(0xFFBBC7DB)
internal val DarkOnSecondary = Color(0xFF253140)
internal val DarkSecondaryContainer = Color(0xFF3B4858)
internal val DarkOnSecondaryContainer = Color(0xFFD7E3F8)
internal val DarkTertiary = Color(0xFFD4BDE6)
internal val DarkOnTertiary = Color(0xFF392849)
internal val DarkTertiaryContainer = Color(0xFF503F61)
internal val DarkOnTertiaryContainer = Color(0xFFF0DBFF)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkBackground = Color(0xFF0F0F0F)
internal val DarkOnBackground = Color(0xFFE3E2E6)
internal val DarkSurface = Color(0xFF0F0F0F)
internal val DarkOnSurface = Color(0xFFE3E2E6)
internal val DarkSurfaceVariant = Color(0xFF44474E)
internal val DarkOnSurfaceVariant = Color(0xFFC4C6CF)
internal val DarkOutline = Color(0xFF8E9099)
internal val DarkOutlineVariant = Color(0xFF44474E)
internal val DarkSurfaceContainer = Color(0xFF1E1E1E)
internal val DarkSurfaceContainerHigh = Color(0xFF2B2B2B)
internal val DarkSurfaceContainerHighest = Color(0xFF363636)

// ---------------------------------------------------------------------------
// Summer theme (green palette) 🥒
// ---------------------------------------------------------------------------
internal val SummerPrimary = Color(0xFF43A047)
internal val SummerOnPrimary = Color(0xFFFFFFFF)
internal val SummerPrimaryContainer = Color(0xFFC8E6C9)
internal val SummerOnPrimaryContainer = Color(0xFF1B5E20)
internal val SummerSecondary = Color(0xFF66BB6A)
internal val SummerOnSecondary = Color(0xFFFFFFFF)
internal val SummerSecondaryContainer = Color(0xFFC8E6C9)
internal val SummerOnSecondaryContainer = Color(0xFF1B5E20)
internal val SummerTertiary = Color(0xFF81C784)
internal val SummerOnTertiary = Color(0xFFFFFFFF)
internal val SummerTertiaryContainer = Color(0xFFA5D6A7)
internal val SummerOnTertiaryContainer = Color(0xFF388E3C)
internal val SummerError = Color(0xFFB3261E)
internal val SummerOnError = Color(0xFFFFFFFF)
internal val SummerBackground = Color(0xFFFFFBF5)
internal val SummerOnBackground = Color(0xFF1B1B1F)
internal val SummerSurface = Color(0xFFFAFFF8)
internal val SummerOnSurface = Color(0xFF1B1B1F)
internal val SummerSurfaceVariant = Color(0xFFE0F2E9)
internal val SummerOnSurfaceVariant = Color(0xFF37474F)
internal val SummerOutline = Color(0xFF689F38)
internal val SummerOutlineVariant = Color(0xFFC5E1A5)
internal val SummerSurfaceContainer = Color(0xFFF1F8F4)
internal val SummerSurfaceContainerHigh = Color(0xFFE8F5E9)
internal val SummerSurfaceContainerHighest = Color(0xFFDCEDC8)

// ---------------------------------------------------------------------------
// Extra (non-MaterialColorScheme) tokens used by the power button
// ---------------------------------------------------------------------------
internal val PowerOff = Color(0xFF3A3A3A)
internal val PowerOn = Color(0xFF4CAF50)
internal val ClashStopped = Color(0xFF808080)
