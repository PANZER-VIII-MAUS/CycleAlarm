package com.cyclealarm.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 设计稿字号/字重。
 * 注意：这是**全局覆盖**——Material3 的 TopAppBar 标题、AlertDialog 标题、
 * TimePicker 内部数字等都会跟着变化，属预期效果。
 */
val CycleAlarmTypography = Typography(
    // 顶栏标题 / 对话框标题：20sp SemiBold（对话框标题使用时再 Bold）
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    // 卡片「第 N 天」：18sp Medium
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    // Material3 AlertDialog 标题用的是 headlineSmall：设计稿要求 20sp Bold
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    // 卡片时间：30sp Bold
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    // 卡片副标题 / 说明：15sp
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    // 对话框说明：14sp
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    // 底部主按钮 / 保存：17sp SemiBold
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    // 「删除」：14sp Medium
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    // 对话框超大时间：54sp Bold
    displayMedium = TextStyle(fontSize = 54.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold)
)
