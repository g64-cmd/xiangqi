package com.xiangqi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Cinnabar,
    onPrimary = PaperCream,
    primaryContainer = CinnabarLight,
    onPrimaryContainer = InkBlack,
    secondary = WoodDark,
    onSecondary = PaperCream,
    tertiary = InkGray,
    background = PaperCream,
    onBackground = InkBlack,
    surface = WoodLight,
    onSurface = InkBlack,
)

private val DarkColorScheme = darkColorScheme(
    primary = CinnabarLight,
    onPrimary = InkBlack,
    primaryContainer = Cinnabar,
    onPrimaryContainer = PaperCream,
    secondary = WoodMid,
    onSecondary = InkBlack,
    tertiary = WoodLight,
    background = InkBlack,
    onBackground = PaperCream,
    surface = InkGray,
    onSurface = PaperCream,
)

@Composable
fun XiangqiTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
