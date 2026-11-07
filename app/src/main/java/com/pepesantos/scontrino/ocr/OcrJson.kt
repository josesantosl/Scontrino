package com.pepesantos.scontrino.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes an OCR run so real receipts can be collected as test fixtures
 * for the future receipt parser.
 */
object OcrJson {
    const val FORMAT_VERSION = 1

    fun toJson(result: OcrResult, rows: List<ReceiptRow>): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("languages", JSONArray(result.languages.map { it.tesseractCode }))
        put("imageWidth", result.imageWidth)
        put("imageHeight", result.imageHeight)
        put("meanConfidence", result.meanConfidence)
        put("rows", JSONArray(rows.map { row ->
            JSONObject().apply {
                put("text", row.text)
                put("segments", JSONArray(row.segments))
                put("box", row.box.toJson())
            }
        }))
        put("words", JSONArray(result.words.map { word ->
            JSONObject().apply {
                put("text", word.text)
                put("confidence", word.confidence.toDouble())
                put("box", word.box.toJson())
                word.line?.let { put("line", it) }
            }
        }))
        put("rawText", result.rawText)
    }.toString(2)

    private fun BoundingBox.toJson() = JSONArray(listOf(left, top, right, bottom))
}
