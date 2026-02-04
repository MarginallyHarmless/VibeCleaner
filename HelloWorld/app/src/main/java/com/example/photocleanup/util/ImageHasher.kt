package com.example.photocleanup.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Utility class for computing perceptual image hashes using dHash and pHash algorithms.
 *
 * ## Algorithm Overview
 *
 * This class implements an **adaptive two-stage duplicate detection** system:
 *
 * ### Stage 1: dHash (Difference Hash) - Fast Candidate Selection
 * - Resizes image to 9x8 pixels
 * - Compares each pixel to its right neighbor (gradient direction)
 * - Produces a 64-bit hash representing horizontal gradients
 * - **Pros**: Very fast, good at catching obvious duplicates
 * - **Cons**: Can have false positives for visually different images with similar gradients
 *
 * ### Stage 2: pHash (Perceptual Hash) - DCT-Based Verification
 * - Resizes image to 32x32 pixels
 * - Applies 2D Discrete Cosine Transform (DCT)
 * - Extracts 8x8 low-frequency coefficients (captures overall structure)
 * - Generates hash based on median comparison
 * - **Pros**: More discriminative, better at rejecting false positives
 * - **Cons**: Slower (~3x), sensitive to geometric shifts (camera shake in bursts)
 *
 * ### Adaptive Logic
 * The key insight is that dHash and pHash have complementary strengths:
 * - **Burst shots**: Very low dHash (0-5) but pHash can be high due to camera shake
 * - **False positives**: Moderate dHash (7-12) but high pHash (different frequency profiles)
 *
 * Therefore, we use an **adaptive approach**:
 * ```
 * if dHash ≤ 6:  MATCH immediately (definitely duplicates, skip pHash)
 * if dHash 7-12: Verify with pHash ≤ 10 (questionable, need confirmation)
 * if dHash > 12: REJECT (too different)
 * ```
 *
 * ## Threshold Tuning Guide
 *
 * ### DHASH_CERTAIN (default: 6)
 * - Lower = fewer false positives, but may miss some burst shots
 * - Higher = catches more burst shots, but may include false positives
 * - Typical burst shot distances: 0-5
 * - Recommendation: Keep at 5-7
 *
 * ### DHASH_THRESHOLD (default: 12)
 * - Maximum dHash distance to consider for pHash verification
 * - Lower = faster (fewer pHash computations), but misses marginal duplicates
 * - Higher = catches more duplicates, but slower and more false positive candidates
 * - Recommendation: Keep at 10-14
 *
 * ### PHASH_THRESHOLD (default: 10)
 * - Confirmation threshold for questionable matches
 * - Lower = stricter, fewer false positives, but may miss valid duplicates
 * - Higher = more permissive, catches more duplicates, but more false positives
 * - Recommendation: Keep at 8-12
 *
 * ### Pre-filters
 * - ASPECT_RATIO_TOLERANCE (1.2): Rejects images with different shapes
 * - FILE_SIZE_TOLERANCE (2.0): Rejects images with vastly different sizes
 *
 * ## Performance Characteristics
 *
 * - **dHash computation**: ~5-10ms per image (9x8 resize + 64 comparisons)
 * - **pHash computation**: ~15-30ms per image (32x32 resize + DCT)
 * - **Comparison**: O(n²) pairs, but pHash only computed for dHash candidates
 * - **Memory**: Low (small bitmaps, no caching of full images)
 *
 * ## Debugging
 *
 * Enable DEBUG=true and check Logcat for "ImageHasher" tag:
 * - "MATCH (dHash certain)" = Fast path match, no pHash needed
 * - "MATCH (pHash verified)" = Confirmed by both stages
 * - "REJECTED by pHash" = False positive filtered out
 * - "NEAR-MISS dHash" = Close but didn't meet threshold
 *
 * ## Future Optimization Ideas
 *
 * 1. **Locality-Sensitive Hashing (LSH)**: For O(n) instead of O(n²) comparison
 * 2. **Hash bucketing**: Group similar hashes to reduce comparisons
 * 3. **GPU acceleration**: DCT can be parallelized on GPU
 * 4. **Incremental scanning**: Only compare new photos against existing hashes
 * 5. **aHash addition**: Average hash as third voting member for edge cases
 * 6. **Histogram pre-filter**: Quick color distribution check before hashing
 */
object ImageHasher {

    private const val TAG = "ImageHasher"
    private const val DEBUG = true  // Set to false to disable debug logging in production

    // dHash constants: Image resized to 9x8 to compute 8 horizontal differences per row (64 bits)
    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8

    // pHash constants
    private const val PHASH_SIZE = 32   // Resize to 32x32 for DCT
    private const val PHASH_SMALL = 8   // Extract 8x8 low-frequency coefficients

    // Color histogram constants
    private const val HISTOGRAM_BINS = 8  // 8 bins per RGB channel = 512 total bins
    const val COLOR_HISTOGRAM_THRESHOLD = 0.60  // Minimum color similarity (0-1) to proceed

    // Edge hash constants (Sobel-based structure detection)
    const val EDGE_HASH_THRESHOLD = 8           // Max Hamming distance for structure match (alternative to color path)
    const val PHASH_THRESHOLD_STRICT = 6        // Stricter pHash when using structure path only

    // High-confidence thresholds for geometric tolerance
    // When BOTH color AND structure pass strongly, we can tolerate more hash variance
    // (slight angle/position changes cause hash differences even in clearly related photos)
    private const val HIGH_CONFIDENCE_COLOR = 0.68    // Strong color match threshold
    private const val HIGH_CONFIDENCE_EDGE = 8        // Strong structure match threshold
    private const val DHASH_BOOST = 6                 // Extra dHash tolerance for high-confidence pairs
    private const val PHASH_BOOST = 6                 // Extra pHash tolerance for high-confidence pairs

