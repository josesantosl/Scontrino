package com.pepesantos.scontrino.ocr

import org.json.JSONObject

/**
 * Loads real receipts exported from the OCR debug screen ("Copy JSON") and saved
 * under src/test/resources/ocr/. Only the words are needed: rows are rebuilt by
 * the code under test.
 */
object OcrFixtures {

    fun words(name: String): List<OcrWord> {
        val stream = javaClass.classLoader!!.getResourceAsStream("ocr/$name.json")
            ?: error("Fixture not found: ocr/$name.json")
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        val words = json.getJSONArray("words")
        return (0 until words.length()).map { i ->
            val word = words.getJSONObject(i)
            val box = word.getJSONArray("box")
            OcrWord(
                text = word.getString("text"),
                box = BoundingBox(box.getInt(0), box.getInt(1), box.getInt(2), box.getInt(3)),
                confidence = word.getDouble("confidence").toFloat(),
                line = if (word.has("line")) word.getInt("line") else null,
            )
        }
    }
}
