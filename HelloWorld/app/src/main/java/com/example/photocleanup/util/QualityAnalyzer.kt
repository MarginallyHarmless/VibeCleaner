package com.example.photocleanup.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Analyzes photo quality to detect issues like blur, motion blur, exposure problems, and noise.
 *
 * Quality scores range from 0.0 (bad) to 1.0 (good).
 *
 * Performance: All pixel data is bulk-read once via getPixels() and a luminance array
 * is pre-computed. All analysis methods operate on arrays — no per-pixel JNI calls.
 *
 * Blur detection uses a tiled Laplacian approach on a 256x256 bitmap:
 * - Divides the image into a 4x4 grid of 64x64 tiles
 * - Computes Laplacian variance per tile (measures edge strength)
 * - Uses the top quartile (best 4 of 16 tiles) as the sharpness score
 *   so that bokeh photos score high (subject tiles dominate) while
 *   uniformly blurry photos score low (even the best tiles are weak)
 * - A single noisy tile can't save a blurry photo (averaged with 3 others)
 *
 * Motion blur is detected via directional gradient analysis (Sobel-X vs Sobel-Y).
 * If gradients are strongly directional (ratio > 3.0), it indicates motion blur.
 */
object QualityAnalyzer {

    private const val DEBUG = false

    // Issue types
    enum class QualityIssue {
        BLURRY,           // Out of focus (uniform blur)
        MOTION_BLUR,      // Directional motion blur
        VERY_DARK,        // Almost completely black
        VERY_BRIGHT,      // Almost completely white
        UNDEREXPOSED,     // Too dark but has content
        OVEREXPOSED,      // Blown highlights
        NOISY,            // High noise/grain
        LOW_CONTRAST      // Flat, washed out
    }

    // Sharpness thresholds
    private const val SHARPNESS_THRESHOLD = 0.60f        // Below = blurry (top-quartile score)
    private const val CENTER_SHARPNESS_THRESHOLD = 0.40f // Below = center is out of focus (catches misfocused photos)

    // Tiled Laplacian constants
    private const val QUALITY_GRID_SIZE = 4              // 4x4 grid of tiles
    private const val TILE_NORMALIZATION_DIVISOR = 35.0  // Normalizes sqrt(variance) to 0-1 range (lower = wider score spread)
    private const val TOP_QUARTILE_COUNT = 4             // Use best 4 of 16 tiles for score
    private const val EDGE_PIXEL_THRESHOLD = 15.0        // |Laplacian| above this = edge pixel
    private const val EDGE_DENSITY_BLURRY_THRESHOLD = 0.05  // Below 5% edge pixels = likely blurry

    // Motion blur detection
    private const val MOTION_BLUR_RATIO_THRESHOLD = 5.0  // Gradient ratio above this = directional blur (5.0 avoids false positives on buildings/stripes)

    // Exposure thresholds (unchanged)
    private const val DARK_THRESHOLD = 0.04f           // Below = almost black (accidental shots)
    private const val BRIGHT_THRESHOLD = 0.96f         // Above = almost white (accidental shots)
    private const val UNDEREXPOSED_THRESHOLD = 0.06f   // Below = extremely dark (stricter - night photos OK)
    private const val OVEREXPOSED_THRESHOLD = 0.90f    // Above = very bright
    private const val NOISE_THRESHOLD = 0.95f          // Above = extreme noise only (disabled anyway)
    private const val CONTRAST_THRESHOLD = 0.06f       // Below = very flat

    /**
     * Holds detailed sharpness metrics from the tiled Laplacian analysis.
     */
    data class SharpnessResult(
        val score: Float,            // Top-quartile sharpness score (0-1)
        val centerScore: Float,      // Average sharpness of center 4 tiles (0-1)
        val edgeDensity: Float,      // Fraction of pixels that are edges (0-1)
        val maxTileVariance: Double,  // Highest single tile variance (for debugging)
        val meanTileVariance: Double  // Average tile variance (for debugging)
    )

