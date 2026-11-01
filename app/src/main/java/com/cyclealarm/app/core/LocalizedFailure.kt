package com.cyclealarm.app.core

import android.content.Context

/**
 * 带资源 id 的异常：数据层只抛「错误码」，界面层按当前语言解析，避免把界面语言写进数据层。
 */
class LocalizedFailure(val messageRes: Int, vararg val args: Any) : Exception() {
    override val message: String
        get() = "localized#" + messageRes + ":" + args.joinToString(",")
}

/** 把任意异常转成当前语言的可读文案。 */
fun Throwable.uiMessage(context: Context): String = when (this) {
    is LocalizedFailure -> runCatching {
        context.getString(messageRes, *args)
    }.getOrElse { context.getString(messageRes) }
    else -> message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}
