package com.cyclealarm.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 统一配色的开关。
 *
 * 系统默认的未选中轨道在浅色主题下与白底的对比度只有约 1.36:1，
 * 低于 WCAG 1.4.11 要求的 3:1，导致「开/关」状态看不清。这里显式指定更深的轨道色。
 */
@Composable
fun CycleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors? = null
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val uncheckedTrack = if (dark) Color(0xFF5A6270) else Color(0xFF8A93A0)
    val effectiveColors = colors ?: SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = if (dark) Color(0xFFD6DBE1) else Color(0xFFFFFFFF),
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = uncheckedTrack
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = effectiveColors
    )
}
