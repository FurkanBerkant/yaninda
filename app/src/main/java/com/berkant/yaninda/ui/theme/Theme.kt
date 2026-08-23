package com.berkant.yaninda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val YanindaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = NightBlue,
    onPrimary = OnNightBlue,
    primaryContainer = NightSurfaceVariant,
    onPrimaryContainer = NightText,
    secondary = NightBrown,
    onSecondary = OnNightBrown,
    secondaryContainer = NightSurfaceVariant,
    onSecondaryContainer = NightText,
    tertiary = NightGreen,
    onTertiary = OnNightGreen,
    tertiaryContainer = NightGreenContainer,
    onTertiaryContainer = OnNightGreenContainer,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightTextMuted,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    error = NightAlarm,
    onError = OnNightAlarm,
    errorContainer = NightAlarmContainer,
    onErrorContainer = OnNightAlarmContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepBlue,
    onPrimary = OnDeepBlue,
    primaryContainer = PaleBlue,
    onPrimaryContainer = OnPaleBlue,
    secondary = WarmBrown,
    onSecondary = OnWarmBrown,
    secondaryContainer = PaleSecondary,
    onSecondaryContainer = OnPaleSecondary,
    tertiary = SuccessGreen,
    onTertiary = OnSuccessGreen,
    tertiaryContainer = PaleGreen,
    onTertiaryContainer = OnPaleGreen,
    background = WarmBackground,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = StrongOutline,
    outlineVariant = SoftOutline,
    error = AlarmRust,
    onError = OnAlarmRust,
    errorContainer = PaleAlarm,
    onErrorContainer = OnPaleAlarm,
)

@Composable
fun YanindaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = YanindaShapes,
        content = content
    )
}