    // Temporal proximity boost - photos taken very close in time are likely related
    // even with significant hash differences (burst shots, slight movement between shots)
    private const val TEMPORAL_CLOSE_SECONDS = 120L   // Photos within 2 minutes = close
    private const val TEMPORAL_BURST_SECONDS = 60L    // Photos within 1 minute = likely burst
    private const val TEMPORAL_RAPID_SECONDS = 30L    // Photos within 30 seconds = rapid burst (very likely same scene)
    private const val TEMPORAL_CLOSE_BOOST = 8        // Extra tolerance for temporally close photos
    private const val TEMPORAL_BURST_BOOST = 14       // Extra tolerance for likely burst shots
    private const val TEMPORAL_RAPID_BOOST = 20       // Very aggressive tolerance for rapid bursts

    // For rapid bursts, also relax entry thresholds (color/structure)
    // since temporal proximity is strong evidence of relationship
    private const val TEMPORAL_RAPID_COLOR_THRESHOLD = 0.50   // Lower color requirement for rapid bursts
    private const val TEMPORAL_RAPID_EDGE_THRESHOLD = 12      // Higher edge tolerance for rapid bursts

    // Color similarity tiers - higher color match = more confidence = more boost allowed
    // True duplicates (same scene) have very high color similarity (0.85+)
    // False positives (different subjects, similar clothes) have moderate similarity (0.60-0.75)
    private const val COLOR_VERY_HIGH = 0.72      // Same scene, same lighting - full temporal boost
    private const val COLOR_HIGH = 0.65           // Likely same scene - partial temporal boost
    // Below COLOR_HIGH: minimal temporal boost to avoid false positives

    // Adaptive two-stage thresholds (RESTORED to strict values)
    // If dHash ≤ DHASH_CERTAIN: definitely duplicates, skip pHash (fast path for burst shots)
    // If dHash ≤ DHASH_THRESHOLD: possible duplicates, verify with pHash
    const val DHASH_CERTAIN = 5         // RESTORED from 8 - stricter for certain matches
    const val DHASH_THRESHOLD = 12      // RESTORED from 16 - stricter candidate selection
    const val PHASH_THRESHOLD = 10      // RESTORED from 14 - stricter confirmation

    // Legacy threshold for backward compatibility
    const val SIMILARITY_THRESHOLD = DHASH_THRESHOLD

    // Pre-filter tolerances to reduce false positives
    // Tight tolerances prevent false positives from different image types
    private const val ASPECT_RATIO_TOLERANCE = 1.2f  // Max 1.2x difference (true duplicates have nearly identical ratios)
    private const val FILE_SIZE_TOLERANCE = 2.0f     // Max 2x difference (duplicates are usually very similar size)

    private fun logDebug(msg: String) {
        if (DEBUG) Log.d(TAG, msg)
    }

