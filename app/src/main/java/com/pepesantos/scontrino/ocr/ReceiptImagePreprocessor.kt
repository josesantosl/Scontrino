package com.pepesantos.scontrino.ocr

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares a receipt photo for OCR with OpenCV:
 *
 * 1. grayscale
 * 2. locate the receipt: a clean quadrilateral (perspective is corrected) or, when
 *    fingers/framing hide the corners, the largest bright region (background is
 *    whitened and cropped)
 * 3. deskew so text lines are horizontal (row reconstruction depends on it)
 * 4. upscale small images
 * 5. flatten uneven lighting
 * 6. (optional) binarize with a strict threshold that drops faint show-through
 *    printing from the back of the paper
 */
class ReceiptImagePreprocessor {

    data class Options(
        val detectDocument: Boolean = true,
        val deskew: Boolean = true,
        val binarize: Boolean = true,
    )

    enum class Detection {
        /** Four corners found, perspective corrected. */
        CORNERS,
        /** Corners not found, but the paper was found as a bright region. */
        BRIGHT_REGION,
        NONE,
    }

    data class Result(
        val bitmap: Bitmap,
        val detection: Detection,
        /** Rotation applied to straighten text lines, in degrees. */
        val skewAngle: Double,
    )

    fun process(input: Bitmap, options: Options = Options()): Result {
        check(OpenCv.isLoaded) { "OpenCV native library could not be loaded" }

        val rgba = Mat()
        Utils.bitmapToMat(input, rgba)
        var working = Mat()
        Imgproc.cvtColor(rgba, working, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()

        var detection = Detection.NONE
        if (options.detectDocument) {
            val corners = findReceiptCorners(working)
            if (corners != null) {
                working = replace(working, warp(working, corners))
                detection = Detection.CORNERS
            } else {
                isolateBrightRegion(working)?.let {
                    working = replace(working, it)
                    detection = Detection.BRIGHT_REGION
                }
            }
        }

        var skewAngle = 0.0
        if (options.deskew) {
            skewAngle = estimateSkew(working)
            if (skewAngle != 0.0) working = replace(working, rotate(working, skewAngle))
        }

        if (working.cols() < MIN_OCR_WIDTH) {
            working = replace(working, upscale(working, MIN_OCR_WIDTH.toDouble() / working.cols()))
        }

        working = replace(working, normalizeIllumination(working))

        if (options.binarize) {
            working = replace(working, binarize(working))
        }

        val output = Bitmap.createBitmap(working.cols(), working.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(working, output)
        working.release()
        return Result(output, detection, skewAngle)
    }

    /** Releases [old] and returns [new], to keep Mat lifetimes explicit. */
    private fun replace(old: Mat, new: Mat): Mat {
        old.release()
        return new
    }

    private fun downscaleFactor(mat: Mat, maxSide: Double) =
        minOf(1.0, maxSide / max(mat.cols(), mat.rows()).toDouble())

    // region Receipt location

    /**
     * Looks for the largest convex quadrilateral covering a meaningful part of the
     * photo (the receipt) on a downscaled copy. Returns corners ordered
     * top-left, top-right, bottom-right, bottom-left in full-size coordinates.
     */
    private fun findReceiptCorners(gray: Mat): List<Point>? {
        val scale = downscaleFactor(gray, DETECTION_MAX_SIDE)
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(small, edges, 50.0, 150.0)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val minArea = small.total() * MIN_DOCUMENT_AREA_RATIO
        val corners = contours
            .map { it to Imgproc.contourArea(it) }
            .filter { (_, area) -> area >= minArea }
            .sortedByDescending { (_, area) -> area }
            .take(5)
            .firstNotNullOfOrNull { (contour, _) -> approximateQuad(contour) }

        contours.forEach { it.release() }
        listOf(small, edges, kernel, hierarchy).forEach { it.release() }

        return corners
            ?.map { Point(it.x / scale, it.y / scale) }
            ?.let(::orderCorners)
    }

    private fun approximateQuad(contour: MatOfPoint): List<Point>? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
        val points = approx.toArray().toList()
        curve.release()
        approx.release()
        if (points.size != 4) return null

        val polygon = MatOfPoint(*points.toTypedArray())
        val convex = Imgproc.isContourConvex(polygon)
        polygon.release()
        return points.takeIf { convex }
    }

    private fun orderCorners(points: List<Point>): List<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.minBy { it.y - it.x }
        val bottomLeft = points.maxBy { it.y - it.x }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun warp(gray: Mat, corners: List<Point>): Mat {
        val (tl, tr, br, bl) = corners
        val width = max(distance(tl, tr), distance(bl, br)).roundToInt()
        val height = max(distance(tl, bl), distance(tr, br)).roundToInt()

        val source = MatOfPoint2f(tl, tr, br, bl)
        val target = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(source, target)
        val warped = Mat()
        Imgproc.warpPerspective(gray, warped, transform, Size(width.toDouble(), height.toDouble()), Imgproc.INTER_CUBIC)
        listOf(source, target, transform).forEach { it.release() }
        return warped
    }

    /**
     * Fallback when the corners are hidden (fingers, paper running off the frame):
     * the receipt is usually the largest bright area on a darker background.
     * Everything outside it is painted white and the image is cropped to it.
     * Returns null if no plausible region is found, or it already fills the photo.
     */
    private fun isolateBrightRegion(gray: Mat): Mat? {
        val scale = downscaleFactor(gray, DETECTION_MAX_SIDE)
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        Imgproc.GaussianBlur(small, small, Size(7.0, 7.0), 0.0)

        val bright = Mat()
        Imgproc.threshold(small, bright, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        // Fill the dark text inside the paper, then drop small specks.
        val closeSize = (small.cols() / 25.0).coerceAtLeast(9.0)
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(closeSize, closeSize))
        Imgproc.morphologyEx(bright, bright, Imgproc.MORPH_CLOSE, closeKernel)
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(bright, bright, Imgproc.MORPH_OPEN, openKernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bright, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val largest = contours.maxByOrNull { Imgproc.contourArea(it) }
        val areaRatio = largest?.let { Imgproc.contourArea(it) / small.total() } ?: 0.0

        var result: Mat? = null
        if (largest != null && areaRatio in MIN_REGION_AREA_RATIO..MAX_REGION_AREA_RATIO) {
            val smallMask = Mat.zeros(small.size(), CvType.CV_8UC1)
            Imgproc.drawContours(smallMask, listOf(largest), 0, Scalar(255.0), -1)
            val mask = Mat()
            Imgproc.resize(smallMask, mask, gray.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
            val outside = Mat()
            Core.bitwise_not(mask, outside)

            val box = Imgproc.boundingRect(largest)
            val crop = Rect(
                (box.x / scale).toInt(), (box.y / scale).toInt(),
                (box.width / scale).toInt(), (box.height / scale).toInt(),
            ).clampTo(gray)

            val cleaned = gray.clone()
            cleaned.setTo(Scalar(255.0), outside)
            result = cleaned.submat(crop).clone()
            listOf(smallMask, mask, outside, cleaned).forEach { it.release() }
        }

        contours.forEach { it.release() }
        listOf(small, bright, closeKernel, openKernel, hierarchy).forEach { it.release() }
        return result
    }

    private fun Rect.clampTo(mat: Mat): Rect {
        val left = x.coerceIn(0, mat.cols() - 1)
        val top = y.coerceIn(0, mat.rows() - 1)
        return Rect(left, top, width.coerceAtMost(mat.cols() - left), height.coerceAtMost(mat.rows() - top))
    }

    // endregion

    // region Deskew

    /**
     * Finds the rotation that makes text lines horizontal, using a projection
     * profile: when lines are level, the per-row ink sums alternate sharply between
     * text and gaps, which maximizes the squared difference between adjacent rows.
     */
    private fun estimateSkew(gray: Mat): Double {
        val scale = downscaleFactor(gray, SKEW_MAX_SIDE)
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        val ink = Mat()
        Imgproc.adaptiveThreshold(
            small, ink, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 15.0
        )

        fun score(angle: Double): Double {
            val transform = Imgproc.getRotationMatrix2D(Point(ink.cols() / 2.0, ink.rows() / 2.0), angle, 1.0)
            val rotated = Mat()
            Imgproc.warpAffine(
                ink, rotated, transform, ink.size(),
                Imgproc.INTER_NEAREST, Core.BORDER_CONSTANT, Scalar(0.0)
            )
            val rowSums = Mat()
            Core.reduce(rotated, rowSums, 1, Core.REDUCE_SUM, CvType.CV_32F)
            val values = FloatArray(rowSums.rows())
            rowSums.get(0, 0, values)
            listOf(transform, rotated, rowSums).forEach { it.release() }

            var sum = 0.0
            for (i in 1 until values.size) {
                val d = (values[i] - values[i - 1]).toDouble()
                sum += d * d
            }
            return sum
        }

        val coarse = (-MAX_SKEW_STEPS..MAX_SKEW_STEPS).map { it * 0.5 }.maxBy(::score)
        val fine = (-5..5).map { coarse + it * 0.1 }.maxBy(::score)
        small.release()
        ink.release()
        return if (kotlin.math.abs(fine) < 0.1) 0.0 else fine
    }

    private fun rotate(gray: Mat, angle: Double): Mat {
        val transform = Imgproc.getRotationMatrix2D(Point(gray.cols() / 2.0, gray.rows() / 2.0), angle, 1.0)
        val rotated = Mat()
        Imgproc.warpAffine(
            gray, rotated, transform, gray.size(),
            Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar(255.0)
        )
        transform.release()
        return rotated
    }

    // endregion

    private fun upscale(gray: Mat, factor: Double): Mat {
        val f = minOf(factor, MAX_UPSCALE)
        val out = Mat()
        Imgproc.resize(gray, out, Size(), f, f, Imgproc.INTER_CUBIC)
        return out
    }

    /**
     * Estimates the paper brightness (dilation removes the dark text, blur smooths it)
     * and divides by it, so glare and shadows end up as uniform white paper.
     */
    private fun normalizeIllumination(gray: Mat): Mat {
        var size = (gray.cols() / 40).coerceAtLeast(15)
        if (size % 2 == 0) size++
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(size.toDouble(), size.toDouble()))
        val background = Mat()
        Imgproc.dilate(gray, background, kernel)
        Imgproc.GaussianBlur(background, background, Size(size.toDouble(), size.toDouble()), 0.0)
        val out = Mat()
        Core.divide(gray, background, out, 255.0)
        kernel.release()
        background.release()
        return out
    }

    /**
     * Adaptive threshold on the flattened image. The large offset keeps the dark
     * front-side ink and drops the light gray show-through from the back.
     */
    private fun binarize(gray: Mat): Mat {
        var blockSize = (gray.cols() / 20).coerceIn(15, 151)
        if (blockSize % 2 == 0) blockSize++
        val out = Mat()
        Imgproc.adaptiveThreshold(
            gray, out, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
            blockSize, BINARIZE_OFFSET
        )
        return out
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val DETECTION_MAX_SIDE = 1000.0
        const val MIN_DOCUMENT_AREA_RATIO = 0.2
        const val MIN_REGION_AREA_RATIO = 0.1
        /** A "region" covering almost everything means the paper fills the frame. */
        const val MAX_REGION_AREA_RATIO = 0.95
        const val SKEW_MAX_SIDE = 1000.0
        /** Search range ±10° in 0.5° steps, then refined in 0.1° steps. */
        const val MAX_SKEW_STEPS = 20
        const val MIN_OCR_WIDTH = 1200
        const val MAX_UPSCALE = 3.0
        const val BINARIZE_OFFSET = 25.0
    }
}

private object OpenCv {
    val isLoaded: Boolean by lazy { OpenCVLoader.initLocal() }
}
