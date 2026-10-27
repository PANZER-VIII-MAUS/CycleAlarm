package com.cyclealarm.app.importer.image

import com.cyclealarm.app.importer.ImportedEntry

/**
 * 图片识别接口（P1 才会实现）。
 * 本期只留接口，便于以后接 OCR / 云端多模态识别，且不影响 Excel/CSV 主链路。
 */
interface ScheduleImageRecognizer {
    suspend fun recognize(bitmap: android.graphics.Bitmap): List<ImportedEntry>
}
