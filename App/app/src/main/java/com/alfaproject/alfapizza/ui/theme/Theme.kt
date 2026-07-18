package com.alfaproject.alfapizza.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColors(
    primary = BoxColor,
    primaryVariant = BoxColor,
    secondary = BoxColor,
    secondaryVariant = BoxColor,
    background = BackgroundColor,
    surface = MyTextFieldColor,
    onPrimary = White,
    onSecondary = White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun AlfaPizzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