    /**
     * Compute the dHash for an image at the given URI.
     *
     * @param context Android context for image loading
     * @param imageUri Content URI of the image
     * @param imageLoader Optional Coil ImageLoader instance (for efficiency when batch processing)
     * @return The 64-bit dHash value, or null if the image could not be loaded
     */
    suspend fun computeHash(
        context: Context,
        imageUri: Uri,
        imageLoader: ImageLoader? = null
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val loader = imageLoader ?: ImageLoader.Builder(context)
                .crossfade(false)
                .build()

            // Load and resize image to 9x8 pixels
            // Note: This distorts non-square images, but the pre-filters (aspect ratio,
            // file size) prevent false positives from differently-shaped images matching.
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(Size(HASH_WIDTH, HASH_HEIGHT))
                .allowHardware(false) // Need software bitmap for pixel access
                .build()

            val result = loader.execute(request)
            if (result !is SuccessResult) {
                return@withContext null
            }

            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: return@withContext null

            // Ensure bitmap is the right size
            val scaledBitmap = if (bitmap.width != HASH_WIDTH || bitmap.height != HASH_HEIGHT) {
                Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
            } else {
                bitmap
            }

            try {
                computeHashFromBitmap(scaledBitmap)
            } finally {
                // Recycle scaled bitmap if it's different from original
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                // Note: Don't recycle original bitmap as Coil may reuse it
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute dHash from a bitmap that is already resized to 9x8.
     */
    private fun computeHashFromBitmap(bitmap: Bitmap): Long {
        var hash = 0L
        var bit = 0

        // For each row, compare each pixel to its right neighbor
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val leftPixel = bitmap.getPixel(x, y)
                val rightPixel = bitmap.getPixel(x + 1, y)

                // Convert to grayscale using luminance formula
                val leftGray = toGrayscale(leftPixel)
                val rightGray = toGrayscale(rightPixel)

                // Set bit if left > right (gradient goes dark)
                if (leftGray > rightGray) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }

        return hash
    }

    /**
     * Convert a pixel color to grayscale using luminance formula.
     * This gives more weight to green (human eye is most sensitive to green).
     */
    private fun toGrayscale(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Standard luminance weights: 0.299 R + 0.587 G + 0.114 B
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    /**
     * Compute the pHash (perceptual hash) for an image using Discrete Cosine Transform.
     *
     * pHash is more robust than dHash for detecting near-duplicates because it analyzes
     * frequency components rather than simple gradient differences. This makes it better
     * at distinguishing between similar-looking but different images.
     *
     * Algorithm:
     * 1. Resize image to 32x32 pixels
     * 2. Convert to grayscale
     * 3. Apply 2D DCT (Discrete Cosine Transform)
     * 4. Extract top-left 8x8 low-frequency coefficients (skip DC component)
     * 5. Compute median of these coefficients
     * 6. Generate 64-bit hash: bit = 1 if coefficient > median, else 0
     *
     * @param context Android context for image loading
     * @param imageUri Content URI of the image
     * @param imageLoader Optional Coil ImageLoader instance (for efficiency when batch processing)
     * @return The 64-bit pHash value, or null if the image could not be loaded
     */
    suspend fun computePHash(
        context: Context,
        imageUri: Uri,
        imageLoader: ImageLoader? = null
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val loader = imageLoader ?: ImageLoader.Builder(context)
                .crossfade(false)
                .build()

            // Load and resize image to 32x32 pixels
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(Size(PHASH_SIZE, PHASH_SIZE))
                .allowHardware(false) // Need software bitmap for pixel access
                .build()

            val result = loader.execute(request)
            if (result !is SuccessResult) {
                return@withContext null
            }

            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: return@withContext null

            // Ensure bitmap is the right size
            val scaledBitmap = if (bitmap.width != PHASH_SIZE || bitmap.height != PHASH_SIZE) {
                Bitmap.createScaledBitmap(bitmap, PHASH_SIZE, PHASH_SIZE, true)
            } else {
                bitmap
            }

            try {
                computePHashFromBitmap(scaledBitmap)
            } finally {
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute pHash from a bitmap that is already resized to 32x32.
     */
    private fun computePHashFromBitmap(bitmap: Bitmap): Long {
        // Convert to grayscale matrix
        val pixels = Array(PHASH_SIZE) { DoubleArray(PHASH_SIZE) }
        for (y in 0 until PHASH_SIZE) {
            for (x in 0 until PHASH_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                pixels[y][x] = toGrayscale(pixel).toDouble()
            }
        }

        // Apply 2D DCT
        val dct = applyDCT(pixels)

        // Extract top-left 8x8 low-frequency coefficients (skip DC at 0,0)
        val lowFreq = mutableListOf<Double>()
        for (y in 0 until PHASH_SMALL) {
            for (x in 0 until PHASH_SMALL) {
                if (x == 0 && y == 0) continue // Skip DC component
                lowFreq.add(dct[y][x])
            }
        }

        // Compute median
        val sorted = lowFreq.sorted()
        val median = sorted[sorted.size / 2]

        // Generate hash: bit = 1 if coefficient > median
        var hash = 0L
        var bit = 0
        for (y in 0 until PHASH_SMALL) {
            for (x in 0 until PHASH_SMALL) {
                if (x == 0 && y == 0) continue // Skip DC component
                if (dct[y][x] > median) {
                    hash = hash or (1L shl bit)
                }
                bit++
                if (bit >= 64) break
            }
            if (bit >= 64) break
        }

        return hash
    }

    /**
     * Apply 2D Discrete Cosine Transform to the input matrix.
     *
     * DCT converts spatial domain data to frequency domain, allowing us to
     * extract low-frequency components that represent the overall structure
     * of the image (which are most important for perceptual similarity).
     */
    private fun applyDCT(input: Array<DoubleArray>): Array<DoubleArray> {
        val n = input.size
        val output = Array(n) { DoubleArray(n) }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (i in 0 until n) {
                    for (j in 0 until n) {
                        sum += input[i][j] *
                            cos((2 * i + 1) * u * PI / (2 * n)) *
                            cos((2 * j + 1) * v * PI / (2 * n))
                    }
                }
                val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                output[u][v] = 0.25 * cu * cv * sum
            }
        }

        return output
    }

    /**
     * Compute pHash AND color histogram from the same 32x32 bitmap.
     * This is more efficient than loading the image twice.
     *
     * Color histogram: 8 bins per R/G/B channel (512 bins total).
     * The histogram captures color distribution which is very different between
     * photos of different scenes (blue sky vs orange sunset vs dark interior).
     *
     * @param context Android context for image loading
     * @param imageUri Content URI of the image
     * @param imageLoader Optional Coil ImageLoader instance
     * @return Triple of (pHash, colorHistogram, bitmap) or null if loading failed
     * @deprecated Use computeAllHashes instead which also computes edge hash
     */
    suspend fun computePHashAndHistogram(
        context: Context,
        imageUri: Uri,
        imageLoader: ImageLoader? = null
    ): Triple<Long, IntArray, Bitmap?>? = withContext(Dispatchers.IO) {
        val result = computeAllHashes(context, imageUri, imageLoader) ?: return@withContext null
        Triple(result.first, result.third, null)
    }

    /**
     * Data class to hold all computed hashes from a single bitmap load.
     */
    data class AllHashes(
        val pHash: Long,
        val edgeHash: Long,
        val colorHistogram: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AllHashes
            return pHash == other.pHash && edgeHash == other.edgeHash && colorHistogram.contentEquals(other.colorHistogram)
        }
        override fun hashCode(): Int = 31 * (31 * pHash.hashCode() + edgeHash.hashCode()) + colorHistogram.contentHashCode()
    }

    /**
     * Compute pHash, edge hash, AND color histogram from the same 32x32 bitmap.
     * This is more efficient than loading the image multiple times.
     *
     * - pHash: DCT-based perceptual hash for overall image structure
     * - edgeHash: Sobel-based edge hash for brightness-invariant structure matching
     * - colorHistogram: RGB histogram for color distribution pre-filtering
     *
     * The edge hash allows detecting duplicates with different exposure/lighting
     * that fail the color histogram check (e.g., same scene sunny vs overcast).
     *
     * @param context Android context for image loading
     * @param imageUri Content URI of the image
     * @param imageLoader Optional Coil ImageLoader instance
     * @return Triple of (pHash, edgeHash, colorHistogram) or null if loading failed
     */
    suspend fun computeAllHashes(
        context: Context,
        imageUri: Uri,
        imageLoader: ImageLoader? = null
    ): Triple<Long, Long, IntArray>? = withContext(Dispatchers.IO) {
        try {
            val loader = imageLoader ?: ImageLoader.Builder(context)
                .crossfade(false)
                .build()

            // Load and resize image to 32x32 pixels
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(coil.size.Size(PHASH_SIZE, PHASH_SIZE))
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            if (result !is SuccessResult) {
                return@withContext null
            }

            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: return@withContext null

            val scaledBitmap = if (bitmap.width != PHASH_SIZE || bitmap.height != PHASH_SIZE) {
                Bitmap.createScaledBitmap(bitmap, PHASH_SIZE, PHASH_SIZE, true)
            } else {
                bitmap
            }

            try {
                // Compute pHash (DCT-based)
                val pHash = computePHashFromBitmap(scaledBitmap)

                // Compute edge hash (Sobel-based, brightness-invariant)
                val edgeHash = computeEdgeHashFromBitmap(scaledBitmap)

                // Compute color histogram from same bitmap
                val histogram = computeColorHistogramFromBitmap(scaledBitmap)

                Triple(pHash, edgeHash, histogram)
            } finally {
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute color histogram from a bitmap.
     * Uses 8 bins per RGB channel (512 total bins).
     */
    private fun computeColorHistogramFromBitmap(bitmap: Bitmap): IntArray {
        val totalBins = HISTOGRAM_BINS * HISTOGRAM_BINS * HISTOGRAM_BINS
        val histogram = IntArray(totalBins)

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Map 0-255 to 0-7 bins
                val rBin = (r * HISTOGRAM_BINS) / 256
                val gBin = (g * HISTOGRAM_BINS) / 256
                val bBin = (b * HISTOGRAM_BINS) / 256

                // 3D index: r * 64 + g * 8 + b
                val index = rBin * HISTOGRAM_BINS * HISTOGRAM_BINS + gBin * HISTOGRAM_BINS + bBin
                histogram[index]++
            }
        }

        return histogram
    }

    /**
     * Compute edge magnitudes using Sobel edge detection.
     *
     * Sobel operators detect edges by computing horizontal and vertical gradients:
     * - Gx (horizontal): [-1 0 1; -2 0 2; -1 0 1]
     * - Gy (vertical):   [-1 -2 -1; 0 0 0; 1 2 1]
     *
     * Edge magnitude = sqrt(Gx² + Gy²)
     *
     * @param bitmap Grayscale input bitmap
     * @return 2D array of edge magnitudes (size - 2 in each dimension due to borders)
     */
    private fun computeEdgeMagnitudes(bitmap: Bitmap): Array<DoubleArray> {
        val width = bitmap.width
        val height = bitmap.height

        // Convert bitmap to grayscale array
        val gray = Array(height) { y ->
            DoubleArray(width) { x ->
                toGrayscale(bitmap.getPixel(x, y)).toDouble()
            }
        }

        // Output is smaller due to 3x3 kernel borders
        val outHeight = height - 2
        val outWidth = width - 2
        val edges = Array(outHeight) { DoubleArray(outWidth) }

        // Apply Sobel operators
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Horizontal gradient (Gx)
                val gx = (-1 * gray[y-1][x-1] + 1 * gray[y-1][x+1] +
                         -2 * gray[y][x-1]   + 2 * gray[y][x+1] +
                         -1 * gray[y+1][x-1] + 1 * gray[y+1][x+1])

                // Vertical gradient (Gy)
                val gy = (-1 * gray[y-1][x-1] - 2 * gray[y-1][x] - 1 * gray[y-1][x+1] +
                          1 * gray[y+1][x-1] + 2 * gray[y+1][x] + 1 * gray[y+1][x+1])

                // Magnitude
                edges[y-1][x-1] = sqrt(gx * gx + gy * gy)
            }
        }

        return edges
    }

