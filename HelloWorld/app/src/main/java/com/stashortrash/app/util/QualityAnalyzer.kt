package com.stashortrash.app.util

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
 * - Filters out featureless tiles (luminance stddev < 5) — uniform areas like
 *   sky, walls, or skin can't be judged for sharpness (always low variance)
 * - Computes Laplacian variance per textured tile (measures edge strength)
 * - Uses the top quartile of textured tiles as the sharpness score
 *   so that bokeh photos score high (subject tiles dominate) while
 *   uniformly blurry photos score low (even the best tiles are weak)
 * - Center 4 tiles scored separately to catch misfocused photos
 * - If fewer than 2 textured tiles exist, blur check is skipped entirely
 *
 * Motion blur is detected via directional gradient analysis (Sobel-X vs Sobel-Y).
 * If gradients are strongly directional (ratio > 3.0), it indicates motion blur.
 */
object QualityAnalyzer {

    private const val DEBUG = true

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
    private const val SHARPNESS_THRESHOLD = 0.50f        // Below = blurry (top-quartile score)
    private const val CENTER_SHARPNESS_THRESHOLD = 0.35f // Below = center is out of focus (catches misfocused photos)

    // Tiled Laplacian constants
    private const val QUALITY_GRID_SIZE = 4              // 4x4 grid of tiles
    private const val TILE_NORMALIZATION_DIVISOR = 35.0  // Normalizes sqrt(variance) to 0-1 range (lower = wider score spread)
    private const val TOP_QUARTILE_COUNT = 2             // Use best 2 of 16 tiles for score (lower = more bokeh-tolerant)
    private const val EDGE_PIXEL_THRESHOLD = 15.0        // |Laplacian| above this = edge pixel
    private const val EDGE_DENSITY_BLURRY_THRESHOLD = 0.08  // Below 8% edge pixels = likely blurry (rescue threshold for shallow DOF)

    // Texture-aware tile filtering
    private const val TILE_TEXTURE_THRESHOLD = 5.0       // Min luminance stddev for a tile to be "textured" (below = uniform color, skip)
    private const val MIN_TEXTURED_TILES = 2             // Need at least 2 textured tiles to judge blur (otherwise image is mostly uniform)

    // Motion blur detection
    private const val MOTION_BLUR_RATIO_THRESHOLD = 5.0        // Gradient ratio above this = directional blur (when already below sharpness threshold)


    // Exposure thresholds
    private const val DARK_THRESHOLD = 0.10f           // Below = skip blur check (too dark to judge sharpness)
    private const val BRIGHT_THRESHOLD = 0.96f         // Above = almost white (accidental shots)
    private const val OVEREXPOSED_THRESHOLD = 0.90f    // Above = very bright

    // Underexposed detection: requires BOTH conditions:
    // 1. Image is overall dark (avgBrightness < 0.20) — gates the check so normal photos are never evaluated
    // 2. Even the brightest 1% of pixels are very dim (p99 < 80) — genuinely underexposed photos
    //    have nothing visible; dark screenshots always have readable text/icons above luminance 80
    // NOT gated by screenshot detection — dark photos get misidentified as screenshots
    // (lots of near-black pixels + few quantized colors) so the gate blocks them too
    private const val UNDEREXPOSED_AVG_BRIGHTNESS = 0.20f  // Only check p99 if overall quite dark
    private const val HIGHLIGHT_BRIGHTNESS = 80            // p99 below this = nothing even dimly visible (~31% luminance)

    private const val NOISE_THRESHOLD = 0.95f          // Above = extreme noise only (disabled anyway)
    private const val CONTRAST_THRESHOLD = 0.06f       // Below = very flat

    /**
     * Holds exposure metrics from histogram analysis.
     */
    data class ExposureResult(
        val score: Float,            // Overall exposure quality (0-1)
        val avgBrightness: Float,    // Mean brightness (0-1)
        val contrast: Float,         // Dynamic range (0-1)
        val p95: Int,                // 95th percentile luminance (used for contrast)
        val p99: Int                 // 99th percentile luminance (brightness of top 1% — for underexposed detection)
    )