    data class QualityResult(
        val sharpnessScore: Float,
        val exposureScore: Float,
        val noiseScore: Float,
        val overallQuality: Float,
        val issues: List<QualityIssue>
    ) {
        val issuesString: String get() = issues.joinToString(",") { it.name }
        val hasIssues: Boolean get() = issues.isNotEmpty()

        // Human-readable primary issue
        val primaryIssue: String? get() = when (issues.firstOrNull()) {
            QualityIssue.BLURRY -> "Blurry"
            QualityIssue.MOTION_BLUR -> "Motion Blur"
            QualityIssue.VERY_DARK -> "Black"
            QualityIssue.VERY_BRIGHT -> "White"
            QualityIssue.UNDEREXPOSED -> "Too Dark"
            QualityIssue.OVEREXPOSED -> "Overexposed"
            QualityIssue.NOISY -> "Noisy"
            QualityIssue.LOW_CONTRAST -> "Low Contrast"
            null -> null
        }
    }

    /**
     * Analyze a bitmap and return quality metrics.
     * Expects a 256x256 bitmap for optimal blur detection accuracy.
     *
     * All pixels are bulk-read once and luminance is pre-computed to avoid
     * per-pixel JNI overhead from getPixel().
     */
    fun analyze(bitmap: Bitmap): QualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        // Bulk-read all pixels once (single JNI call instead of 65K+ individual ones)
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Pre-compute luminance array (used by sharpness, motion blur, exposure, noise)
        val lum = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            lum[i] = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt().coerceIn(0, 255)
        }

        val sharpnessResult = computeSharpnessDetailed(lum, width, height)
        val sharpness = sharpnessResult.score
        val (exposure, avgBrightness, contrast) = computeExposure(lum, totalPixels)
        val noise = computeNoise(lum, width, height)

        // Check if this looks like a screenshot (skip exposure checks for screenshots)
        val isLikelyScreenshot = isScreenshot(pixels, totalPixels)

        // Detect issues
        val issues = mutableListOf<QualityIssue>()

        // Exposure issues - skip for screenshots (they often have white/black UI backgrounds)
        if (!isLikelyScreenshot) {
            when {
                avgBrightness < DARK_THRESHOLD -> issues.add(QualityIssue.VERY_DARK)
                avgBrightness > BRIGHT_THRESHOLD -> issues.add(QualityIssue.VERY_BRIGHT)
                avgBrightness < UNDEREXPOSED_THRESHOLD -> issues.add(QualityIssue.UNDEREXPOSED)
                avgBrightness > OVEREXPOSED_THRESHOLD -> issues.add(QualityIssue.OVEREXPOSED)
            }
        }

        // Blur detection (only check if not completely dark/bright, and not a screenshot)
        if (!isLikelyScreenshot && avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD) {
            if (sharpness < SHARPNESS_THRESHOLD) {
                // Below sharpness threshold — determine if it's motion blur or general blur
                val isMotion = detectMotionBlur(lum, width, height)
                if (isMotion) {
                    issues.add(QualityIssue.MOTION_BLUR)
                } else {
                    // Edge density tiebreaker: if sharpness is borderline (within 0.05 of threshold),
                    // only flag as blurry if edge density is also low
                    val isBorderline = sharpness >= (SHARPNESS_THRESHOLD - 0.05f)
                    if (isBorderline && sharpnessResult.edgeDensity >= EDGE_DENSITY_BLURRY_THRESHOLD) {
                        if (DEBUG) {
                            android.util.Log.d("QualityAnalyzer",
                                "Borderline sharpness=$sharpness but edgeDensity=${sharpnessResult.edgeDensity} >= $EDGE_DENSITY_BLURRY_THRESHOLD, NOT flagging")
                        }
                    } else {
                        issues.add(QualityIssue.BLURRY)
                    }
                }
            } else if (sharpnessResult.centerScore < CENTER_SHARPNESS_THRESHOLD) {
                // Overall top-quartile passed (edges are sharp), but center is blurry.
                // This catches misfocused photos where autofocus locked on background/edges
                // instead of the subject in the center of the frame.
                if (DEBUG) {
                    android.util.Log.d("QualityAnalyzer",
                        "Center blur: overall=$sharpness passed, but centerScore=${sharpnessResult.centerScore} < $CENTER_SHARPNESS_THRESHOLD")
                }
                issues.add(QualityIssue.BLURRY)
            }
        }

        // Noise detection disabled - the local variance method mistakes texture/detail for noise
        // and flags almost every photo. Would need a more sophisticated algorithm (e.g., wavelet-based)
        // to reliably detect actual noise without false positives.
        // if (avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD && noise > NOISE_THRESHOLD) {
        //     issues.add(QualityIssue.NOISY)
        // }

        // Contrast (skip for screenshots)
        if (!isLikelyScreenshot && contrast < CONTRAST_THRESHOLD && avgBrightness in 0.2f..0.8f) {
            issues.add(QualityIssue.LOW_CONTRAST)
        }

        // Overall quality (weighted average, penalize for issues)
        val baseQuality = (sharpness * 0.4f + exposure * 0.4f + (1f - noise) * 0.2f)
        val penalty = issues.size * 0.15f
        val overallQuality = (baseQuality - penalty).coerceIn(0f, 1f)

        if (DEBUG) {
            android.util.Log.d("QualityAnalyzer",
                "sharpness=${"%.3f".format(sharpness)}, centerScore=${"%.3f".format(sharpnessResult.centerScore)}, " +
                "edgeDensity=${"%.3f".format(sharpnessResult.edgeDensity)}, " +
                "maxTileVar=${"%.1f".format(sharpnessResult.maxTileVariance)}, meanTileVar=${"%.1f".format(sharpnessResult.meanTileVariance)}, " +
                "exposure=${"%.2f".format(exposure)}, brightness=${"%.2f".format(avgBrightness)}, " +
                "contrast=${"%.2f".format(contrast)}, issues=$issues")
        }

        return QualityResult(
            sharpnessScore = sharpness,
            exposureScore = exposure,
            noiseScore = noise,
            overallQuality = overallQuality,
            issues = issues
        )
    }

    /**
     * Compute sharpness using a tiled Laplacian variance approach.
     *
     * Divides the bitmap into a 4x4 grid and computes Laplacian variance per tile.
     * Sorts tiles by variance and uses the top quartile (best 4 of 16) as the score.
     *
     * Why top quartile instead of max or mean:
     * - Max alone: one noisy tile on a blurry photo fakes sharpness → false negatives
     * - Mean alone: bokeh background tiles drag down sharp subject → false positives
     * - Top quartile: 4 tiles must be sharp to pass, robust against both failure modes
     *   A sharp subject typically covers 25%+ of the frame (4+ tiles)
     *
     * Center-weighted scoring: The inner 4 tiles (positions 1,1 / 1,2 / 2,1 / 2,2)
     * are scored separately. If a photo has sharp edges but a blurry center, it's
     * likely misfocused — the autofocus locked onto background/edges instead of the subject.
     *
     * Also counts edge pixels (|Laplacian| > threshold) to compute edge density,
     * used as a tiebreaker for borderline cases.
     */
    private fun computeSharpnessDetailed(lum: IntArray, width: Int, height: Int): SharpnessResult {
        if (width < 3 || height < 3) return SharpnessResult(0.5f, 0.5f, 0.0f, 0.0, 0.0)

        val tileW = width / QUALITY_GRID_SIZE
        val tileH = height / QUALITY_GRID_SIZE
        if (tileW < 3 || tileH < 3) return SharpnessResult(0.5f, 0.5f, 0.0f, 0.0, 0.0)

        val tileVariances = mutableListOf<Double>()
        val centerTileVariances = mutableListOf<Double>()
        var totalEdgePixels = 0
        var totalPixelsChecked = 0

        // Center tiles in a 4x4 grid are at positions (1,1), (1,2), (2,1), (2,2)
        val centerRange = 1..2

        for (ty in 0 until QUALITY_GRID_SIZE) {
            for (tx in 0 until QUALITY_GRID_SIZE) {
                val startX = tx * tileW
                val startY = ty * tileH
                val endX = if (tx == QUALITY_GRID_SIZE - 1) width else startX + tileW
                val endY = if (ty == QUALITY_GRID_SIZE - 1) height else startY + tileH

                var sum = 0.0
                var sumSq = 0.0
                var count = 0

                // Laplacian kernel: [0, 1, 0], [1, -4, 1], [0, 1, 0]
                for (y in max(startY, 1) until min(endY, height - 1)) {
                    for (x in max(startX, 1) until min(endX, width - 1)) {
                        val idx = y * width + x
                        val center = lum[idx]
                        val top = lum[idx - width]
                        val bottom = lum[idx + width]
                        val left = lum[idx - 1]
                        val right = lum[idx + 1]

                        val laplacian = (top + bottom + left + right - 4 * center).toDouble()
                        sum += laplacian
                        sumSq += laplacian * laplacian
                        count++

                        // Count edge pixels for edge density
                        if (abs(laplacian) > EDGE_PIXEL_THRESHOLD) {
                            totalEdgePixels++
                        }
                        totalPixelsChecked++
                    }
                }

                if (count > 0) {
                    val mean = sum / count
                    val variance = (sumSq / count) - (mean * mean)
                    tileVariances.add(variance)

                    // Track center tile variances separately
                    if (tx in centerRange && ty in centerRange) {
                        centerTileVariances.add(variance)
                    }
                }
            }
        }

        if (tileVariances.isEmpty()) return SharpnessResult(0.5f, 0.5f, 0.0f, 0.0, 0.0)

        // Sort descending so best tiles are first
        tileVariances.sortDescending()

        val maxVariance = tileVariances[0]
        val meanVariance = tileVariances.average()

        // Top quartile: average the best 4 tiles' normalized scores
        val topCount = min(TOP_QUARTILE_COUNT, tileVariances.size)
        var topQuartileSum = 0.0
        for (i in 0 until topCount) {
            topQuartileSum += (sqrt(tileVariances[i]) / TILE_NORMALIZATION_DIVISOR).coerceIn(0.0, 1.0)
        }
        val score = (topQuartileSum / topCount).toFloat()

        // Center score: average of center 4 tiles' normalized scores
        val centerScore = if (centerTileVariances.isNotEmpty()) {
            var centerSum = 0.0
            for (v in centerTileVariances) {
                centerSum += (sqrt(v) / TILE_NORMALIZATION_DIVISOR).coerceIn(0.0, 1.0)
            }
            (centerSum / centerTileVariances.size).toFloat()
        } else {
            score // fallback to overall score if no center tiles
        }

        val edgeDensity = if (totalPixelsChecked > 0) {
            totalEdgePixels.toFloat() / totalPixelsChecked
        } else 0f

        if (DEBUG) {
            val tileScores = tileVariances.map { "%.1f".format(it) }
            val centerScores = centerTileVariances.map { "%.1f".format(it) }
            android.util.Log.d("QualityAnalyzer",
                "Tile variances (sorted): $tileScores, topQ=${"%.3f".format(score)}, " +
                "centerTiles=$centerScores, centerScore=${"%.3f".format(centerScore)}, " +
                "edgeDensity=${"%.3f".format(edgeDensity)}")
        }

        return SharpnessResult(
            score = score,
            centerScore = centerScore,
            edgeDensity = edgeDensity,
            maxTileVariance = maxVariance,
            meanTileVariance = meanVariance
        )
    }

    /**
     * Detect motion blur using directional gradient analysis.
     *
     * Computes Sobel-X (horizontal edges) and Sobel-Y (vertical edges) variances.
     * Motion blur smears edges in one direction, so one gradient direction will have
     * significantly higher variance than the other. A ratio > 3.0 indicates directional blur.
     *
     * Samples every 2nd pixel for performance (still accurate at 256x256).
     */
    private fun detectMotionBlur(lum: IntArray, width: Int, height: Int): Boolean {
        if (width < 3 || height < 3) return false

        var sobelXSum = 0.0
        var sobelXSumSq = 0.0
        var sobelYSum = 0.0
        var sobelYSumSq = 0.0
        var count = 0

        // Sample every 2nd pixel for performance
        for (y in 1 until height - 1 step 2) {
            for (x in 1 until width - 1 step 2) {
                val idx = y * width + x
                val topLeft = lum[idx - width - 1]
                val top = lum[idx - width]
                val topRight = lum[idx - width + 1]
                val left = lum[idx - 1]
                val right = lum[idx + 1]
                val bottomLeft = lum[idx + width - 1]
                val bottom = lum[idx + width]
                val bottomRight = lum[idx + width + 1]

                // Sobel-X: detects vertical edges (horizontal gradient)
                val gx = (-topLeft + topRight - 2 * left + 2 * right - bottomLeft + bottomRight).toDouble()

                // Sobel-Y: detects horizontal edges (vertical gradient)
                val gy = (-topLeft - 2 * top - topRight + bottomLeft + 2 * bottom + bottomRight).toDouble()

                sobelXSum += gx
                sobelXSumSq += gx * gx
                sobelYSum += gy
                sobelYSumSq += gy * gy
                count++
            }
        }

        if (count == 0) return false

        val sobelXMean = sobelXSum / count
        val sobelYMean = sobelYSum / count
        val sobelXVariance = (sobelXSumSq / count) - (sobelXMean * sobelXMean)
        val sobelYVariance = (sobelYSumSq / count) - (sobelYMean * sobelYMean)

        // Avoid division by zero — if both are near zero, image is uniformly flat (not motion blur)
        val maxVar = max(sobelXVariance, sobelYVariance)
        val minVar = min(sobelXVariance, sobelYVariance)
        if (minVar < 1.0) return false  // Too little gradient overall

        val ratio = maxVar / minVar

        if (DEBUG) {
            android.util.Log.d("QualityAnalyzer",
                "Motion blur: sobelXVar=${"%.1f".format(sobelXVariance)}, sobelYVar=${"%.1f".format(sobelYVariance)}, ratio=${"%.2f".format(ratio)}")
        }

        return ratio > MOTION_BLUR_RATIO_THRESHOLD
    }

    /**
     * Compute exposure metrics from histogram analysis.
     * Returns (exposureScore, avgBrightness, contrast)
     */
    private fun computeExposure(lum: IntArray, pixelCount: Int): Triple<Float, Float, Float> {
        if (pixelCount == 0) return Triple(0.5f, 0.5f, 0.5f)

        val histogram = IntArray(256)
        var totalBrightness = 0L

        for (i in 0 until pixelCount) {
            val l = lum[i]
            histogram[l]++
            totalBrightness += l
        }

        val avgBrightness = (totalBrightness.toFloat() / pixelCount) / 255f

        // Find percentiles for contrast
        var cumulative = 0
        var p5 = 0
        var p95 = 255
        for (i in 0..255) {
            cumulative += histogram[i]
            if (p5 == 0 && cumulative >= pixelCount * 0.05) p5 = i
            if (cumulative >= pixelCount * 0.95) {
                p95 = i
                break
            }
        }
        val contrast = (p95 - p5) / 255f

        // Exposure score: penalize extremes
        val exposureScore = when {
            avgBrightness < 0.1f -> avgBrightness * 2  // Very dark
            avgBrightness > 0.9f -> (1f - avgBrightness) * 2  // Very bright
            avgBrightness < 0.3f -> 0.5f + (avgBrightness - 0.1f)  // Dark
            avgBrightness > 0.7f -> 0.5f + (0.9f - avgBrightness)  // Bright
            else -> 0.8f + (0.5f - abs(avgBrightness - 0.5f)) * 0.4f  // Good range
        }.coerceIn(0f, 1f)

        return Triple(exposureScore, avgBrightness, contrast)
    }

    /**
     * Compute noise estimate using local variance.
     * Higher values = more noise.
     */
    private fun computeNoise(lum: IntArray, width: Int, height: Int): Float {
        if (width < 4 || height < 4) return 0.3f

        // Divide into 4x4 blocks and measure local variance
        val blockSize = minOf(width, height) / 4
        if (blockSize < 2) return 0.3f

        var totalVariance = 0.0
        var blockCount = 0

        for (by in 0 until 4) {
            for (bx in 0 until 4) {
                val startX = bx * blockSize
                val startY = by * blockSize

                var sum = 0.0
                var sumSq = 0.0
                var count = 0

                for (y in startY until minOf(startY + blockSize, height)) {
                    for (x in startX until minOf(startX + blockSize, width)) {
                        val l = lum[y * width + x].toDouble()
                        sum += l
                        sumSq += l * l
                        count++
                    }
                }

                if (count > 0) {
                    val mean = sum / count
                    val variance = (sumSq / count) - (mean * mean)
                    totalVariance += variance
                    blockCount++
                }
            }
        }

        if (blockCount == 0) return 0.3f

        val avgVariance = totalVariance / blockCount
        // Normalize: low variance = smooth/good, high variance = noisy
        // Empirically, noise variance > 200 is noticeable
        val normalized = (sqrt(avgVariance) / 25.0).coerceIn(0.0, 1.0)
        return normalized.toFloat()
    }

    /**
     * Detect if an image is likely a screenshot based on color patterns.
     * Screenshots typically have:
     * - Large areas of pure white or black (UI backgrounds)
     * - Very limited color palette compared to photos
     */
    private fun isScreenshot(pixels: IntArray, totalPixels: Int): Boolean {
        val colorCounts = mutableMapOf<Int, Int>()
        var pureWhiteCount = 0
        var pureBlackCount = 0

        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Count near-pure white pixels (RGB > 245)
            if (r > 245 && g > 245 && b > 245) {
                pureWhiteCount++
            }
            // Count near-pure black pixels (RGB < 10)
            if (r < 10 && g < 10 && b < 10) {
                pureBlackCount++
            }

            // Quantize for color variety check (inline for speed)
            val quantized = ((r / 32) * 32 shl 16) or ((g / 32) * 32 shl 8) or ((b / 32) * 32)
            colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
        }

        val pureWhiteRatio = pureWhiteCount.toFloat() / totalPixels
        val pureBlackRatio = pureBlackCount.toFloat() / totalPixels
        val uniqueColors = colorCounts.size

        // Screenshot if:
        // 1. >35% pure white or black (solid background with text/icons), OR
        // 2. Limited color palette (<60 unique colors) indicating UI graphics, OR
        // 3. Moderate solid background (>25%) AND limited palette (<120 colors)
        // Note: thresholds scaled up from 20/40 because 256x256 preserves more detail than 64x64
        val hasSolidBackground = pureWhiteRatio > 0.35f || pureBlackRatio > 0.35f
        val hasVeryLimitedPalette = uniqueColors < 60
        val hasModerateSolidAndLimitedColors =
            (pureWhiteRatio > 0.25f || pureBlackRatio > 0.25f) && uniqueColors < 120

        val isScreenshot = hasSolidBackground || hasVeryLimitedPalette || hasModerateSolidAndLimitedColors

        if (DEBUG && isScreenshot) {
            android.util.Log.d("QualityAnalyzer",
                "Screenshot detected: white=$pureWhiteRatio, black=$pureBlackRatio, colors=$uniqueColors")
        }

        return isScreenshot
    }
}
