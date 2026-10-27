package com.cyclealarm.app.importer

import java.io.InputStream

/** 表格读取器：xlsx / csv 各自实现。 */
interface SheetReader {
    fun read(input: InputStream, sheetName: String? = null): List<Sheet>
}

/** 解析失败时抛出的异常，message 必须是给用户看的中文原因。 */
class SheetReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