    /**
     * Compute 64-bit edge hash from a bitmap using Sobel edge detection.
     *
     * This hash captures the structural pattern of the image (edges, contours)
     * and is invariant to brightness/exposure changes. Same composition with
     * different lighting will produce similar edge hashes.
     *
     * Algorithm:
     * 1. Apply Sobel edge detection to get edge magnitudes
     * 2. Divide edge map into 8x8 grid (64 cells)
     * 3. Compute average edge magnitude per cell
     * 4. Find median of cell averages
     * 5. Generate hash: bit = 1 if cell average > median
     *
     * @param bitmap Input bitmap (should be 32x32 for consistency with pHash)
     * @return 64-bit edge hash
     */
    private fun computeEdgeHashFromBitmap(bitmap: Bitmap): Long {
        // Get edge magnitudes (30x30 for 32x32 input due to Sobel borders)
        val edges = computeEdgeMagnitudes(bitmap)
        val edgeHeight = edges.size
        val edgeWidth = if (edgeHeight > 0) edges[0].size else 0

        if (edgeHeight < 8 || edgeWidth < 8) {
            return 0L // Bitmap too small
        }

        // Divide into 8x8 grid and compute average per cell
        val cellHeight = edgeHeight / 8
        val cellWidth = edgeWidth / 8
        val cellAverages = DoubleArray(64)

        for (cellY in 0 until 8) {
            for (cellX in 0 until 8) {
                var sum = 0.0
                var count = 0

                val startY = cellY * cellHeight
                val startX = cellX * cellWidth
                val endY = if (cellY == 7) edgeHeight else startY + cellHeight
                val endX = if (cellX == 7) edgeWidth else startX + cellWidth

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        sum += edges[y][x]
                        count++
                    }
                }

                cellAverages[cellY * 8 + cellX] = if (count > 0) sum / count else 0.0
            }
        }

        // Find median
        val sorted = cellAverages.sorted()
        val median = sorted[sorted.size / 2]

        // Generate hash: bit = 1 if cell > median
        var hash = 0L
        for (i in 0 until 64) {
            if (cellAverages[i] > median) {
                hash = hash or (1L shl i)
            }
        }

        return hash
    }

    /**
     * Compare two color histograms using normalized intersection.
     * This measures the overlap between two color distributions.
     *
     * @param h1 First histogram
     * @param h2 Second histogram
     * @return Similarity score from 0.0 (completely different) to 1.0 (identical)
     */
    fun histogramIntersection(h1: IntArray, h2: IntArray): Double {
        if (h1.isEmpty() || h2.isEmpty() || h1.size != h2.size) {
            return 1.0 // Skip filter if histograms invalid
        }

        var intersection = 0L
        var sum1 = 0L
        var sum2 = 0L

        for (i in h1.indices) {
            intersection += min(h1[i], h2[i])
            sum1 += h1[i]
            sum2 += h2[i]
        }

        // Normalize by the smaller histogram sum (prevents penalizing different exposures)
        val minSum = min(sum1, sum2)
        return if (minSum > 0) intersection.toDouble() / minSum else 1.0
    }

    /**
     * Encode histogram to Base64 string for database storage.
     * Uses a compact format: each int as 2 bytes (max count ~1024 for 32x32 image).
     */
    fun encodeHistogram(histogram: IntArray): String {
        if (histogram.isEmpty()) return ""

        val bytes = ByteArray(histogram.size * 2)
        for (i in histogram.indices) {
            val value = histogram[i].coerceIn(0, 65535)
            bytes[i * 2] = (value shr 8).toByte()
            bytes[i * 2 + 1] = (value and 0xFF).toByte()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Decode histogram from Base64 string.
     */
    fun decodeHistogram(encoded: String): IntArray {
        if (encoded.isEmpty()) return IntArray(0)

        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val histogram = IntArray(bytes.size / 2)
            for (i in histogram.indices) {
                val high = (bytes[i * 2].toInt() and 0xFF) shl 8
                val low = bytes[i * 2 + 1].toInt() and 0xFF
                histogram[i] = high or low
            }
            histogram
        } catch (e: Exception) {
            IntArray(0)
        }
    }

    /**
     * Compute the Hamming distance between two hashes.
     * The Hamming distance is the number of bit positions where the hashes differ.
     *
     * @param hash1 First hash value
     * @param hash2 Second hash value
     * @return Number of different bits (0-64). Lower = more similar.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        // XOR the hashes - bits that differ will be 1
        val xor = hash1 xor hash2
        // Count the number of 1 bits
        return java.lang.Long.bitCount(xor)
    }

    /**
     * Check if two hashes represent similar images.
     *
     * @param hash1 First hash value
     * @param hash2 Second hash value
     * @param threshold Maximum allowed Hamming distance (default: SIMILARITY_THRESHOLD)
     * @return true if images are similar within the threshold
     */
    fun areSimilar(hash1: Long, hash2: Long, threshold: Int = SIMILARITY_THRESHOLD): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }

    /**
     * Find all similar hash pairs from a list of hashes.
     * Uses chunked processing to avoid memory exhaustion on large galleries.
     *
     * @param hashes List of (uri, hash) pairs
     * @param threshold Maximum Hamming distance to consider as duplicates
     * @return List of pairs of URIs that are similar
     */
    fun findSimilarPairs(
        hashes: List<Pair<String, Long>>,
        threshold: Int = SIMILARITY_THRESHOLD
    ): List<Pair<String, String>> {
        val similar = mutableListOf<Pair<String, String>>()
        val size = hashes.size

        // Process in chunks to allow periodic garbage collection
        // but still do full O(n²) comparison for accuracy
        for (i in 0 until size) {
            for (j in i + 1 until size) {
                val distance = hammingDistance(hashes[i].second, hashes[j].second)
                if (distance <= threshold) {
                    similar.add(Pair(hashes[i].first, hashes[j].first))
                }
            }

            // Periodic garbage collection hint for large galleries
            if (i % 500 == 0 && i > 0) {
                System.gc()
            }
        }

        return similar
    }

    /**
     * Data class containing photo metadata for enhanced duplicate comparison.
     * Includes dimensions, file size, color histogram, edge hash, and both hash types for multi-stage verification.
     */
    data class PhotoMetadata(
        val uri: String,
        val hash: Long,              // dHash for fast first-pass
        val pHash: Long,             // pHash for strict confirmation
        val edgeHash: Long,          // Sobel edge hash for brightness-invariant structure matching
        val colorHistogram: IntArray, // RGB color histogram for pre-filtering
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val dateAdded: Long = 0      // Timestamp in seconds for time-window clustering
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PhotoMetadata
            return uri == other.uri
        }

        override fun hashCode(): Int = uri.hashCode()
    }

    /**
     * Configuration for burst/time-window based duplicate detection.
     *
     * Time-window clustering dramatically improves performance by only comparing
     * photos taken within a short time window of each other. This is based on the
     * observation that burst shots and near-duplicates typically happen within
     * seconds or minutes of each other.
     *
     * Thresholds are now strict because the color histogram pre-filter provides
     * additional validation - different colored photos are rejected before hash comparison.
     *
     * DUAL-PATH ENTRY: Photos can enter comparison via either:
     * 1. Color path: colorSim >= colorThreshold (0.60) - similar colors
     * 2. Structure path: edgeDist <= edgeHashThreshold (8) - same structural pattern
     *
     * This allows detecting duplicates with different exposure/lighting that fail
     * color matching but have identical structural patterns.
     */
    data class BurstDetectionConfig(
        val windowSizeSeconds: Long = 300,      // 5 minutes
        val windowStepSeconds: Long = 150,      // 2.5 min overlap (catches boundary cases)
        val dHashCertain: Int = 5,              // RESTORED from 8 - strict for certain matches
        val dHashThreshold: Int = 12,           // RESTORED from 16 - strict candidate selection
        val pHashThreshold: Int = 10,           // RESTORED from 14 - strict confirmation
        val pHashThresholdStrict: Int = 6,      // Stricter pHash when using structure path only
        val fileSizeTolerance: Float = 2.0f,    // RESTORED from 3.0 - strict size filter
        val colorThreshold: Double = 0.60,      // Minimum color similarity for color path
        val edgeHashThreshold: Int = 8          // Max edge hash distance for structure path
    )

    /**
     * Find similar photo pairs using adaptive multi-stage verification with dual-path entry.
     *
     * This uses a multi-layer filtering approach for speed and precision:
     *
     * ## DUAL-PATH ENTRY (NEW)
     * Photos can proceed to hash comparison via EITHER path:
     * - **Color path**: colorSim >= colorThreshold (0.60) - similar color distributions
     * - **Structure path**: edgeDist <= edgeHashThreshold (8) - same structural pattern
     *
     * This solves the false negative problem where legitimate duplicates with different
     * exposure/lighting fail color matching. Edge patterns are brightness-invariant:
     * same scene sunny vs overcast will have the same edges but different colors.
     *
     * When using structure path only (color failed), pHash threshold is stricter (6 vs 10)
     * to compensate for reduced confidence.
     *
     * ## FILTER LAYERS
     * 0. Dual-path entry: Either color OR structure match
     * 1. Aspect ratio filter: Reject different aspect ratios
     * 2. File size filter: Reject vastly different file sizes
     * 3. dHash check with adaptive pHash:
     *    - dHash ≤ 5: MATCH immediately (very similar, definitely duplicates - burst shots)
     *    - dHash 6-12: Verify with pHash (threshold depends on entry path)
     *    - dHash > 12: REJECT (too different)
     *
     * @param photos List of PhotoMetadata containing hashes, edge hash, and histogram
     * @param dHashCertain dHash threshold for certain matches (skip pHash)
     * @param dHashThreshold Maximum dHash for candidate selection
     * @param pHashThreshold pHash threshold for color-path matches
     * @param fileSizeTolerance Maximum ratio between file sizes
     * @param colorThreshold Minimum color histogram similarity for color path
     * @param edgeHashThreshold Maximum edge hash distance for structure path
     * @param pHashThresholdStrict Stricter pHash threshold for structure-only path
     * @return List of pairs of URIs that are similar
     */
    fun findSimilarPairsEnhanced(
        photos: List<PhotoMetadata>,
        dHashCertain: Int = DHASH_CERTAIN,
        dHashThreshold: Int = DHASH_THRESHOLD,
        pHashThreshold: Int = PHASH_THRESHOLD,
        fileSizeTolerance: Float = FILE_SIZE_TOLERANCE,
        colorThreshold: Double = COLOR_HISTOGRAM_THRESHOLD,
        edgeHashThreshold: Int = EDGE_HASH_THRESHOLD,
        pHashThresholdStrict: Int = PHASH_THRESHOLD_STRICT
    ): List<Pair<String, String>> {
        val similar = mutableListOf<Pair<String, String>>()
        var checkedPairs = 0
        var passedColorPath = 0
        var passedStructurePath = 0
        var passedBothPaths = 0
        var rejectedBothPaths = 0
        var passedAspectRatio = 0
        var passedFileSize = 0
        var matchedByDHashAlone = 0
        var passedDHash = 0
        var rejectedByPHash = 0
        var matchedWithPHash = 0

        logDebug("Starting multi-stage comparison of ${photos.size} photos")
        logDebug("  Thresholds: color≥$colorThreshold, edge≤$edgeHashThreshold, dHash≤$dHashCertain=certain, dHash≤$dHashThreshold+pHash≤$pHashThreshold/$pHashThresholdStrict=verify")

        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                checkedPairs++
                val p1 = photos[i]
                val p2 = photos[j]

                // TEMPORAL PROXIMITY DETECTION (check first - affects entry thresholds)
                // Photos taken very close in time are likely related (burst shots, slight
                // movement between shots). We use this as an additional confidence signal.
                val timeDiffSeconds = kotlin.math.abs(p1.dateAdded - p2.dateAdded)
                val isTemporalRapid = timeDiffSeconds <= TEMPORAL_RAPID_SECONDS
                val isTemporalBurst = timeDiffSeconds <= TEMPORAL_BURST_SECONDS
                val isTemporalClose = timeDiffSeconds <= TEMPORAL_CLOSE_SECONDS

                // Layer 0: DUAL-PATH ENTRY - Either color match OR structure match
                // For rapid bursts (≤5 seconds), use relaxed entry thresholds since
                // temporal proximity is strong evidence of relationship
                val colorSim = histogramIntersection(p1.colorHistogram, p2.colorHistogram)
                val edgeDist = hammingDistance(p1.edgeHash, p2.edgeHash)

                val effectiveColorThreshold = if (isTemporalRapid) TEMPORAL_RAPID_COLOR_THRESHOLD else colorThreshold
                val effectiveEdgeThreshold = if (isTemporalRapid) TEMPORAL_RAPID_EDGE_THRESHOLD else edgeHashThreshold

                val passedColorCheck = colorSim >= effectiveColorThreshold
                val passedStructureCheck = edgeDist <= effectiveEdgeThreshold

                // Must pass at least one path to continue
                if (!passedColorCheck && !passedStructureCheck) {
                    rejectedBothPaths++
                    if (DEBUG && (colorSim > 0.40 || edgeDist <= 14 || isTemporalClose)) {
                        val timeInfo = if (isTemporalClose) ", time=${timeDiffSeconds}s" else ""
                        logDebug("REJECTED (neither path): color=${"%.2f".format(colorSim)}, edge=$edgeDist$timeInfo, ${p1.uri.takeLast(30)} <-> ${p2.uri.takeLast(30)}")
                    }
                    continue
                }

                // Track which path(s) were used
                when {
                    passedColorCheck && passedStructureCheck -> passedBothPaths++
                    passedColorCheck -> passedColorPath++
                    else -> passedStructurePath++
                }

                // HIGH-CONFIDENCE DETECTION
                // When BOTH color AND structure match strongly, we have high confidence
                // these are related photos (same scene). In this case, we can tolerate
                // more hash variance to catch slight angle/position changes.
                val isHighConfidence = colorSim >= HIGH_CONFIDENCE_COLOR && edgeDist <= HIGH_CONFIDENCE_EDGE

                // Layer 1: Aspect ratio pre-filter (fast, O(1))
                if (!areAspectRatiosCompatible(p1.width, p1.height, p2.width, p2.height)) {
                    continue
                }
                passedAspectRatio++

                // Layer 2: File size pre-filter (fast, O(1))
                if (!areFileSizesCompatible(p1.fileSize, p2.fileSize, fileSizeTolerance)) {
                    continue
                }
                passedFileSize++

                // Calculate effective thresholds with boosts:
                // 1. High-confidence boost: when color + structure both match strongly
                // 2. Temporal boost: SCALED BY COLOR SIMILARITY
                //    - Very high color (0.82+): full temporal boost (same scene confirmed)
                //    - High color (0.72-0.82): partial temporal boost (likely same scene)
                //    - Moderate color (<0.72): minimal boost (could be different subjects)
                //    This prevents false positives from different subjects with similar colors
                var thresholdBoost = 0
                if (isHighConfidence) thresholdBoost += DHASH_BOOST

                // Scale temporal boost by color similarity
                val temporalBoostMultiplier = when {
                    colorSim >= COLOR_VERY_HIGH -> 1.0    // Full boost - definitely same scene
                    colorSim >= COLOR_HIGH -> 0.5         // Half boost - probably same scene
                    else -> 0.0                           // No temporal boost - could be false positive
                }

                val rawTemporalBoost = when {
                    isTemporalRapid -> TEMPORAL_RAPID_BOOST
                    isTemporalBurst -> TEMPORAL_BURST_BOOST
                    isTemporalClose -> TEMPORAL_CLOSE_BOOST
                    else -> 0
                }
                thresholdBoost += (rawTemporalBoost * temporalBoostMultiplier).toInt()

                val effectiveDHashCertain = dHashCertain + thresholdBoost
                val effectiveDHashThreshold = dHashThreshold + thresholdBoost

                // Layer 3: dHash check
                val dHashDist = hammingDistance(p1.hash, p2.hash)

                // Build boost info string for logging
                val boostReasons = mutableListOf<String>()
                if (isHighConfidence) boostReasons.add("high-conf")
                if (rawTemporalBoost > 0) {
                    val timeLabel = when {
                        isTemporalRapid -> "rapid"
                        isTemporalBurst -> "burst"
                        else -> "close"
                    }
                    val multiplierLabel = when {
                        temporalBoostMultiplier >= 1.0 -> "full"
                        temporalBoostMultiplier >= 0.5 -> "half"
                        else -> "none"
                    }
                    boostReasons.add("$timeLabel=${timeDiffSeconds}s×$multiplierLabel")
                }
                val boostInfo = if (boostReasons.isNotEmpty()) " [${boostReasons.joinToString("+")}]" else ""

                // Fast path: very low dHash = definitely duplicates (burst shots, etc.)
                if (dHashDist <= effectiveDHashCertain) {
                    matchedByDHashAlone++
                    logDebug("MATCH (dHash certain): dHash=$dHashDist (threshold=$effectiveDHashCertain), color=${"%.2f".format(colorSim)}, edge=$edgeDist$boostInfo, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    similar.add(Pair(p1.uri, p2.uri))
                    continue
                }

                // Reject if dHash too high
                if (dHashDist > effectiveDHashThreshold) {
                    if (dHashDist <= 24) {
                        logDebug("NEAR-MISS dHash: dist=$dHashDist (threshold=$effectiveDHashThreshold), color=${"%.2f".format(colorSim)}, edge=$edgeDist$boostInfo, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    }
                    continue
                }
                passedDHash++

                // Layer 4: pHash confirmation for questionable matches
                // Threshold depends on entry path AND confidence level:
                // - Temporal/high confidence: boosted threshold (tolerates geometric variance)
                // - Structure-only path: stricter threshold (less certainty)
                // - Color path: normal threshold
                // pHash boost also scaled by color similarity (same as dHash boost)
                var pHashBoost = 0
                if (isHighConfidence) pHashBoost += PHASH_BOOST
                pHashBoost += (rawTemporalBoost * temporalBoostMultiplier).toInt()

                val effectivePHashThreshold = when {
                    pHashBoost > 0 -> pHashThreshold + pHashBoost  // Boosted: relaxed
                    passedStructureCheck && !passedColorCheck -> pHashThresholdStrict  // Structure-only: strict
                    else -> pHashThreshold  // Normal
                }

                val pHashDist = hammingDistance(p1.pHash, p2.pHash)
                if (pHashDist > effectivePHashThreshold) {
                    rejectedByPHash++
                    logDebug("REJECTED by pHash: dHash=$dHashDist, pHash=$pHashDist (threshold=$effectivePHashThreshold), color=${"%.2f".format(colorSim)}, edge=$edgeDist$boostInfo, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    continue
                }

                // All stages passed - confirmed duplicate
                matchedWithPHash++
                val pathInfo = if (passedColorCheck && passedStructureCheck) "both" else if (passedColorCheck) "color" else "structure"
                logDebug("MATCH (pHash verified): dHash=$dHashDist, pHash=$pHashDist, color=${"%.2f".format(colorSim)}, edge=$edgeDist, path=$pathInfo$boostInfo, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                similar.add(Pair(p1.uri, p2.uri))
            }

            // Periodic garbage collection hint for large galleries
            if (i % 500 == 0 && i > 0) {
                System.gc()
            }
        }

        val totalMatched = matchedByDHashAlone + matchedWithPHash
        val totalPassed = passedColorPath + passedStructurePath + passedBothPaths
        logDebug("Summary: checked=$checkedPairs, passedDualPath=$totalPassed (color=$passedColorPath, structure=$passedStructurePath, both=$passedBothPaths), rejectedBoth=$rejectedBothPaths")
        logDebug("  After filters: passedAR=$passedAspectRatio, passedSize=$passedFileSize, passedDHash=$passedDHash")
        logDebug("  Matches: $totalMatched total ($matchedByDHashAlone by dHash alone, $matchedWithPHash verified by pHash)")
        logDebug("  Filtered: rejectedByPHash=$rejectedByPHash")
        return similar
    }

    /**
     * Check if two images have compatible aspect ratios.
     * A wide panorama (3:1) should never match a tall portrait (1:2).
     *
     * @return true if aspect ratios are within tolerance
     */
    private fun areAspectRatiosCompatible(w1: Int, h1: Int, w2: Int, h2: Int): Boolean {
        // Skip filter if dimensions are invalid or zero
        if (w1 <= 0 || h1 <= 0 || w2 <= 0 || h2 <= 0) return true

        // Normalize aspect ratios to always be >= 1 (wider dimension / narrower)
        val r1 = if (w1 > h1) w1.toFloat() / h1 else h1.toFloat() / w1
        val r2 = if (w2 > h2) w2.toFloat() / h2 else h2.toFloat() / w2

        // Check if the ratio of ratios is within tolerance
        val factor = if (r1 > r2) r1 / r2 else r2 / r1
        return factor <= ASPECT_RATIO_TOLERANCE
    }

    /**
     * Check if two images have compatible file sizes.
     * A 138KB meme shouldn't match a 9.6MB camera photo.
     *
     * @param s1 File size of first image
     * @param s2 File size of second image
     * @param tolerance Maximum ratio between file sizes (default: FILE_SIZE_TOLERANCE)
     * @return true if file sizes are within tolerance
     */
    private fun areFileSizesCompatible(s1: Long, s2: Long, tolerance: Float = FILE_SIZE_TOLERANCE): Boolean {
        // Skip filter if sizes are invalid or zero
        if (s1 <= 0 || s2 <= 0) return true

        // Check if sizes are within tolerance factor of each other
        val factor = if (s1 > s2) s1.toDouble() / s2 else s2.toDouble() / s1
        return factor <= tolerance
    }

    /**
     * Find similar photo pairs using time-window clustering for efficient burst detection.
     *
     * This algorithm dramatically improves performance by only comparing photos taken
     * within a short time window of each other. The key insight is:
     * - Burst shots and near-duplicates happen within seconds/minutes
     * - Comparing only temporally close photos reduces O(n²) to O(sum of window²)
     * - For 10,000 photos in ~100 windows of ~100 photos: ~500K comparisons vs ~50M
     *
     * Within time windows, thresholds are relaxed because temporal proximity provides
     * high confidence that similar-looking photos are genuinely related (burst shots,
     * HDR captures, slight movement between shots).
     *
     * Overlapping windows (50% overlap by default) ensure photos at window boundaries
     * are compared with neighbors on both sides.
     *
     * @param photos List of PhotoMetadata with dateAdded timestamps
     * @param config Configuration for window size, overlap, and thresholds
     * @return List of pairs of URIs that are similar within time windows
     */
    fun findSimilarPairsWithTimeWindows(
        photos: List<PhotoMetadata>,
        config: BurstDetectionConfig = BurstDetectionConfig()
    ): List<Pair<String, String>> {
        if (photos.size < 2) return emptyList()

        // Sort by timestamp for windowing
        val sortedPhotos = photos.sortedBy { it.dateAdded }
        val minTime = sortedPhotos.first().dateAdded
        val maxTime = sortedPhotos.last().dateAdded

        // Use Set to deduplicate pairs found in overlapping windows
        val foundPairs = mutableSetOf<Pair<String, String>>()
        var windowCount = 0
        var totalWindowComparisons = 0

        logDebug("Time-window scan: ${sortedPhotos.size} photos, time range ${maxTime - minTime}s")
        logDebug("Config: window=${config.windowSizeSeconds}s, step=${config.windowStepSeconds}s")
        logDebug("Thresholds: dHashCertain=${config.dHashCertain}, dHashThreshold=${config.dHashThreshold}, pHashThreshold=${config.pHashThreshold}")

        var windowStart = minTime
        while (windowStart <= maxTime) {
            val windowEnd = windowStart + config.windowSizeSeconds

            // Get photos within this time window
            val windowPhotos = sortedPhotos.filter {
                it.dateAdded >= windowStart && it.dateAdded < windowEnd
            }

            if (windowPhotos.size >= 2) {
                windowCount++
                val windowPairCount = (windowPhotos.size * (windowPhotos.size - 1)) / 2
                totalWindowComparisons += windowPairCount

                // Find duplicates within window using configured thresholds
                val windowPairs = findSimilarPairsEnhanced(
                    windowPhotos,
                    config.dHashCertain,
                    config.dHashThreshold,
                    config.pHashThreshold,
                    config.fileSizeTolerance,
                    config.colorThreshold,
                    config.edgeHashThreshold,
                    config.pHashThresholdStrict
                )

                // Normalize pair ordering for deduplication (smaller URI first)
                windowPairs.forEach { (uri1, uri2) ->
                    val normalized = if (uri1 < uri2) Pair(uri1, uri2) else Pair(uri2, uri1)
                    foundPairs.add(normalized)
                }

                if (windowPairs.isNotEmpty()) {
                    logDebug("Window $windowCount (${windowPhotos.size} photos): found ${windowPairs.size} pairs")
                }
            }

            windowStart += config.windowStepSeconds
        }

        val globalComparisons = (sortedPhotos.size.toLong() * (sortedPhotos.size - 1)) / 2
        val savings = if (globalComparisons > 0) {
            ((globalComparisons - totalWindowComparisons) * 100) / globalComparisons
        } else 0

        logDebug("Time-window summary: $windowCount windows, $totalWindowComparisons comparisons (vs $globalComparisons global, ${savings}% reduction)")
        logDebug("Found ${foundPairs.size} unique duplicate pairs")

        return foundPairs.toList()
    }
}
