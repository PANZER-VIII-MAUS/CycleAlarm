package com.cyclealarm.app.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言设置（跟随系统 / 中文 / English）。
 *
 * 不引入 appcompat：通过给每个 Activity 的 base context 套一层配置来实现语言切换，
 * Compose 的字符串资源会跟着 Activity 的 configuration 走。
 * 通知/服务里的文案仍按系统语言，这是本方案的已知边界。
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    private const val PREFS = "app_prefs"
    private const val KEY = "app_language"

    fun get(context: Context): String =
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, SYSTEM)
        }.getOrNull() ?: SYSTEM

    fun set(context: Context, language: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language)
            .apply()
    }

    /** 语言代码 → Locale；跟随系统返回 null。 */
    fun localeFor(language: String): Locale? = when (language) {
        ZH -> Locale.SIMPLIFIED_CHINESE
        EN -> Locale.ENGLISH
        else -> null
    }

    fun wrap(base: Context): Context {
        val locale = localeFor(get(base)) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
