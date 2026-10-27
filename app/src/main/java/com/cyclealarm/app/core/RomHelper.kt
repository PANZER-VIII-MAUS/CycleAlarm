package com.cyclealarm.app.core

import android.os.Build
import java.util.Locale

/**
 * 厂商/ROM 识别：用于权限引导页展示对应的自启动、电池、后台弹出界面等设置指引。
 * 各家设置页 Activity 经常随版本变动，跳转不稳定，因此这里只负责“识别 + 文案指引”，
 * 能稳定跳转的入口仍由 [PermissionHelper] 提供。
 */
object RomHelper {

    enum class Vendor(val displayName: String, val keywords: List<String>) {
        OPPO("OPPO/ColorOS", listOf("oppo", "realme", "oneplus")),
        XIAOMI("小米/HyperOS", listOf("xiaomi", "redmi", "poco")),
        VIVO("vivo/OriginOS", listOf("vivo", "iqoo")),
        HUAWEI("华为/MagicOS", listOf("huawei", "honor")),
        SAMSUNG("三星/One UI", listOf("samsung")),
        GOOGLE("Google/原生", listOf("google")),
        OTHER("其他厂商", emptyList());
    }

    val vendor: Vendor by lazy { detect() }

    private fun detect(): Vendor {
        val raw = listOf(Build.BRAND, Build.MANUFACTURER, Build.PRODUCT, Build.DEVICE)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return Vendor.entries.firstOrNull { v -> v.keywords.any { raw.contains(it) } }
            ?: Vendor.OTHER
    }

    /** 该 ROM 需要额外手动确认的设置项（权限引导页展示）。 */
    fun extraTips(): List<String> = when (vendor) {
        Vendor.OPPO -> listOf(
            "应用管理 → 循环闹钟 → 耗电管理 → 允许完全后台行为",
            "电池 → 耗电管理 → 允许完全后台行为；电池优化选“不优化”"
        )

        Vendor.XIAOMI -> listOf(
            "设置 → 应用设置 → 循环闹钟 → 省电策略 → 无限制",
            "应用管理 → 后台弹出界面 → 允许（否则响铃界面弹不出来）",
            "安全中心 → 应用管理 → 自启动 → 允许"
        )

        Vendor.VIVO -> listOf(
            "i管家 → 应用管理 → 权限管理 → 后台耗电 → 允许后台高耗电",
            "设置 → 电池 → 后台耗电管理 → 允许"
        )

        Vendor.HUAWEI -> listOf(
            "设置 → 应用 → 应用启动管理 → 手动管理 → 全部允许",
            "设置 → 电池 → 更多电池设置 → 休眠时始终保持网络连接"
        )

        Vendor.SAMSUNG -> listOf(
            "设置 → 电池 → 后台使用限制 → 不受限制",
            "设置 → 应用 → 循环闹钟 → 电池 → 不受限制"
        )

        else -> listOf(
            "系统设置 → 电池 → 电池优化 → 不优化",
            "系统设置 → 应用 → 自启动 / 后台活动 → 允许"
        )
    }
}
