package com.shiyin.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shiyin.player.data.AppStore

val Pink = Color(0xFFD33A31)

private val DarkScheme = darkColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    secondary = Color(0xFFE05C52),
    onSecondary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF9E9E9E),
    error = Color(0xFFE57373),
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC22E25),
    onPrimary = Color.White,
    secondary = Color(0xFFD33A31),
    onSecondary = Color.White,
    background = Color(0xFFF6F6F6),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF6E6E6E),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** 深色/浅色主题由设置页切换(设置 → 主题),默认深色 */
@Composable
fun ShiyinTheme(content: @Composable () -> Unit) {
    val dark = AppStore.settings.value.theme != "light"
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}

/** 当前是否深色主题(玻璃面等自定义绘制用) */
@Composable
fun isDarkTheme(): Boolean = AppStore.settings.value.theme != "light"
