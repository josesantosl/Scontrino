package com.pepesantos.scontrino.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes a photo from a content Uri, bounded in size and with EXIF rotation applied. */
object ReceiptImageLoader {

    /** Large enough for small receipt fonts, small enough to keep memory reasonable. */
    private const val MAX_SIDE = 3000

    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        val resolver = context.contentResolver

        // With inJustDecodeBounds, decodeStream always returns null and only fills
        // outWidth/outHeight, so only the stream itself is checked here.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largestSide = max(bounds.outWidth, bounds.outHeight)
        if (largestSide <= 0) throw IOException("Not an image: $uri")

        var sampleSize = 1
        while (largestSide / (sampleSize * 2) >= maxSide) sampleSize *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it, null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: throw IOException("Cannot decode $uri")

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it != decoded) decoded.recycle() }
        } else {
            decoded
        }

        val side = max(rotated.width, rotated.height)
        if (side <= maxSide) return rotated
        val factor = maxSide.toFloat() / side
        return rotated.scale((rotated.width * factor).roundToInt(), (rotated.height * factor).roundToInt())
            .also { if (it != rotated) rotated.recycle() }
    }
}
