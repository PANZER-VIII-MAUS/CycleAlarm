package com.cyclealarm.app.core

import android.content.Context
import android.os.Build
import com.cyclealarm.app.R
import java.util.Locale

/**
 * 厂商/ROM 识别：用于权限引导页展示对应的自启动、电池、后台弹出界面等设置指引。
 * 各家设置页 Activity 经常随版本变动，跳转不稳定，因此这里只负责“识别 + 文案指引”，
 * 能稳定跳转的入口仍由 [PermissionHelper] 提供。
 */
object RomHelper {

    enum class Vendor(
        val displayRes: Int,
        val tipRes: List<Int>,
        val keywords: List<String>
    ) {
        OPPO(
            R.string.rom_oppo,
            listOf(R.string.rom_oppo_tip1, R.string.rom_oppo_tip2),
            listOf("oppo", "realme", "oneplus")
        ),
        XIAOMI(
            R.string.rom_xiaomi,
            listOf(R.string.rom_xiaomi_tip1, R.string.rom_xiaomi_tip2, R.string.rom_xiaomi_tip3),
            listOf("xiaomi", "redmi", "poco")
        ),
        VIVO(
            R.string.rom_vivo,
            listOf(R.string.rom_vivo_tip1, R.string.rom_vivo_tip2),
            listOf("vivo", "iqoo")
        ),
        HUAWEI(
            R.string.rom_huawei,
            listOf(R.string.rom_huawei_tip1, R.string.rom_huawei_tip2),
            listOf("huawei", "honor")
        ),
        SAMSUNG(
            R.string.rom_samsung,
            listOf(R.string.rom_samsung_tip1, R.string.rom_samsung_tip2),
            listOf("samsung")
        ),
        GOOGLE(
            R.string.rom_google,
            listOf(R.string.rom_other_tip1, R.string.rom_other_tip2),
            listOf("google")
        ),
        OTHER(
            R.string.rom_other,
            listOf(R.string.rom_other_tip1, R.string.rom_other_tip2),
            emptyList()
        );
    }

    val vendor: Vendor by lazy { detect() }

    private fun detect(): Vendor {
        val raw = listOf(Build.BRAND, Build.MANUFACTURER, Build.PRODUCT, Build.DEVICE)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return Vendor.entries.firstOrNull { v -> v.keywords.any { raw.contains(it) } }
            ?: Vendor.OTHER
    }

    /** 厂商显示名（按当前界面语言）。 */
    fun displayName(context: Context): String = context.getString(vendor.displayRes)

    /** 该 ROM 需要额外手动确认的设置项（权限引导页展示）。 */
    fun extraTips(context: Context): List<String> = vendor.tipRes.map { context.getString(it) }
}
