# Hero Cards & Random Feature Design

## Summary

Add two hero cards ("All Media" and "Random") to the top of the Cleanup Tab's "by date" view. These cards are taller, sit side by side, and use photo thumbnails as backgrounds. The "Random" card opens a swipe view with all unreviewed media in shuffled order. Random is a premium feature.

## UI Changes

### Hero Card Row
- Replaces the current "All Media" card at the top of the LazyColumn
- `Row` with two cards, each `weight(1f)`, 140dp height, 12dp gap
- Only shown in BY_DATE mode

### Hero Card Design
- Photo thumbnail background loaded via Coil `AsyncImage` with `ContentScale.Crop`
- Dark gradient scrim overlay (black ~60%, stronger at bottom) for text readability
- Content anchored bottom-left: icon + title on one line, count below
- Thin progress bar (4dp) along bottom edge using AccentPrimary
- "Random" card: small lock icon in top-right corner when not premium

### Thumbnail Sources
- "All Media": most recent media item on device
- "Random": a random unreviewed media item (changes each tab visit)

## Data Changes

### MenuFilter
Add `isRandom: Boolean = false` field.

### MenuViewModel / MenuUiState
Add `mostRecentMediaUri: Uri?` and `randomMediaUri: Uri?` to UI state.
Fetch both in `loadMenuData()`.

### PhotoRepository
- `getMostRecentMediaUri(): Uri?` — query MediaStore sorted by DATE_ADDED DESC, limit 1
- `getRandomThumbnailUri(): Uri?` — query all unreviewed, pick one at random

### PhotoViewModel.loadPhotosWithMenuFilter
Add `isRandom` branch: calls `repository.loadAllMedia()` then `.shuffled()`.

## Premium Gating
- Card always visible with lock badge when not premium
- Tap when not premium: show upsell dialog (same pattern as video feature)
- Uses existing `AppPreferences.isPremium`

## Files to Modify
1. `MenuCard.kt` — add `HeroCard` composable
2. `MenuScreen.kt` — replace All Media item with hero row
3. `MenuViewModel.kt` — add thumbnail URIs to state
4. `PhotoFilter.kt` — add `isRandom` to MenuFilter
5. `PhotoRepository.kt` — add thumbnail query methods
6. `PhotoViewModel.kt` — handle `isRandom` in loadPhotosWithMenuFilter
7. `strings.xml` — add "Random" string
