package com.pepesantos.scontrino.ocr

import java.util.Locale

/**
 * Engine-independent OCR models. Kept free of Android types so the row
 * reconstruction and (later) the receipt parser can be unit tested on the JVM.
 */
data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerY: Double get() = (top + bottom) / 2.0

    fun union(other: BoundingBox) = BoundingBox(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )
}

data class OcrWord(
    val text: String,
    val box: BoundingBox,
    /** 0..100, as reported by the engine. */
    val confidence: Float,
)

data class OcrResult(
    /** Size of the (preprocessed) image the boxes refer to. */
    val imageWidth: Int,
    val imageHeight: Int,
    val words: List<OcrWord>,
    /** Plain text exactly as returned by the engine, useful for comparison. */
    val rawText: String,
    val languages: List<OcrLanguage>,
    val durationMs: Long,
) {
    val meanConfidence: Int
        get() = if (words.isEmpty()) 0 else words.map { it.confidence }.average().toInt()
}

/** Languages bundled in assets/tessdata (tessdata_fast). */
enum class OcrLanguage(val tesseractCode: String, val isoCode: String) {
    ENGLISH("eng", "en"),
    ITALIAN("ita", "it"),
    SPANISH("spa", "es"),
    FRENCH("fra", "fr"),
    GERMAN("deu", "de");

    companion object {
        /**
         * The device language first (if bundled), then English as a fallback,
         * since receipts often mix in English words and units.
         */
        fun defaultFor(locale: Locale): List<OcrLanguage> {
            val primary = entries.firstOrNull { it.isoCode == locale.language }
            return listOfNotNull(primary, ENGLISH).distinct()
        }
    }
}

/** Tesseract page segmentation modes worth comparing on receipts. */
enum class OcrPageSegMode(val tesseractValue: Int) {
    AUTO(3),
    SINGLE_BLOCK(6),
    SPARSE(11),
}
