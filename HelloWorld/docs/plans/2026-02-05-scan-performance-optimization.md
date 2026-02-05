# Performance Optimization: Parallel Hashing + Adaptive Time Windows

**Date:** 2026-02-05
**Status:** Approved for implementation

## Overview

Two complementary optimizations to reduce duplicate scan time while maintaining matching quality:

1. **Parallel hash computation** - Use multiple CPU cores to compute hashes concurrently
2. **Adaptive time windows** - Shrink comparison windows when photo density is high

**Combined expected speedup:** 4-10x depending on gallery size and photo distribution.

---

## Part 1: Parallel Hash Computation

**Files to modify:** `DuplicateScanWorker.kt`

### Changes

1. Add a configurable parallelism constant:
```kotlin
private const val HASH_PARALLELISM = 4  // Concurrent image loads
```

2. Replace the sequential for-loop with parallel processing using coroutines and a semaphore for throttling.

3. Extract hash computation into a separate `computeHashesForPhoto()` function.

4. Update progress reporting to be atomic (thread-safe) using `AtomicInteger`.

### Expected Speedup
2-4x on typical devices (depends on CPU cores and storage speed).

---

## Part 2: Adaptive Time Windows

**Files to modify:** `ImageHasher.kt`

### Changes

1. Add `AdaptiveWindowConfig` data class for configurable thresholds.

2. Add `calculateAdaptiveWindowSize()` function:
   - Very dense (>100 photos/hour): 15-minute windows
   - Dense (>50 photos/hour): 30-minute windows
   - Moderate (>20 photos/hour): 1-hour windows
   - Sparse: 2-hour windows (current behavior)

3. Add `analyzePhotoDensity()` function to pre-scan photo timestamps.

4. Add `findSimilarPairsWithAdaptiveWindows()` that uses density-based window sizing.

### Example Impact
- 200 photos in 1 hour with 15-minute windows: ~1,225 comparisons per window
- Same 200 photos with 2-hour window: ~19,900 comparisons
- **~16x fewer comparisons**

---

## Summary of Changes

| File | Change | Purpose |
|------|--------|---------|
| `DuplicateScanWorker.kt` | Add `HASH_PARALLELISM` constant | Configure concurrency |
| `DuplicateScanWorker.kt` | Replace for-loop with coroutine parallel processing | 2-4x speedup on hashing |
| `DuplicateScanWorker.kt` | Add `AtomicInteger` progress counter | Thread-safe progress |
| `DuplicateScanWorker.kt` | Extract `computeHashesForPhoto()` function | Cleaner parallel code |
| `ImageHasher.kt` | Add `AdaptiveWindowConfig` data class | Configurable thresholds |
| `ImageHasher.kt` | Add `calculateAdaptiveWindowSize()` | Density-based windows |
| `ImageHasher.kt` | Add `analyzePhotoDensity()` | Pre-scan density analysis |
| `ImageHasher.kt` | Add `findSimilarPairsWithAdaptiveWindows()` | Main adaptive algorithm |
| `DuplicateScanWorker.kt` | Call new adaptive function | Wire it together |

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Memory pressure from parallel loads | Semaphore limits concurrent loads to 4 |
| Missing duplicates at window boundaries | 50% window overlap ensures boundary photos compared |
| Slower on single-core devices | Parallelism of 4 still works, just less benefit |
| Different behavior on dense vs sparse galleries | Adaptive windows handle both cases appropriately |

---

## Testing Plan

1. **Unit test:** Verify `calculateAdaptiveWindowSize()` returns expected values
2. **Unit test:** Verify `analyzePhotoDensity()` correctly buckets photos
3. **Integration test:** Compare results of old vs new algorithm on test gallery
4. **Performance test:** Measure scan time before/after on galleries of 1K, 5K, 10K photos
