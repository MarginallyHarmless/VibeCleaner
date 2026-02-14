# CLAUDE.md
# Stash or Trash Android App

## Project Overview
A photo gallery management app that helps users quickly organize their photos through an intuitive swipe interface. Users can swipe left to delete photos, swipe right to save them, or organize photos into folders. The app targets users who have accumulated large photo collections and need an efficient way to declutter and organize their galleries.

**Current Status**: Phase 1 (swipe functionality) is built. Planning additional features.

**Developer Experience Level**: No prior coding experience - provide detailed explanations and ensure all code follows best practices for Play Store publication.

## Target Platform
- **Language**: Kotlin (modern Android standard)
- **Minimum SDK**: API 24 (Android 7.0) - covers ~95% of active devices
- **Target SDK**: API 34 (Android 14) - latest stable
- **Architecture**: MVVM (Model-View-ViewModel) with Jetpack Compose for UI

## Core Features

### Phase 1 (Currently Built)
- Swipe left to delete photos
- Swipe right to save/keep photos
- View photos from device gallery
- Basic navigation between photos

### Phase 2 (In Progress)
- **Duplicate photo detection** ✅ (implemented, being refined)
- Quick folder organization
- Batch operations
- Smart categorization suggestions
- Photo statistics/insights

## Duplicate Detection System

### Overview
The app detects similar/duplicate photos using a multi-stage perceptual hashing approach. Photos are compared using multiple "fingerprints" and grouped together when they're similar enough.

### How It Works (Simple Explanation)

1. **Create Fingerprints**: For each photo, compute 4 types of fingerprints:
   - **dHash**: Compares brightness of neighboring pixels (fast, catches obvious duplicates)
   - **pHash**: Analyzes overall image patterns using frequency analysis (more accurate)
   - **Edge Hash**: Detects outlines/edges (works even with different lighting)
   - **Color Histogram**: Counts color distribution (sunset = orange, sky = blue)

2. **Compare Photos**: For each pair, check:
   - Are colors similar? (color histogram)
   - Are shapes/edges similar? (edge hash)
   - Were they taken close in time? (timestamps)

3. **Apply Confidence Boosts**: The closer in time + the more similar colors = more tolerance for differences

4. **Group Similar Photos**: Photos that pass all checks are grouped together

### Key Files

| File | Purpose |
|------|---------|
| `util/ImageHasher.kt` | Hash computation and comparison logic |
| `worker/DuplicateScanWorker.kt` | Background scanning and grouping |
| `data/PhotoHash.kt` | Database entity for storing hashes |
| `data/PhotoDatabase.kt` | Room database with migrations |

### Tunable Parameters (ImageHasher.kt)

#### Temporal Windows
Photos taken close together get more lenient matching:

```kotlin
TEMPORAL_SESSION_SECONDS = 7200   // 2 hours - same photo session
TEMPORAL_CLOSE_SECONDS = 300     // 5 minutes - close shots
TEMPORAL_BURST_SECONDS = 60      // 1 minute - likely burst
TEMPORAL_RAPID_SECONDS = 30      // 30 seconds - rapid burst
```

#### Temporal Boosts
Extra tolerance added to hash thresholds based on time proximity:

```kotlin
TEMPORAL_SESSION_BOOST = 4       // Small boost for same session
TEMPORAL_CLOSE_BOOST = 12        // Strong boost for close photos
TEMPORAL_BURST_BOOST = 18        // Very strong for burst shots
TEMPORAL_RAPID_BOOST = 24        // Maximum for rapid shots
```

#### Color Similarity Tiers
Temporal boost is scaled by color similarity (prevents false positives):

```kotlin
COLOR_VERY_HIGH = 0.72    // Full temporal boost (100%)
COLOR_HIGH = 0.65         // Partial temporal boost (50%)
// Below COLOR_HIGH: No temporal boost (0%)
```

#### Base Hash Thresholds
Maximum hash distance (Hamming distance) to consider as duplicates:

