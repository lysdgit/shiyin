package com.shiyin.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pink = Color(0xFFD33A31)
val Bg = Color(0xFF121212)
val Surface1 = Color(0xFF1E1E1E)
val Surface2 = Color(0xFF2A2A2A)
val SubText = Color(0xFF9E9E9E)
val OnText = Color(0xFFEAEAEA)

@Composable
fun ShiyinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pink,
            onPrimary = Color.White,
            secondary = Color(0xFFE05C52),
            onSecondary = Color.White,
            background = Bg,
            onBackground = OnText,
            surface = Surface1,
            onSurface = OnText,
            surfaceVariant = Surface2,
            onSurfaceVariant = SubText,
            error = Color(0xFFE57373),
            onError = Color.White,
        ),
        content = content,
    )
}
