# Low Quality Photo Detection - Design Document

**Date:** February 2025
**Status:** Approved
**Feature:** Detect and surface low-quality photos for cleanup

---

## Overview

Add low-quality photo detection to the existing scan feature. Users can clean up blurry, poorly exposed, and otherwise problematic photos alongside duplicate detection.

### Goals

1. **Auto-flag definitely-bad photos** (completely black, completely white, extremely blurry)
2. **Surface questionable photos** for manual review (underexposed, motion blur, etc.)
3. **Reuse existing infrastructure** to minimize scan time overhead

---

## Quality Issues Detected

| Issue | Description | Detection Method |
|-------|-------------|------------------|
| **Blurry/Out-of-focus** | Lack of sharp edges | Low average edge strength (Sobel) |
| **Motion blur** | Directional blur from camera shake | Weak edges + directional alignment |
| **Completely black** | Accidental shots, lens covered | 90%+ pixels in darkest histogram bins |
| **Completely white** | Overexposed to pure white | 90%+ pixels in brightest histogram bins |
| **Underexposed** | Too dark but has content | Low average luminance with histogram spread |
| **Overexposed** | Blown highlights, lost detail | High percentage of clipped bright pixels |
| **Low-quality screenshots** | Pixelated, compression artifacts | DCT artifact analysis + screen dimensions |

---

## User Interface

### Results Screen

The scan results screen gains a second tab:

```
┌─────────────────────────────────┐
│ ← Scan Results                  │
├─────────────────────────────────┤
│  [Duplicates (12)]  [Low Quality (8)]  │
├─────────────────────────────────┤
│                                 │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │     │ │     │ │     │       │
│  │ 📷  │ │ 📷  │ │ 📷  │       │
│  │Blurry│ │Dark │ │Motion│      │
│  └─────┘ └─────┘ └─────┘       │
│                                 │
├─────────────────────────────────┤
│  [ Delete 3 Low Quality Photos ]│
└─────────────────────────────────┘
```

### Interactions

| Action | Result |
|--------|--------|
| Tap thumbnail | Toggle selection (checkbox appears) |
| Long-press thumbnail | Fullscreen preview until release |
| Tap "Delete X" button | Confirmation dialog → delete selected |

### Issue Labels

- Each thumbnail shows a short label: "Blurry", "Dark", "Overexposed", etc.
- If a photo has multiple issues, show the primary one (worst confidence score)

---

## Technical Architecture

### New Components

| Component | Purpose |
|-----------|---------|
| `QualityAnalyzer.kt` | Analyzes bitmap, returns quality issues and scores |
| `PhotoQuality` entity | Database table for quality data |
| `LowQualityScreen.kt` | UI screen for low-quality results |
| `LowQualityCard.kt` | Thumbnail card component with issue label |

### Database Schema

New table `PhotoQuality`:

```kotlin
@Entity(tableName = "photo_quality")
data class PhotoQuality(
    @PrimaryKey val uri: String,
    val overallScore: Float,        // 0.0 = terrible, 1.0 = good
    val issues: String,             // Comma-separated: "BLURRY,UNDEREXPOSED"
    val edgeStrength: Float,        // For blur detection
    val avgLuminance: Float,        // For exposure detection
    val clippedHighlights: Float,   // % of overexposed pixels
    val clippedShadows: Float,      // % of underexposed pixels
    val artifactScore: Float,       // Compression artifact level
    val lastScanned: Long,          // Timestamp
    val algorithmVersion: Int       // For re-scan on algorithm updates
)
```

**Why store raw metrics?**
- Adjust thresholds without re-scanning
- Future: user sensitivity slider
- Debugging and tuning

### Scan Worker Integration

Modified flow in `DuplicateScanWorker`:

```
For each photo:
  1. Load 32x32 bitmap (existing)
  2. Compute hashes: dHash, pHash, edgeHash, colorHistogram (existing)
  3. Load 64x64 bitmap (new - more detail for quality)
  4. Compute quality metrics (new)
  5. Determine issues and overall score (new)
  6. Save PhotoHash + PhotoQuality to database
```

### Detection Algorithms

**Blur Detection:**
- Use Sobel edge detection (already computed for edgeHash)
- Calculate average edge magnitude across image
- Sharp photos: high edge strength; Blurry photos: low edge strength
- Threshold: calibrate based on testing

**Motion Blur:**
- Analyze edge direction consistency
- Motion blur creates aligned edges (in direction of motion)
- Low edge strength + directional alignment = motion blur

**Exposure Analysis:**
- Use color histogram (already computed)
- Calculate average luminance from histogram
- Check for clipped highlights (pixels at max brightness)
- Check for clipped shadows (pixels at min brightness)

**Completely Black/White:**
- Check histogram distribution
- If 90%+ pixels in extreme bins → flag as completely dark/bright

**Screenshot Quality:**
- Analyze DCT coefficients for compression artifacts
- Check if dimensions match common screen sizes
- High artifact score + screen dimensions = low-quality screenshot

### Confidence Scoring

Each issue has a confidence score (0.0 to 1.0):
- **High confidence (0.8+):** Definitely has this issue
- **Medium confidence (0.5-0.8):** Likely has this issue
- **Low confidence (<0.5):** Not flagged

Photos are flagged when any issue exceeds threshold (balanced mode ≈ 0.5).

---

## Performance

### Estimated Impact

| Metric | Value |
|--------|-------|
| Additional bitmap load | 64x64 (small) |
| New computations | Luminance average, artifact analysis |
| Scan time increase | ~10-15% |
| Photos per second | 100+ (typical device) |

### Optimizations

- Reuse existing edge and histogram data
- Small bitmap size (64x64) for quality checks
- Batch database writes
- Parallel processing (existing infrastructure)

---

## Implementation Plan

### Phase 1: Core Detection
1. Create `QualityAnalyzer.kt` with detection algorithms
2. Add `PhotoQuality` database entity and DAO
3. Integrate quality analysis into `DuplicateScanWorker`
4. Unit tests for detection algorithms

### Phase 2: User Interface
1. Add tab bar to scan results screen
2. Create `LowQualityScreen.kt` (based on DuplicatesScreen)
3. Create `LowQualityCard.kt` with issue labels
4. Wire up selection and delete flow

### Phase 3: Tuning
1. Test with real photo libraries
2. Adjust detection thresholds
3. Handle edge cases (HDR photos, artistic dark photos, etc.)
4. Performance optimization if needed

---

## Future Enhancements

- Sensitivity slider (conservative/balanced/aggressive)
- Filter by issue type
- "Auto-select all" for definitely-bad photos
- Quality score visible in main gallery
- Batch undo after deletion

---

## Open Questions

None - design approved.

---

## Appendix: Reused Components

From duplicate detection:
- `DuplicateScanWorker` - scan infrastructure
- `ImageHasher` - bitmap loading, edge detection, histograms
- `DuplicateGroupCard` - thumbnail grid pattern
- Fullscreen preview on long-press
- Selection + bulk delete flow