```kotlin
DHASH_CERTAIN = 5         // Instant match, skip further checks
DHASH_THRESHOLD = 12      // Maximum for candidate selection
PHASH_THRESHOLD = 10      // Confirmation threshold
PHASH_THRESHOLD_STRICT = 6  // Used when only structure path passes
```

#### Entry Thresholds
Minimum similarity to even consider comparing two photos:

```kotlin
COLOR_HISTOGRAM_THRESHOLD = 0.60  // Minimum color similarity
EDGE_HASH_THRESHOLD = 8           // Maximum edge hash distance
```

#### High-Confidence Boosts
When BOTH color AND structure match strongly:

```kotlin
HIGH_CONFIDENCE_COLOR = 0.68  // Color threshold for high confidence
HIGH_CONFIDENCE_EDGE = 8      // Edge threshold for high confidence
DHASH_BOOST = 6               // Extra dHash tolerance
PHASH_BOOST = 6               // Extra pHash tolerance
```

### Threshold Calculation Example

For photos with high color similarity (≥0.72) taken within 30 seconds:

```
Base dHash threshold:     12
+ High-confidence boost:  +6  (if color≥0.68 AND edge≤8)
+ Temporal boost (full):  +24 (rapid, scaled by color)
= Maximum threshold:      42
```

### Grouping Algorithm

Photos are grouped using representative-based clustering with merging:

1. When A~B found: Create group with A as representative
2. When C wants to join: Must match representative A
3. When two groups could merge: Merge IF their representatives match

This prevents "chaining" (A~B, B~C, C~D creating one group even if A≠D) while still allowing legitimate merges.

### Tuning Tips

**Too many false positives (different photos grouped together)?**
- Increase `COLOR_VERY_HIGH` and `COLOR_HIGH` thresholds
- Decrease temporal boost values
- Decrease `DHASH_THRESHOLD` and `PHASH_THRESHOLD`

**Missing obvious duplicates?**
- Decrease `COLOR_VERY_HIGH` and `COLOR_HIGH` thresholds
- Increase temporal boost values
- Increase `DHASH_THRESHOLD` and `PHASH_THRESHOLD`
- Increase temporal window sizes

**Groups being split that should be together?**
- Check `matchesRepresentative()` threshold in DuplicateScanWorker.kt
- Currently uses `DHASH_THRESHOLD + 10` for representative matching

### Algorithm Version

When changing hash computation (not just thresholds), increment `CURRENT_ALGORITHM_VERSION` in DuplicateScanWorker.kt to force re-scanning of all photos.

Current version: **5** (edge hash + color histogram)

## Technical Requirements

### Permissions
The app requires the following Android permissions:
- `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API 32 and below) - to access user photos
- `WRITE_EXTERNAL_STORAGE` (API 28 and below) - to delete photos
- `MANAGE_EXTERNAL_STORAGE` (API 30+) - for full gallery management (may be required for certain operations)

**Important**: Always implement runtime permission requests with clear explanations to users about why each permission is needed.

### Storage & Data
- **Local Storage**: Use MediaStore API for gallery access (modern, scoped storage compliant)
- **Preferences**: SharedPreferences or DataStore for user settings
- **Database**: Room database if we need to track user actions, folder organization, or app state
- **No User Data Collection**: The app operates entirely on-device; no user data is sent to external servers

### Dependencies (Key Libraries)
```gradle
// Core Android
- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.activity:activity-compose

// UI
- androidx.compose.ui (Jetpack Compose)
- androidx.compose.material3 (Material Design 3)
- coil-compose (for efficient image loading)

// Architecture
- androidx.lifecycle:lifecycle-viewmodel-compose
- androidx.navigation:navigation-compose

// Storage
- androidx.room (if needed for local database)
- androidx.datastore:datastore-preferences

