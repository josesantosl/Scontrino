package com.pepesantos.scontrino.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.scontrino.data.settings.SettingsRepository
import com.pepesantos.scontrino.ocr.OcrLanguage
import com.pepesantos.scontrino.ocr.ReceiptScanner
import com.pepesantos.scontrino.ocr.ScannedReceiptImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Steps of scanning a receipt, from the camera to the review screen. */
sealed interface ScanState {
    /** Waiting for a photo (camera open). */
    data object Idle : ScanState

    /** Reading the photo; [preview] is the cleaned-up receipt once ready. */
    data class Processing(val preview: Bitmap? = null) : ScanState

    /** Read, with items: to be reviewed, with the image it was read from. */
    data class Done(val parsed: ParsedReceipt, val image: ScannedReceiptImage) : ScanState

    /** Read, but no item was found on it: usually a bad photo, or the wrong languages. */
    data object NothingFound : ScanState

    data class Failed(val message: String) : ScanState
}

/**
 * The scan feature: a photo goes through [ReceiptScanner] with the default pipeline and
 * the receipt languages from the settings. No lab knobs; those live in [OcrDebugViewModel].
 */
class ScanReceiptViewModel @JvmOverloads constructor(
    application: Application,
    private val scanner: ReceiptScanner = ReceiptScanner(application),
    private val settings: SettingsRepository = SettingsRepository.getInstance(application),
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Languages the photo is read in: the ones in the settings, or the default ones. */
    val languages: StateFlow<List<OcrLanguage>> = settings.receiptLanguages
        .map(::resolve)
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(settings.receiptLanguages.value))

    private var job: Job? = null

    /** The photo being read or last read, to read it again in other languages. */
    private var lastUri: Uri? = null

    fun scan(uri: Uri) {
        job?.cancel()
        lastUri = uri
        _state.value = ScanState.Processing()
        job = viewModelScope.launch {
            try {
                // Read at each scan, so a change in the settings applies right away.
                val options = ReceiptScanner.Options(languages = resolve(settings.receiptLanguages.value))
                val result = scanner.scan(uri, options) { processed ->
                    _state.value = ScanState.Processing(preview = processed.bitmap)
                }
                _state.value = if (result.parsed.items.isEmpty()) {
                    ScanState.NothingFound
                } else {
                    ScanState.Done(result.parsed, withContext(Dispatchers.Default) { ScannedReceiptImage.of(result) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed", e)
                _state.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Saves the receipt languages (the same setting as in the settings screen) and, if a
     * photo is being read or could not be read, reads it again with them.
     */
    fun setLanguages(languages: List<OcrLanguage>) {
        settings.setReceiptLanguages(languages.map { it.tesseractCode })
        val uri = lastUri
        if (uri != null && _state.value !is ScanState.Idle) scan(uri)
    }

    /** Back to the camera: cancels a scan in progress, or forgets the last outcome. */
    fun reset() {
        job?.cancel()
        job = null
        lastUri = null
        _state.value = ScanState.Idle
    }

    private fun resolve(codes: List<String>): List<OcrLanguage> =
        OcrLanguage.fromCodes(codes, ReceiptScanner::defaultLanguages)

    private companion object {
        const val TAG = "ScanReceiptViewModel"
    }
}
