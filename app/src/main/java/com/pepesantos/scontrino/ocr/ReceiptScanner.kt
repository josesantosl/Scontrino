package com.pepesantos.scontrino.ocr

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.pepesantos.scontrino.data.AppDatabase
import com.pepesantos.scontrino.data.settings.SettingsRepository
import com.pepesantos.scontrino.ocr.draft.ProductNames
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The whole receipt scanning pipeline: photo → preprocessed image → OCR in one or
 * more layout modes → rows → parsed receipt, keeping the best reading.
 *
 * Shared by the scan feature and the OCR lab screen, so both always read receipts
 * the same way.
 *
 * @param knownNames the user's product names, normalized with [ProductNames.normalize];
 *   only loaded when several readings have to be compared. Defaults to the database.
 */
class ReceiptScanner(
    context: Context,
    private val recognizer: ReceiptTextRecognizer = TesseractTextRecognizer(context),
    private val preprocessor: ReceiptImagePreprocessor = ReceiptImagePreprocessor(),
    knownNames: (suspend () -> Set<String>)? = null,
) {
    private val appContext = context.applicationContext

    private val knownNames: suspend () -> Set<String> = knownNames ?: {
        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(appContext).productDao().getAll()
                .map { ProductNames.normalize(it.name) }
                .toSet()
        }
    }

    data class Options(
        /** Ordered: the first language is Tesseract's primary one. */
        val languages: List<OcrLanguage> = defaultLanguages(),
        val preprocessing: ReceiptImagePreprocessor.Options = ReceiptImagePreprocessor.Options(),
        // AUTO's column analysis read Bennet's price column as vertical text, even with
        // vertical detection disabled; a single column is what a receipt is.
        val pageSegMode: OcrPageSegMode = OcrPageSegMode.SINGLE_COLUMN,
        /** If the chosen mode's reading does not add up, try the other modes too. */
        val tryOtherModes: Boolean = true,
    )

    data class Result(
        val processed: ReceiptImagePreprocessor.Result,
        /** Every mode tried, in order. */
        val attempts: List<LayoutAttempt>,
        val best: LayoutAttempt,
        /** The names used to choose [best]; empty if only one mode was tried. */
        val knownNames: Set<String>,
    ) {
        val parsed: ParsedReceipt get() = best.parsed
    }

    /**
     * Reads the receipt in the photo at [uri].
     *
     * @param onPreprocessed called as soon as the cleaned-up image is ready, before
     *   the (slow) OCR passes, e.g. to show it while waiting.
     */
    suspend fun scan(
        uri: Uri,
        options: Options = Options(),
        onPreprocessed: (ReceiptImagePreprocessor.Result) -> Unit = {},
    ): Result {
        require(options.languages.isNotEmpty()) { "At least one OCR language is required" }

        val processed = withContext(Dispatchers.Default) {
            val original = ReceiptImageLoader.load(appContext, uri)
            preprocessor.process(original, options.preprocessing).also { original.recycle() }
        }
        onPreprocessed(processed)

        suspend fun attempt(mode: OcrPageSegMode): LayoutAttempt {
            val result = recognizer.recognize(processed.bitmap, options.languages, mode)
            return withContext(Dispatchers.Default) { LayoutAttempt.of(mode, result) }
        }

        // The chosen mode first; the others only if its reading does not add up,
        // so a receipt that is read well costs a single OCR pass.
        val attempts = mutableListOf(attempt(options.pageSegMode))
        if (options.tryOtherModes && !LayoutChoice.isGoodEnough(attempts.first())) {
            for (mode in LayoutChoice.fallbacksAfter(options.pageSegMode)) {
                attempts += attempt(mode)
            }
        }
        // Only needed to choose among several readings.
        val names = if (attempts.size > 1) knownNames() else emptySet()
        return Result(processed, attempts, LayoutChoice.best(attempts, names), names)
    }

    companion object {
        /**
         * When the user hasn't chosen: the app language (the default locale, see AppLocale),
         * the system language, and English.
         */
        fun defaultLanguages(): List<OcrLanguage> =
            OcrLanguage.defaultFor(Locale.getDefault(), Resources.getSystem().configuration.locales[0])

        /** The receipt languages chosen in the settings, or [defaultLanguages]. */
        fun languagesFromSettings(context: Context): List<OcrLanguage> =
            OcrLanguage.fromCodes(SettingsRepository.storedReceiptLanguages(context), ::defaultLanguages)
    }
}