// Permissions
- com.google.accompanist:accompanist-permissions
```

## Code Standards & Best Practices

### Architecture Guidelines
1. **MVVM Pattern**: Separate UI (Compose), ViewModel (business logic), and Model (data)
2. **Single Responsibility**: Each class/function should have one clear purpose
3. **State Management**: Use Compose state properly, avoid memory leaks
4. **Error Handling**: Always handle edge cases (no photos, permission denied, storage full, etc.)

### Code Quality
- **Comments**: Add explanatory comments for complex logic, especially helpful for a beginner to learn from
- **Naming**: Use clear, descriptive variable and function names
- **Null Safety**: Leverage Kotlin's null safety features
- **Resource Management**: Properly close/dispose of resources (images, file streams)
- **Performance**: Lazy load images, use pagination for large galleries, avoid loading all photos into memory

### File Organization
```
app/src/main/java/com/[package]/
├── ui/
│   ├── screens/          # Composable screens
│   ├── components/       # Reusable UI components
│   └── theme/           # App theming
├── viewmodel/           # ViewModels
├── data/
│   ├── repository/      # Data repositories
│   ├── model/          # Data models
│   └── local/          # Local data sources
└── util/               # Utility functions
```

## Play Store Compliance

### Essential Requirements
1. **Target API Level**: Must target API 34 (current requirement as of 2024-2025)
2. **64-bit Support**: Include arm64-v8a and x86_64 architectures
3. **Privacy Policy**: Required even without data collection - must host privacy policy URL
4. **App Signing**: Use Play App Signing (handled during first upload)
5. **Content Rating**: Target "Everyone" rating - avoid any mature content in screenshots/descriptions

### Privacy & Security
- **Scoped Storage**: Use MediaStore API, comply with Android 11+ storage restrictions
- **No Sensitive Permissions**: Avoid requesting unnecessary permissions
- **Privacy Policy**: Must clearly state:
  - App only accesses photos for organization purposes
  - No data collection or transmission
  - All operations are local to device
  - Which permissions are used and why
- **Data Safety Form**: Complete accurately in Play Console (select "no data collection")

### Pre-Launch Checklist
Before submitting to Play Store, ensure:
- [ ] All features tested on multiple Android versions (7.0 to 14)
- [ ] Tested on different screen sizes (phone, tablet)
- [ ] All permissions properly requested and explained
- [ ] No crashes or ANRs (App Not Responding)
- [ ] Privacy policy hosted and linked
- [ ] App icon meets guidelines (512x512 PNG, no transparency)
- [ ] Screenshots prepared (minimum 2, recommended 4-8)
- [ ] Store listing written (title, short description, full description)
- [ ] Content rating questionnaire completed
- [ ] ProGuard/R8 configured for release build

## Monetization (Future Consideration)

### Options Being Explored
1. **In-App Purchases**: Premium features (advanced organization, themes, unlimited folders)
2. **Ads**: Non-intrusive banner or interstitial ads (requires AdMob integration)
3. **Freemium Model**: Basic features free, advanced features paid
4. **One-time Purchase**: Pay once for full app

**Note**: If implementing ads or IAP, will need to:
- Integrate Google Play Billing Library (for IAP)
- Integrate Google AdMob (for ads)
- Update privacy policy accordingly
- Add appropriate Play Store declarations

## Design Guidelines

### UI/UX Principles
- **Intuitive**: Gestures should feel natural (swipe patterns familiar from dating apps)
- **Fast**: Smooth animations, no lag when swiping through photos
- **Safe**: Confirm before permanent deletions (or provide undo functionality)
- **Accessible**: Support TalkBack, adequate touch targets (48dp minimum)
- **Material Design 3**: Follow Android design guidelines

### Branding (Work in Progress)
- App name: Stash or Trash
- Tagline: Swipe your gallery clean.
- Color scheme: [TBD]
- Logo/Icon: [TBD]
- Typography: Use Material Design 3 defaults until custom branding decided

**Note to Claude**: When branding is finalized, update theme files and resources accordingly.

## Testing Strategy

### Manual Testing Requirements
Since developer has no coding experience, prioritize clear testing instructions:
1. **Feature Testing**: Test each swipe action, folder creation, photo navigation
2. **Permission Testing**: Test permission denial scenarios
3. **Edge Cases**: Empty gallery, single photo, thousands of photos
4. **Device Testing**: Test on at least 2 different Android versions
5. **Storage Testing**: Test with nearly full storage
6. **Interruption Testing**: Test behavior when interrupted (phone call, app switching)

### Automated Testing (Optional but Recommended)
- Unit tests for ViewModels and business logic
- UI tests for critical user flows
- Consider adding tests before major feature additions

## Build Configuration

### Debug vs Release
- **Debug**: Use for development and testing
- **Release**: Minified with R8, signed with release key, optimized

### ProGuard/R8 Rules
Ensure the following are NOT obfuscated:
- Data classes used with Room database
- Model classes
- Any classes used with reflection

### Version Management
Use semantic versioning: MAJOR.MINOR.PATCH
- MAJOR: Breaking changes or complete redesigns
- MINOR: New features
- PATCH: Bug fixes

Update `versionCode` (integer) and `versionName` (string) in `build.gradle` for each release.

## Common Pitfalls to Avoid

1. **Storage Access**: Don't use deprecated storage APIs; always use MediaStore
2. **Memory Leaks**: Be careful with image loading; use Coil with proper lifecycle awareness
3. **Permission Handling**: Always check permissions before attempting gallery access
4. **UI Thread Blocking**: Load images asynchronously, never on main thread
5. **Hardcoded Strings**: Use `strings.xml` for all user-facing text (helps with future translations)
6. **Missing Error States**: Always show user-friendly messages when things go wrong
7. **Battery Drain**: Avoid continuous scanning or unnecessary background operations

## Development Workflow

### When Adding New Features
1. Discuss feature scope and implementation approach with Claude
2. Implement in small, testable increments
3. Test thoroughly on device after each change
4. Document any new dependencies or permissions needed
5. Update this claude.md if architecture or requirements change

### When Encountering Issues
1. Provide full error messages and stack traces
2. Describe what you expected vs what happened
3. Share relevant code context
4. Test on physical device when possible (emulator can behave differently)

## Resources for Learning

### Recommended Documentation
- Android Developer Guides: https://developer.android.com/guide
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Material Design 3: https://m3.material.io/
- Play Store Policies: https://play.google.com/about/developer-content-policy/

### Play Console
- Google Play Console: https://play.google.com/console
- App signing documentation: https://developer.android.com/studio/publish/app-signing
- Release checklist: https://developer.android.com/distribute/best-practices/launch/launch-checklist

## Communication Guidelines for Claude Code

**When helping this developer:**
1. **Explain WHY, not just WHAT**: Since they're learning, explain reasoning behind architectural decisions
2. **Highlight Play Store implications**: Point out when changes affect store compliance
3. **Suggest best practices**: Proactively recommend industry standards
4. **Warn about common mistakes**: Help avoid pitfalls that could delay launch
5. **Provide context**: Explain how different parts of the app work together
6. **Be explicit about testing**: Always include testing steps for new features
7. **Clarify permissions**: Explain why permissions are needed and how to request them properly
8. **Think production-ready**: All code should be store-ready quality, not just "works on my machine"

## Future Expansion Ideas (Brainstorming)

Consider these for Phase 2+ features:
- ~~Duplicate photo detection~~ ✅ Implemented
- Similar photo grouping (using ML Kit) - could enhance current detection
- Photo quality analysis
- Storage space analytics
- Backup suggestions before deletion
- Photo timeline/calendar view
- Custom swipe actions
- Themes/customization
- Photo editing integration
- Cloud backup integration
- Shared folder organization

**Note**: Each new feature should be evaluated for:
- User value
- Implementation complexity
- Play Store compliance implications
- Performance impact
- Additional permissions required

## Notes

- This is a living document - update as the project evolves
- When major decisions are made (branding, monetization model, new features), document them here
- Keep track of any Play Store policy changes that might affect the app
- Consider user feedback once app is launched for future feature priorities

---

**Last Updated**: February 2026
**Project Phase**: Phase 2 in progress - Duplicate detection implemented
**Next Milestone**: Refine duplicate detection thresholds, reduce false positives