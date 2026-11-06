package com.pepesantos.scontrino.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.ocr.OcrJson
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.OcrPageSegMode
import com.pepesantos.scontrino.ocr.OcrResult
import com.pepesantos.scontrino.ocr.ReceiptImageLoader
import com.pepesantos.scontrino.ocr.ReceiptImagePreprocessor
import com.pepesantos.scontrino.ocr.ReceiptRow
import com.pepesantos.scontrino.ocr.ReceiptRowBuilder
import com.pepesantos.scontrino.ocr.ReceiptTextRecognizer
import com.pepesantos.scontrino.ocr.TesseractTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

data class OcrDebugUiState(
    val sourceUri: Uri? = null,
    /** Ordered: the first language is Tesseract's primary one. */
    val languages: List<OcrLanguage> = OcrLanguage.defaultFor(Locale.getDefault()),
    val detectDocument: Boolean = true,
    val deskew: Boolean = true,
    val binarize: Boolean = true,
    // AUTO handled the separate name/price columns best on the first real receipt.
    val pageSegMode: OcrPageSegMode = OcrPageSegMode.AUTO,
    val isProcessing: Boolean = false,
    val processedImage: Bitmap? = null,
    val detection: ReceiptImagePreprocessor.Detection? = null,
    val skewAngle: Double? = null,
    val result: OcrResult? = null,
    val rows: List<ReceiptRow> = emptyList(),
    val error: String? = null,
) {
    val canRun: Boolean get() = sourceUri != null && languages.isNotEmpty() && !isProcessing
}

/**
 * Lab screen state for phase 1 of receipt scanning: photo → preprocessed image →
 * OCR words → reconstructed rows. No data is persisted.
 */
class OcrDebugViewModel @JvmOverloads constructor(
    application: Application,
    private val recognizer: ReceiptTextRecognizer = TesseractTextRecognizer(application),
    private val preprocessor: ReceiptImagePreprocessor = ReceiptImagePreprocessor(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OcrDebugUiState())
    val uiState: StateFlow<OcrDebugUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun onImageSelected(uri: Uri) {
        runJob?.cancel()
        _uiState.update {
            it.copy(
                sourceUri = uri, isProcessing = false, processedImage = null,
                detection = null, skewAngle = null, result = null, rows = emptyList(), error = null,
            )
        }
        run()
    }

    fun toggleLanguage(language: OcrLanguage) = _uiState.update {
        val languages = if (language in it.languages) it.languages - language else it.languages + language
        it.copy(languages = languages)
    }

    fun setDetectDocument(enabled: Boolean) = _uiState.update { it.copy(detectDocument = enabled) }
    fun setDeskew(enabled: Boolean) = _uiState.update { it.copy(deskew = enabled) }
    fun setBinarize(enabled: Boolean) = _uiState.update { it.copy(binarize = enabled) }
    fun setPageSegMode(mode: OcrPageSegMode) = _uiState.update { it.copy(pageSegMode = mode) }

    fun run() {
        val state = _uiState.value
        val uri = state.sourceUri ?: return
        if (!state.canRun) return

        runJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val processed = withContext(Dispatchers.Default) {
                    val original = ReceiptImageLoader.load(getApplication(), uri)
                    preprocessor.process(
                        original,
                        ReceiptImagePreprocessor.Options(
                            detectDocument = state.detectDocument,
                            deskew = state.deskew,
                            binarize = state.binarize,
                        )
                    ).also { original.recycle() }
                }
                _uiState.update {
                    it.copy(
                        processedImage = processed.bitmap,
                        detection = processed.detection,
                        skewAngle = processed.skewAngle,
                    )
                }

                val result = recognizer.recognize(processed.bitmap, state.languages, state.pageSegMode)
                val rows = withContext(Dispatchers.Default) { ReceiptRowBuilder.build(result.words) }
                _uiState.update { it.copy(result = result, rows = rows, isProcessing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun exportJson(): String? {
        val state = _uiState.value
        val result = state.result ?: return null
        return OcrJson.toJson(result, state.rows)
    }

    private companion object {
        const val TAG = "OcrDebugViewModel"
    }
}
