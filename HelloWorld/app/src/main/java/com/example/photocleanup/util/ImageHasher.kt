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
import kotlin.math.PI
import kotlin.math.cos
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

    // Adaptive two-stage thresholds
    // If dHash ≤ DHASH_CERTAIN: definitely duplicates, skip pHash (fast path for burst shots)
    // If dHash ≤ DHASH_THRESHOLD: possible duplicates, verify with pHash
    const val DHASH_CERTAIN = 6         // Very similar - trust dHash alone (burst shots: 0-5)
    const val DHASH_THRESHOLD = 12      // Permissive first pass (catches burst shots with dist 11-12)
    const val PHASH_THRESHOLD = 10      // Confirmation threshold (slightly relaxed)

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
     * Includes dimensions, file size, and both hash types for two-stage verification.
     */
    data class PhotoMetadata(
        val uri: String,
        val hash: Long,         // dHash for fast first-pass
        val pHash: Long,        // pHash for strict confirmation
        val width: Int,
        val height: Int,
        val fileSize: Long
    )

    /**
     * Find similar photo pairs using adaptive two-stage hash verification.
     *
     * This uses an adaptive approach to balance speed, recall, and precision:
     * 1. Aspect ratio filter: Reject different aspect ratios
     * 2. File size filter: Reject vastly different file sizes
     * 3. dHash check with adaptive pHash:
     *    - dHash ≤ 6: MATCH immediately (very similar, definitely duplicates - burst shots)
     *    - dHash 7-12: Verify with pHash (questionable, need confirmation)
     *    - dHash > 12: REJECT (too different)
     *
     * This catches burst shots (which have low dHash but may have high pHash due to
     * slight camera shake) while still filtering false positives for marginal matches.
     *
     * @param photos List of PhotoMetadata containing both dHash and pHash
     * @param dHashCertain dHash threshold for certain matches (skip pHash)
     * @param dHashThreshold Maximum dHash for candidate selection
     * @param pHashThreshold pHash threshold for confirming questionable matches
     * @return List of pairs of URIs that are similar
     */
    fun findSimilarPairsEnhanced(
        photos: List<PhotoMetadata>,
        dHashCertain: Int = DHASH_CERTAIN,
        dHashThreshold: Int = DHASH_THRESHOLD,
        pHashThreshold: Int = PHASH_THRESHOLD
    ): List<Pair<String, String>> {
        val similar = mutableListOf<Pair<String, String>>()
        var checkedPairs = 0
        var passedAspectRatio = 0
        var passedFileSize = 0
        var matchedByDHashAlone = 0
        var passedDHash = 0
        var rejectedByPHash = 0
        var matchedWithPHash = 0

        logDebug("Starting adaptive comparison of ${photos.size} photos (dHash≤$dHashCertain=certain, dHash≤$dHashThreshold+pHash≤$pHashThreshold=verify)")

        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                checkedPairs++
                val p1 = photos[i]
                val p2 = photos[j]

                // Layer 1: Aspect ratio pre-filter (fast, O(1))
                if (!areAspectRatiosCompatible(p1.width, p1.height, p2.width, p2.height)) {
                    continue
                }
                passedAspectRatio++

                // Layer 2: File size pre-filter (fast, O(1))
                if (!areFileSizesCompatible(p1.fileSize, p2.fileSize)) {
                    continue
                }
                passedFileSize++

                // Layer 3: dHash check
                val dHashDist = hammingDistance(p1.hash, p2.hash)

                // Fast path: very low dHash = definitely duplicates (burst shots, etc.)
                if (dHashDist <= dHashCertain) {
                    matchedByDHashAlone++
                    logDebug("MATCH (dHash certain): dHash=$dHashDist, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    similar.add(Pair(p1.uri, p2.uri))
                    continue
                }

                // Reject if dHash too high
                if (dHashDist > dHashThreshold) {
                    if (dHashDist <= 20) {
                        logDebug("NEAR-MISS dHash: dist=$dHashDist (threshold=$dHashThreshold), ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    }
                    continue
                }
                passedDHash++

                // Layer 4: pHash confirmation for questionable matches (dHash 7-12)
                val pHashDist = hammingDistance(p1.pHash, p2.pHash)
                if (pHashDist > pHashThreshold) {
                    rejectedByPHash++
                    logDebug("REJECTED by pHash: dHash=$dHashDist, pHash=$pHashDist, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                    continue
                }

                // Both stages passed - confirmed duplicate
                matchedWithPHash++
                logDebug("MATCH (pHash verified): dHash=$dHashDist, pHash=$pHashDist, ${p1.uri.takeLast(40)} <-> ${p2.uri.takeLast(40)}")
                similar.add(Pair(p1.uri, p2.uri))
            }

            // Periodic garbage collection hint for large galleries
            if (i % 500 == 0 && i > 0) {
                System.gc()
            }
        }

        val totalMatched = matchedByDHashAlone + matchedWithPHash
        logDebug("Summary: checked=$checkedPairs, passedAR=$passedAspectRatio, passedSize=$passedFileSize")
        logDebug("  Matches: $totalMatched total ($matchedByDHashAlone by dHash alone, $matchedWithPHash verified by pHash)")
        logDebug("  Filtered: passedDHash=$passedDHash, rejectedByPHash=$rejectedByPHash")
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
     * @return true if file sizes are within tolerance
     */
    private fun areFileSizesCompatible(s1: Long, s2: Long): Boolean {
        // Skip filter if sizes are invalid or zero
        if (s1 <= 0 || s2 <= 0) return true

        // Check if sizes are within 10x of each other
        val factor = if (s1 > s2) s1.toDouble() / s2 else s2.toDouble() / s1
        return factor <= FILE_SIZE_TOLERANCE
    }
}
