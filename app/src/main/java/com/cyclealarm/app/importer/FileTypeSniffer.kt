package com.cyclealarm.app.importer

/** 文件真实类型（看文件头，不看扩展名）。 */
enum class FileKind { XLSX, HTML_TABLE, UNSUPPORTED }

/**
 * 文件头嗅探：教务系统经常把 HTML 表格导出成 .xls，必须提前识别出来，
 * 否则按 zip 解析会直接崩。
 */
object FileTypeSniffer {
    fun sniff(head: ByteArray): FileKind {
        if (head.size >= 4 &&
            head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        ) {
            return FileKind.XLSX
        }
        // 用 ISO-8859-1 逐字节映射成字符串，够用于判断 ASCII 标签
        val text = String(head, Charsets.ISO_8859_1)
            .removePrefix("\uFEFF")
            .trimStart(' ', '\t', '\r', '\n')
            .lowercase()
            .take(256)
        val looksHtml = text.startsWith("<html") ||
            text.startsWith("<!doctype") ||
            text.startsWith("<table") ||
            text.startsWith("<?xml") ||
            text.contains("<html")
        return if (looksHtml) FileKind.HTML_TABLE else FileKind.UNSUPPORTED
    }
}
