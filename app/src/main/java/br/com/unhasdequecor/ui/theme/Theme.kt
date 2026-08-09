package br.com.unhasdequecor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrandAction,
    onPrimary = BrandOnAction,
    primaryContainer = BrandSoftSurface,
    onPrimaryContainer = BrandInk,
    secondary = BrandFun,
    onSecondary = BrandInk,
    secondaryContainer = BrandSoftSurface,
    onSecondaryContainer = BrandInk,
    tertiary = BrandFun,
    onTertiary = BrandInk,
    background = BrandBase,
    onBackground = BrandInk,
    surface = BrandCard,
    onSurface = BrandInk,
    surfaceVariant = BrandSoftSurface,
    onSurfaceVariant = BrandInk.copy(alpha = 0.72f),
    outline = BrandOutline,
    outlineVariant = BrandSoftSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAction,
    onPrimary = DarkOnAction,
    primaryContainer = DarkSoftSurface,
    onPrimaryContainer = DarkInk,
    secondary = DarkFun,
    onSecondary = DarkBase,
    secondaryContainer = DarkSoftSurface,
    onSecondaryContainer = DarkInk,
    tertiary = DarkFun,
    onTertiary = DarkBase,
    background = DarkBase,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkSoftSurface,
    onSurfaceVariant = DarkInk.copy(alpha = 0.78f),
    outline = DarkOutline,
    outlineVariant = DarkSoftSurface,
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
        shapes = BrandShapes,
        content = content,
    )
}
