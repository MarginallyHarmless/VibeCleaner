package com.example.photocleanup.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analyzes photo quality to detect issues like blur, exposure problems, and noise.
 *
 * Quality scores range from 0.0 (bad) to 1.0 (good).
 */
object QualityAnalyzer {

    private const val DEBUG = false

    // Issue types
    enum class QualityIssue {
        BLURRY,           // Out of focus or motion blur
        VERY_DARK,        // Almost completely black
        VERY_BRIGHT,      // Almost completely white
        UNDEREXPOSED,     // Too dark but has content
        OVEREXPOSED,      // Blown highlights
        NOISY,            // High noise/grain
        LOW_CONTRAST      // Flat, washed out
    }

    // Thresholds (tuned to catch real issues, avoid false positives)
    private const val SHARPNESS_THRESHOLD = 0.38f      // Below = blurry (higher = catches more blur)
    private const val DARK_THRESHOLD = 0.04f           // Below = almost black (accidental shots)
    private const val BRIGHT_THRESHOLD = 0.96f         // Above = almost white (accidental shots)
    private const val UNDEREXPOSED_THRESHOLD = 0.06f   // Below = extremely dark (stricter - night photos OK)
    private const val OVEREXPOSED_THRESHOLD = 0.90f    // Above = very bright
    private const val NOISE_THRESHOLD = 0.95f          // Above = extreme noise only (disabled anyway)
    private const val CONTRAST_THRESHOLD = 0.06f       // Below = very flat

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
     * Expects a small bitmap (32x32 to 64x64) for performance.
     */
    fun analyze(bitmap: Bitmap): QualityResult {
        val sharpness = computeSharpness(bitmap)
        val (exposure, avgBrightness, contrast) = computeExposure(bitmap)
        val noise = computeNoise(bitmap)

        // Check if this looks like a screenshot (skip exposure checks for screenshots)
        val isLikelyScreenshot = isScreenshot(bitmap)

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

        // Sharpness (only check if not completely dark/bright, and not a screenshot)
        if (!isLikelyScreenshot && avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD) {
            if (sharpness < SHARPNESS_THRESHOLD) {
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
                "sharpness=$sharpness, exposure=$exposure, noise=$noise, " +
                "brightness=$avgBrightness, contrast=$contrast, issues=$issues")
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
     * Compute sharpness using Laplacian variance.
     * Sharp images have high variance in the Laplacian (edges).
     */
    private fun computeSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0.5f

        // Laplacian kernel: [0, 1, 0], [1, -4, 1], [0, 1, 0]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getLuminance(bitmap.getPixel(x, y))
                val top = getLuminance(bitmap.getPixel(x, y - 1))
                val bottom = getLuminance(bitmap.getPixel(x, y + 1))
                val left = getLuminance(bitmap.getPixel(x - 1, y))
                val right = getLuminance(bitmap.getPixel(x + 1, y))

                val laplacian = (top + bottom + left + right - 4 * center).toDouble()
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0.5f

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        // Normalize variance to 0-1 range
        // Empirically, sharp images have variance > 500, blurry < 100
        val normalized = (sqrt(variance) / 30.0).coerceIn(0.0, 1.0)
        return normalized.toFloat()
    }

    /**
     * Compute exposure metrics from histogram analysis.
     * Returns (exposureScore, avgBrightness, contrast)
     */
    private fun computeExposure(bitmap: Bitmap): Triple<Float, Float, Float> {
        val histogram = IntArray(256)
        var totalBrightness = 0L
        var pixelCount = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val lum = getLuminance(bitmap.getPixel(x, y))
                histogram[lum]++
                totalBrightness += lum
                pixelCount++
            }
        }

        if (pixelCount == 0) return Triple(0.5f, 0.5f, 0.5f)

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
    private fun computeNoise(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
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
                        val lum = getLuminance(bitmap.getPixel(x, y)).toDouble()
                        sum += lum
                        sumSq += lum * lum
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
     * Get luminance (perceived brightness) from a pixel color.
     */
    private fun getLuminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Standard luminance formula
        return ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt().coerceIn(0, 255)
    }

    /**
     * Detect if an image is likely a screenshot based on color patterns.
     * Screenshots typically have:
     * - Large areas of pure white or black (UI backgrounds)
     * - Very limited color palette compared to photos
     */
    private fun isScreenshot(bitmap: Bitmap): Boolean {
        val colorCounts = mutableMapOf<Int, Int>()
        val totalPixels = bitmap.width * bitmap.height
        var pureWhiteCount = 0
        var pureBlackCount = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Count near-pure white pixels (RGB > 245)
                if (r > 245 && g > 245 && b > 245) {
                    pureWhiteCount++
                }
                // Count near-pure black pixels (RGB < 10)
                if (r < 10 && g < 10 && b < 10) {
                    pureBlackCount++
                }

                // Quantize for color variety check
                val quantized = quantizeColor(pixel)
                colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
            }
        }

        val pureWhiteRatio = pureWhiteCount.toFloat() / totalPixels
        val pureBlackRatio = pureBlackCount.toFloat() / totalPixels
        val uniqueColors = colorCounts.size

        // Screenshot if:
        // 1. >35% pure white or black (solid background with text/icons), OR
        // 2. Limited color palette (<20 unique colors) indicating UI graphics, OR
        // 3. Moderate solid background (>25%) AND limited palette (<40 colors)
        val hasSolidBackground = pureWhiteRatio > 0.35f || pureBlackRatio > 0.35f
        val hasVeryLimitedPalette = uniqueColors < 20
        val hasModerateSolidAndLimitedColors =
            (pureWhiteRatio > 0.25f || pureBlackRatio > 0.25f) && uniqueColors < 40

        val isScreenshot = hasSolidBackground || hasVeryLimitedPalette || hasModerateSolidAndLimitedColors

        if (DEBUG && isScreenshot) {
            android.util.Log.d("QualityAnalyzer",
                "Screenshot detected: white=$pureWhiteRatio, black=$pureBlackRatio, colors=$uniqueColors")
        }

        return isScreenshot
    }

    /**
     * Quantize a color to reduce variations (groups similar colors together).
     */
    private fun quantizeColor(pixel: Int): Int {
        // Reduce each channel from 256 to 8 levels (divide by 32)
        val r = (Color.red(pixel) / 32) * 32
        val g = (Color.green(pixel) / 32) * 32
        val b = (Color.blue(pixel) / 32) * 32
        return Color.rgb(r, g, b)
    }
}
