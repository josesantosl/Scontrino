package com.pepesantos.scontrino.ocr

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.pepesantos.scontrino.ocr.draft.ReceiptLineCrops
import kotlin.math.roundToInt

/**
 * The cleaned-up receipt a scan was read from, kept in memory only while the scan is
 * reviewed (never saved with the receipt), with the box of each row of the reading.
 */
class ScannedReceiptImage(
    val bitmap: Bitmap,
    /** Box of each row of the chosen reading, in [bitmap] coordinates. */
    val rowBoxes: List<BoundingBox>,
) {
    /** The parts of the image showing [rows], one per run of consecutive rows. */
    fun crops(rows: List<Int>): List<BoundingBox> =
        ReceiptLineCrops.crops(rows, rowBoxes, bitmap.width, bitmap.height)

    companion object {
        /**
         * Wide enough to read a receipt line on a phone, much lighter than the OCR
         * resolution (up to 3000 px).
         */
        private const val MAX_WIDTH = 1200

        fun of(result: ReceiptScanner.Result): ScannedReceiptImage {
            val source = result.processed.bitmap
            val boxes = result.best.rows.map { it.box }
            if (source.width <= MAX_WIDTH) return ScannedReceiptImage(source, boxes)

            val factor = MAX_WIDTH.toDouble() / source.width
            fun Int.scaled() = (this * factor).roundToInt()
            return ScannedReceiptImage(
                bitmap = source.scale(MAX_WIDTH, source.height.scaled()),
                rowBoxes = boxes.map { BoundingBox(it.left.scaled(), it.top.scaled(), it.right.scaled(), it.bottom.scaled()) },
            )
        }
    }
}
