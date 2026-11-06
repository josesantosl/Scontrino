package com.pepesantos.scontrino.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** OCR engine abstraction, so Tesseract can be swapped (e.g. for PaddleOCR) later. */
interface ReceiptTextRecognizer {
    suspend fun recognize(
        bitmap: Bitmap,
        languages: List<OcrLanguage>,
        pageSegMode: OcrPageSegMode = OcrPageSegMode.SINGLE_BLOCK,
    ): OcrResult
}

/**
 * Fully offline Tesseract implementation. The bundled tessdata_fast models are
 * copied from assets to internal storage on first use, since Tesseract needs
 * real files.
 */
class TesseractTextRecognizer(context: Context) : ReceiptTextRecognizer {

    private val appContext = context.applicationContext
    private val dataDir = File(appContext.noBackupFilesDir, "tesseract")

    // TessBaseAPI is not thread-safe and is memory hungry: one recognition at a time.
    private val mutex = Mutex()

    override suspend fun recognize(
        bitmap: Bitmap,
        languages: List<OcrLanguage>,
        pageSegMode: OcrPageSegMode,
    ): OcrResult = mutex.withLock {
        require(languages.isNotEmpty()) { "At least one OCR language is required" }
        withContext(Dispatchers.IO) { ensureTrainedData(languages) }

        withContext(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            val api = TessBaseAPI()
            try {
                val langCodes = languages.joinToString("+") { it.tesseractCode }
                if (!api.init(dataDir.absolutePath, langCodes)) {
                    throw IOException("Tesseract init failed for $langCodes")
                }
                api.pageSegMode = pageSegMode.tesseractValue
                api.setVariable("preserve_interword_spaces", "1")
                api.setImage(bitmap)

                val rawText = api.getUTF8Text().orEmpty() // runs the recognition
                OcrResult(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    words = readWords(api),
                    rawText = rawText,
                    languages = languages,
                    durationMs = System.currentTimeMillis() - start,
                )
            } finally {
                api.recycle()
            }
        }
    }

    private fun readWords(api: TessBaseAPI): List<OcrWord> {
        val iterator = api.resultIterator ?: return emptyList()
        val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
        val words = mutableListOf<OcrWord>()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(level)?.trim()
                if (text.isNullOrEmpty()) continue
                val rect = iterator.getBoundingRect(level)
                words += OcrWord(
                    text = text,
                    box = BoundingBox(rect.left, rect.top, rect.right, rect.bottom),
                    confidence = iterator.confidence(level),
                )
            } while (iterator.next(level))
        } finally {
            iterator.delete()
        }
        return words
    }

    private fun ensureTrainedData(languages: List<OcrLanguage>) {
        val tessdata = File(dataDir, "tessdata").apply { mkdirs() }
        for (language in languages) {
            val fileName = "${language.tesseractCode}.traineddata"
            val target = File(tessdata, fileName)
            if (target.length() > 0) continue
            // Copy to a temp file first so an interrupted copy never looks valid.
            val temp = File(tessdata, "$fileName.tmp")
            appContext.assets.open("tessdata/$fileName").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) throw IOException("Cannot install $fileName")
        }
    }
}
