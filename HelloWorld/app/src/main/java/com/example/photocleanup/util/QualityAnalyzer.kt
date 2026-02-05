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

    // Thresholds (balanced mode - catch most issues, some false positives OK)
    private const val SHARPNESS_THRESHOLD = 0.25f      // Below = blurry
    private const val DARK_THRESHOLD = 0.08f           // Below = very dark
    private const val BRIGHT_THRESHOLD = 0.92f         // Above = very bright
    private const val UNDEREXPOSED_THRESHOLD = 0.25f   // Below = underexposed
    private const val OVEREXPOSED_THRESHOLD = 0.75f    // Above = overexposed
    private const val NOISE_THRESHOLD = 0.6f           // Above = noisy
    private const val CONTRAST_THRESHOLD = 0.15f       // Below = low contrast

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

        // Detect issues
        val issues = mutableListOf<QualityIssue>()

        // Exposure issues (check first - these are most obvious)
        when {
            avgBrightness < DARK_THRESHOLD -> issues.add(QualityIssue.VERY_DARK)
            avgBrightness > BRIGHT_THRESHOLD -> issues.add(QualityIssue.VERY_BRIGHT)
            avgBrightness < UNDEREXPOSED_THRESHOLD -> issues.add(QualityIssue.UNDEREXPOSED)
            avgBrightness > OVEREXPOSED_THRESHOLD -> issues.add(QualityIssue.OVEREXPOSED)
        }

        // Sharpness (only check if not completely dark/bright)
        if (avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD) {
            if (sharpness < SHARPNESS_THRESHOLD) {
                issues.add(QualityIssue.BLURRY)
            }
        }

        // Noise (only meaningful if image has content)
        if (avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD && noise > NOISE_THRESHOLD) {
            issues.add(QualityIssue.NOISY)
        }

        // Contrast
        if (contrast < CONTRAST_THRESHOLD && avgBrightness in 0.2f..0.8f) {
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
}
