package br.com.unhasdequecor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightNeutral,
    onPrimaryContainer = LightHighlight,
    secondary = LightSecondary,
    onSecondary = LightOnPrimary,
    secondaryContainer = Color(0xFFF3D7EC),
    onSecondaryContainer = LightHighlight,
    tertiary = LightSecondary,
    background = LightBase,
    onBackground = LightHighlight,
    surface = LightSurface,
    onSurface = LightHighlight,
    surfaceVariant = LightNeutral,
    onSurfaceVariant = Color(0xFF5C3D48),
    outline = LightOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkCard,
    onPrimaryContainer = DarkSecondary,
    secondary = DarkSecondary,
    onSecondary = DarkBase,
    secondaryContainer = DarkCard,
    onSecondaryContainer = DarkSecondary,
    tertiary = DarkSecondary,
    background = DarkBase,
    onBackground = DarkHighlight,
    surface = DarkSurface,
    onSurface = DarkHighlight,
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFE8D0DA),
    outline = DarkOutline,
)

@Composable
fun UnhasDeQueCorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
