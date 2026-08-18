package com.remotedesktop.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0F172A)
val CardBg = Color(0xFF1E293B)
val PrimaryBlue = Color(0xFF38BDF8)
val AccentGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentGreen,
    background = DarkBg,
    surface = CardBg,
    error = ErrorRed,
    onPrimary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun RemoteDesktopTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