    /**
     * Holds detailed sharpness metrics from the tiled Laplacian analysis.
     */
    data class SharpnessResult(
        val score: Float,            // Top-quartile sharpness score (0-1), only from textured tiles
        val centerScore: Float,      // Average sharpness of center 4 tiles (0-1)
        val edgeDensity: Float,      // Fraction of pixels that are edges (0-1)
        val maxTileVariance: Double,  // Highest single tile variance (for debugging)
        val meanTileVariance: Double, // Average tile variance (for debugging)
        val texturedTileCount: Int   // How many tiles had enough texture to judge sharpness
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
            QualityIssue.VERY_DARK -> "Too Dark"
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
    fun analyze(bitmap: Bitmap, debugLabel: String = ""): QualityResult {
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
        val exposureResult = computeExposure(lum, totalPixels)
        val exposure = exposureResult.score
        val avgBrightness = exposureResult.avgBrightness
        val contrast = exposureResult.contrast
        val p99 = exposureResult.p99
        val noise = computeNoise(lum, width, height)

        // Check if this looks like a screenshot (skip exposure checks for screenshots)
        val isLikelyScreenshot = isScreenshot(pixels, totalPixels)

        // Detect issues
        val issues = mutableListOf<QualityIssue>()

        // Underexposed — NOT screenshot-gated (dark photos get misidentified as screenshots)
        // The strict thresholds (avg < 0.20, p99 < 80) ensure only truly dark images are caught.
        // Dark screenshots always have readable text/icons pushing p99 well above 80.
        if (avgBrightness < UNDEREXPOSED_AVG_BRIGHTNESS && p99 < HIGHLIGHT_BRIGHTNESS) {
            issues.add(QualityIssue.UNDEREXPOSED)
        }

        // Brightness checks — screenshot-gated (white screenshots aren't overexposed)
        if (!isLikelyScreenshot) {
            if (avgBrightness > BRIGHT_THRESHOLD) {
                issues.add(QualityIssue.VERY_BRIGHT)
            } else if (avgBrightness > OVEREXPOSED_THRESHOLD) {
                issues.add(QualityIssue.OVEREXPOSED)
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
                    // Edge density rescue: if sharpness is above the hard floor (within 0.20 of threshold),
                    // real edges prove something is in focus (shallow DOF, smooth subjects like skin).
                    // Genuinely blurry photos have edge density well below 0.08.
                    val isBorderline = sharpness >= (SHARPNESS_THRESHOLD - 0.20f)
                    if (isBorderline && sharpnessResult.edgeDensity >= EDGE_DENSITY_BLURRY_THRESHOLD) {
                        if (DEBUG) {
                            android.util.Log.d("QualityAnalyzer",
                                "Borderline sharpness=$sharpness but edgeDensity=${sharpnessResult.edgeDensity} >= $EDGE_DENSITY_BLURRY_THRESHOLD, NOT flagging")
                        }
                    } else {
                        issues.add(QualityIssue.BLURRY)
                    }
                }
            } else if (sharpness < 0.65f && sharpnessResult.centerScore < CENTER_SHARPNESS_THRESHOLD) {
                // Overall sharpness is marginal AND center is blurry — likely misfocused.
                // Only applies when overall sharpness is below 0.65: if the photo is sharp
                // enough overall (e.g. 0.80+), the soft center is likely intentional
                // (bokeh, off-center composition, dark center area).
                if (DEBUG) {
                    android.util.Log.d("QualityAnalyzer",
                        "Center blur: overall=$sharpness < 0.65 AND centerScore=${sharpnessResult.centerScore} < $CENTER_SHARPNESS_THRESHOLD")
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
            val tag = if (debugLabel.isNotEmpty()) "[$debugLabel] " else ""
            android.util.Log.d("QualityAnalyzer",
                "${tag}sharpness=${"%.3f".format(sharpness)}, centerScore=${"%.3f".format(sharpnessResult.centerScore)}, " +
                "texturedTiles=${sharpnessResult.texturedTileCount}/16, edgeDensity=${"%.3f".format(sharpnessResult.edgeDensity)}, " +
                "maxTileVar=${"%.1f".format(sharpnessResult.maxTileVariance)}, meanTileVar=${"%.1f".format(sharpnessResult.meanTileVariance)}, " +
                "exposure=${"%.2f".format(exposure)}, brightness=${"%.2f".format(avgBrightness)}, " +
                "p99=$p99, contrast=${"%.2f".format(contrast)}, issues=$issues")
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
     * - Top tiles: 2 tiles must be sharp to pass, tolerant of bokeh/shallow DOF
     *   while still catching uniformly blurry photos (all tiles low)
     *
     * Center-weighted scoring: The inner 4 tiles (positions 1,1 / 1,2 / 2,1 / 2,2)
     * are scored separately. If a photo has sharp edges but a blurry center, it's
     * likely misfocused — the autofocus locked onto background/edges instead of the subject.
     *
     * Also counts edge pixels (|Laplacian| > threshold) to compute edge density,
     * used as a tiebreaker for borderline cases.
     */
    private fun computeSharpnessDetailed(lum: IntArray, width: Int, height: Int): SharpnessResult {
        if (width < 3 || height < 3) return SharpnessResult(0.5f, 0.5f, 0.0f, 0.0, 0.0, 0)

        val tileW = width / QUALITY_GRID_SIZE
        val tileH = height / QUALITY_GRID_SIZE
        if (tileW < 3 || tileH < 3) return SharpnessResult(0.5f, 0.5f, 0.0f, 0.0, 0.0, 0)

        val texturedTileVariances = mutableListOf<Double>()
        val texturedCenterTileVariances = mutableListOf<Double>()
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

                var lapSum = 0.0
                var lapSumSq = 0.0
                var lumSum = 0L
                var lumSumSq = 0L
                var count = 0

                // Laplacian kernel + luminance stats in a single pass
                for (y in max(startY, 1) until min(endY, height - 1)) {
                    for (x in max(startX, 1) until min(endX, width - 1)) {
                        val idx = y * width + x
                        val center = lum[idx]
                        val top = lum[idx - width]
                        val bottom = lum[idx + width]
                        val left = lum[idx - 1]
                        val right = lum[idx + 1]

                        val laplacian = (top + bottom + left + right - 4 * center).toDouble()
                        lapSum += laplacian
                        lapSumSq += laplacian * laplacian

                        // Accumulate luminance for texture check
                        lumSum += center
                        lumSumSq += center.toLong() * center

                        count++

                        // Count edge pixels for edge density
                        if (abs(laplacian) > EDGE_PIXEL_THRESHOLD) {
                            totalEdgePixels++
                        }
                        totalPixelsChecked++
                    }
                }

                if (count > 0) {
                    // Compute luminance stddev to check if tile has texture
                    val lumMean = lumSum.toDouble() / count
                    val lumVariance = (lumSumSq.toDouble() / count) - (lumMean * lumMean)
                    val lumStddev = sqrt(lumVariance.coerceAtLeast(0.0))

                    // Skip featureless tiles (uniform color: sky, walls, skin, etc.)
                    // These have near-zero Laplacian regardless of focus — can't judge sharpness
                    if (lumStddev >= TILE_TEXTURE_THRESHOLD) {
                        val lapMean = lapSum / count
                        val lapVariance = (lapSumSq / count) - (lapMean * lapMean)
                        texturedTileVariances.add(lapVariance)

                        if (tx in centerRange && ty in centerRange) {
                            texturedCenterTileVariances.add(lapVariance)
                        }
                    } else if (DEBUG) {
                        android.util.Log.d("QualityAnalyzer",
                            "Tile ($tx,$ty) skipped: lumStddev=${"%.1f".format(lumStddev)} < $TILE_TEXTURE_THRESHOLD (featureless)")
                    }
                }
            }
        }

        val texturedCount = texturedTileVariances.size

        // Not enough textured tiles to judge — assume sharp (it's a style choice, not blur)
        if (texturedCount < MIN_TEXTURED_TILES) {
            if (DEBUG) {
                android.util.Log.d("QualityAnalyzer",
                    "Only $texturedCount textured tiles (< $MIN_TEXTURED_TILES), skipping blur check")
            }
            return SharpnessResult(1.0f, 1.0f, 0.0f, 0.0, 0.0, texturedCount)
        }

        // Sort descending so best tiles are first
        texturedTileVariances.sortDescending()

        val maxVariance = texturedTileVariances[0]
        val meanVariance = texturedTileVariances.average()

        // Top quartile: average the best N textured tiles' normalized scores
        // Scale quartile count proportionally if fewer textured tiles than 16
        val topCount = min(TOP_QUARTILE_COUNT, texturedCount)
        var topQuartileSum = 0.0
        for (i in 0 until topCount) {
            topQuartileSum += (sqrt(texturedTileVariances[i]) / TILE_NORMALIZATION_DIVISOR).coerceIn(0.0, 1.0)
        }
        val score = (topQuartileSum / topCount).toFloat()

        // Center score: average of textured center tiles' normalized scores
        val centerScore = if (texturedCenterTileVariances.isNotEmpty()) {
            var centerSum = 0.0
            for (v in texturedCenterTileVariances) {
                centerSum += (sqrt(v) / TILE_NORMALIZATION_DIVISOR).coerceIn(0.0, 1.0)
            }
            (centerSum / texturedCenterTileVariances.size).toFloat()
        } else {
            // No textured center tiles — center is uniform, can't judge center focus
            // Default to passing so we don't false-positive on e.g. close-up of a white object
            1.0f
        }

        val edgeDensity = if (totalPixelsChecked > 0) {
            totalEdgePixels.toFloat() / totalPixelsChecked
        } else 0f

        if (DEBUG) {
            val tileScores = texturedTileVariances.map { "%.1f".format(it) }
            val centerScores = texturedCenterTileVariances.map { "%.1f".format(it) }
            android.util.Log.d("QualityAnalyzer",
                "Textured tiles: $texturedCount/16, variances (sorted): $tileScores, topQ=${"%.3f".format(score)}, " +
                "centerTiles=$centerScores, centerScore=${"%.3f".format(centerScore)}, " +
                "edgeDensity=${"%.3f".format(edgeDensity)}")
        }

        return SharpnessResult(
            score = score,
            centerScore = centerScore,
            edgeDensity = edgeDensity,
            maxTileVariance = maxVariance,
            meanTileVariance = meanVariance,
            texturedTileCount = texturedCount
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
    private fun detectMotionBlur(lum: IntArray, width: Int, height: Int, ratioThreshold: Double = MOTION_BLUR_RATIO_THRESHOLD): Boolean {
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

        return ratio > ratioThreshold
    }

    /**
     * Compute exposure metrics from histogram analysis.
     * Returns p99 (top 1% brightness) for underexposed detection and p5/p95 for contrast.
     */
    private fun computeExposure(lum: IntArray, pixelCount: Int): ExposureResult {
        if (pixelCount == 0) return ExposureResult(0.5f, 0.5f, 0.5f, 128, 128)

        val histogram = IntArray(256)
        var totalBrightness = 0L

        for (i in 0 until pixelCount) {
            val l = lum[i]
            histogram[l]++
            totalBrightness += l
        }

        val avgBrightness = (totalBrightness.toFloat() / pixelCount) / 255f

        // Find percentiles: p5/p95 for contrast, p99 for underexposed detection
        var cumulative = 0
        var p5 = 0
        var p95 = 255
        var p99 = 255
        for (i in 0..255) {
            cumulative += histogram[i]
            if (p5 == 0 && cumulative >= pixelCount * 0.05) p5 = i
            if (p95 == 255 && cumulative >= pixelCount * 0.95) p95 = i
            if (cumulative >= pixelCount * 0.99) {
                p99 = i
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

        return ExposureResult(exposureScore, avgBrightness, contrast, p95, p99)
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

        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Count near-pure white pixels (RGB > 245)
            if (r > 245 && g > 245 && b > 245) {
                pureWhiteCount++
            }

            // Quantize for color variety check (inline for speed)
            val quantized = ((r / 32) * 32 shl 16) or ((g / 32) * 32 shl 8) or ((b / 32) * 32)
            colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
        }

        val pureWhiteRatio = pureWhiteCount.toFloat() / totalPixels
        val uniqueColors = colorCounts.size

        // Screenshot if:
        // 1. >35% pure white (solid light background — photos rarely have this), OR
        // 2. Moderate white background (>25%) AND limited palette (<120 colors)
        // Note: palette-only check (uniqueColors < 60) was removed — at 32-step quantization,
        // dark/blurry photos have few color buckets too, causing false positives that
        // block blur detection. Dark-mode screenshots are missed but that's acceptable:
        // they're sharp (won't trigger blur) and have high p99 (won't trigger underexposed).
        val hasSolidWhiteBackground = pureWhiteRatio > 0.35f
        val hasModerateWhiteAndLimitedColors = pureWhiteRatio > 0.25f && uniqueColors < 120

        val isScreenshot = hasSolidWhiteBackground || hasModerateWhiteAndLimitedColors

        if (DEBUG && isScreenshot) {
            android.util.Log.d("QualityAnalyzer",
                "Screenshot detected: white=${"%.2f".format(pureWhiteRatio)}, colors=$uniqueColors")
        }

        return isScreenshot
    }
}
