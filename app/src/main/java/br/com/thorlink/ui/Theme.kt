package br.com.thorlink.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFAD87FF)
val DisplayBg = Color(0xFF090A10)
val Cream = Color(0xFFD8D1CA)
@Composable fun ThorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Color(0xFF70DAD5),
        background = DisplayBg, surface = Color(0xFF14141E), onSurface = Color(0xFFEEEEF3))) {
        Surface(color=DisplayBg,contentColor=MaterialTheme.colorScheme.onSurface) { content() }
    }
}
