package com.zilv.clock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF166534), secondary = Color(0xFF2563EB), tertiary = Color(0xFFEA580C),
    background = Color(0xFFF7F8F4), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8F3E8),
)
private val Dark = darkColorScheme(primary = Color(0xFF86EFAC), secondary = Color(0xFF93C5FD), tertiary = Color(0xFFFDBA74))

@Composable fun ClockTheme(dark: Boolean = false, content: @Composable () -> Unit) = MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
