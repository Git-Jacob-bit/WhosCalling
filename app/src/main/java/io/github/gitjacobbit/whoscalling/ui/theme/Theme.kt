package io.github.gitjacobbit.whoscalling.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val Colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.TextPrimary,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    outline = Palette.Outline,
    error = Palette.Reject,
)

@Composable
fun WhosCallingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        // Text outside a Surface/Scaffold would otherwise fall back to black on the dark background.
        CompositionLocalProvider(LocalContentColor provides Palette.TextPrimary, content = content)
    }
}
