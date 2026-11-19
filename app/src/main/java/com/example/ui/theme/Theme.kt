package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AirTouchDarkColorScheme = darkColorScheme(
    primary = CobaltAccent,
    onPrimary = Color.White,
    primaryContainer = CobaltTint,
    onPrimaryContainer = CobaltDark,
    secondary = SlateMuted,
    onSecondary = Color.White,
    secondaryContainer = SlateWash,
    onSecondaryContainer = SlateInk,
    tertiary = EmeraldOnline,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldTint,
    onTertiaryContainer = EmeraldInk,
    background = AlabasterCanvas,
    onBackground = SlateInk,
    surface = PureSurface,
    onSurface = SlateInk,
    surfaceVariant = SlateWash,
    onSurfaceVariant = SlateMuted,
    outline = SlateBorder,
    outlineVariant = SlateSurfaceDim,
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0B0F17),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralTint,
    onErrorContainer = CoralInk,
)

private val AirTouchShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirTouchTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AirTouchDarkColorScheme,
        typography = AirTouchTypography,
        shapes = AirTouchShapes,
        content = content,
    )
}
