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
    /**
     * Index of the text line the engine put this word in (0 = first line), or null
     * if unknown (e.g. older fixtures).
     */
    val line: Int? = null,
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

/**
 * Tesseract page segmentation modes worth comparing on receipts.
 *
 * @property fullWidthLines whether the engine's text lines span the whole receipt
 * width (name ... price). Then they are a better row grouping than geometry alone,
 * as the engine tolerates residual skew and curved paper. In [AUTO] and [SPARSE]
 * lines are split by column, so rows must be rebuilt from geometry.
 */
enum class OcrPageSegMode(val tesseractValue: Int, val fullWidthLines: Boolean) {
    AUTO(3, fullWidthLines = false),
    /**
     * One column of lines with variable font sizes: matches a receipt and skips the
     * column analysis of [AUTO], which sometimes reads the stacked price column as
     * vertical text.
     */
    SINGLE_COLUMN(4, fullWidthLines = true),
    SINGLE_BLOCK(6, fullWidthLines = true),
    SPARSE(11, fullWidthLines = false),
}
