package com.pepesantos.scontrino.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pepesantos.scontrino.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Open source software shipped in the app and its licenses, most specific to the app first.
 *
 * Kept by hand: when adding, removing or bumping a dependency (or adding traineddata to assets),
 * update [LIBRARIES]. Only libraries that end up in the APK belong here, and a license applies only
 * to its own authors' code, so bundled third-party code (like the native libraries inside
 * Tesseract4Android) needs its own license listed even if the wrapper is Apache 2.0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onNavigateBack: () -> Unit) {
    BackHandler(onBack = onNavigateBack)
    var shownName by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_receipt_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            items(LIBRARIES, key = { it.name }) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = {
                        Column {
                            Text(stringResource(library.purpose))
                            library.includes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                library.licenses.joinToString(" · ") { it.title },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.clickable { shownName = library.name }
                )
            }
        }
    }

    LIBRARIES.firstOrNull { it.name == shownName }?.let { library ->
        LicenseDialog(library = library, onDismiss = { shownName = null })
    }
}

/** The full text of every license of [library], read from res/raw. */
@Composable
private fun LicenseDialog(library: OpenSourceLibrary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val texts by produceState<List<String>?>(initialValue = null, library) {
        value = withContext(Dispatchers.IO) { library.licenses.map { it.read(context) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(library.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(library.website, style = MaterialTheme.typography.labelSmall)
                texts?.forEachIndexed { index, text ->
                    Text(
                        library.licenses[index].title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_receipt_ok)) }
        },
    )
}

private enum class License(val title: String, @param:RawRes val textRes: Int) {
    APACHE_2_0("Apache License 2.0", R.raw.license_apache_2_0),
    LIBYUV("BSD 3-Clause (libyuv)", R.raw.license_libyuv),
    LEPTONICA("Leptonica (BSD 2-Clause)", R.raw.license_leptonica),
    IJG("Independent JPEG Group", R.raw.license_ijg),
    LIBPNG("libpng License v2", R.raw.license_libpng),
    CAROTENE("BSD 3-Clause (Carotene)", R.raw.license_carotene);

    fun read(context: Context): String =
        context.resources.openRawResource(textRes).bufferedReader().use { it.readText() }
}

/**
 * [purpose]: what the app uses it for, in plain words. [includes]: what else ships inside, with its
 * own authors (names, not translated).
 */
private data class OpenSourceLibrary(
    val name: String,
    @param:StringRes val purpose: Int,
    val includes: String?,
    val website: String,
    val licenses: List<License>,
)

private val LIBRARIES = listOf(
    OpenSourceLibrary(
        name = "Tesseract OCR",
        purpose = R.string.licenses_purpose_tesseract,
        includes = "Tesseract4Android · tessdata_fast · Leptonica · libjpeg · libpng",
        website = "https://github.com/tesseract-ocr/tesseract",
        licenses = listOf(License.APACHE_2_0, License.LEPTONICA, License.IJG, License.LIBPNG),
    ),
    OpenSourceLibrary(
        name = "OpenCV",
        purpose = R.string.licenses_purpose_opencv,
        // Built from source by opencv/build-opencv.sh (core + imgproc): these ARM helpers ship inside.
        includes = "KleidiCV · Carotene · cpufeatures",
        website = "https://opencv.org",
        licenses = listOf(License.APACHE_2_0, License.CAROTENE),
    ),
    OpenSourceLibrary(
        name = "zxing-cpp",
        purpose = R.string.licenses_purpose_zxing_cpp,
        includes = null,
        website = "https://github.com/zxing-cpp/zxing-cpp",
        licenses = listOf(License.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "ZXing",
        purpose = R.string.licenses_purpose_zxing,
        includes = null,
        website = "https://github.com/zxing/zxing",
        licenses = listOf(License.APACHE_2_0),
    ),
    OpenSourceLibrary(
        name = "Android Jetpack",
        purpose = R.string.licenses_purpose_jetpack,
        includes = "AndroidX · Jetpack Compose · Room · CameraX · Guava ListenableFuture · AutoValue · JSpecify · libyuv",
        website = "https://developer.android.com/jetpack/androidx",
        licenses = listOf(License.APACHE_2_0, License.LIBYUV),
    ),
    OpenSourceLibrary(
        name = "Kotlin",
        purpose = R.string.licenses_purpose_kotlin,
        includes = "kotlinx.coroutines · kotlinx.serialization · JetBrains annotations",
        website = "https://kotlinlang.org",
        licenses = listOf(License.APACHE_2_0),
    ),
)
