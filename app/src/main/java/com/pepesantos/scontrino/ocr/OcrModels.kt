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

/**
 * Languages bundled in assets/tessdata (tessdata_fast).
 *
 * TODO(chinese-ocr): Chinese receipts (chi_sim) are deliberately not supported yet,
 *  waiting for real receipts to build fixtures from. Besides bundling
 *  chi_sim.traineddata and adding CHINESE_SIMPLIFIED("chi_sim", "zh"):
 *  - ReceiptParser joins name words with spaces ("牛 奶"): join CJK characters without one;
 *  - StoreMatcher.tokens splits on [^A-Z0-9]+, which drops Chinese store names entirely;
 *  - parser keywords: totals 合计/总计/应付/实付, change 找零, payments 现金/微信/支付宝,
 *    header 商品名称/数量/单价/金额, discounts 优惠/折扣, tax id 统一社会信用代码/税号 (18 chars);
 *  - dates like "2026年11月21日" in ReceiptDateParser;
 *  - Chinese keywords in CategoryMatcher;
 *  - expect slower and weaker recognition than Latin scripts on small thermal print.
 */
enum class OcrLanguage(val tesseractCode: String, val isoCode: String) {
    ENGLISH("eng", "en"),
    ITALIAN("ita", "it"),
    SPANISH("spa", "es"),
    FRENCH("fra", "fr"),
    GERMAN("deu", "de"),
    PORTUGUESE("por", "pt");

    companion object {
        /**
         * The language of each locale (e.g. the app's, then the system's), if bundled, then
         * English as a fallback, since receipts often mix in English words and units.
         * Only languages: the region is deliberately ignored, the user picks the receipt
         * languages in the settings if they shop in another one.
         */
        fun defaultFor(vararg locales: Locale): List<OcrLanguage> {
            val fromLanguage = locales.mapNotNull { locale -> entries.firstOrNull { it.isoCode == locale.language } }
            return (fromLanguage + ENGLISH).distinct()
        }

        /**
         * Languages from their Tesseract codes, in order, skipping unknown ones; [fallback]
         * if none is left (nothing chosen yet).
         */
        fun fromCodes(codes: List<String>, fallback: () -> List<OcrLanguage>): List<OcrLanguage> =
            codes.mapNotNull { code -> entries.firstOrNull { it.tesseractCode == code } }
                .distinct()
                .ifEmpty(fallback)
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
