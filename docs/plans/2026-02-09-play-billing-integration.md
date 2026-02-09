# Plan: Integrate Google Play Billing for Premium Unlock

## Context

The app shows a $4.99 "Unlock Premium" button in multiple screens, but tapping it does nothing — the callbacks are no-ops. A dev toggle in Settings lets you flip premium on/off manually. Before Play Store submission, we need real billing so users can actually purchase premium, and the dev toggle must be removed.

The existing architecture is well set up: all premium checks go through `AppPreferences.isPremium`, and every screen already has `onUnlockClick` / `onRestoreClick` callback slots ready to wire.

## What Changes

### 1. Add billing dependency
**File:** `HelloWorld/app/build.gradle.kts` (line ~92)
- [x] Add `implementation("com.android.billingclient:billing-ktx:7.1.1")`

### 2. Create BillingManager class (NEW FILE)
**File:** `HelloWorld/app/src/main/java/com/example/photocleanup/data/BillingManager.kt`
- [x] Created ~250 line class handling connect, query, purchase, acknowledge, restore

### 3. Register BillingManager singleton
**File:** `HelloWorld/app/src/main/java/com/example/photocleanup/PhotoCleanupApp.kt`
- [x] Add `val billingManager: BillingManager by lazy { BillingManager(this, appPreferences) }`

### 4. Start billing connection on app launch
**File:** `HelloWorld/app/src/main/java/com/example/photocleanup/MainActivity.kt`
- [x] Add `(application as PhotoCleanupApp).billingManager.startConnection()` before `setContent`

### 5. Change premium default to `false`
**File:** `HelloWorld/app/src/main/java/com/example/photocleanup/data/AppPreferences.kt` (line 10)
- [x] Change `prefs.getBoolean("premium_unlocked", true)` to `false`

### 6. Remove dev toggle from Settings
**File:** `HelloWorld/app/src/main/java/com/example/photocleanup/ui/screens/SettingsTabScreen.kt`
- [ ] Delete the "DEV: Premium toggle" Card
- [ ] Change `var premiumEnabled by remember { mutableStateOf(...) }` to `val premiumEnabled = appPreferences.isPremium`
- [ ] Remove unused imports: `Switch`, `SwitchDefaults`
- **Note:** Kept for now — needed for testing. Remove before release.

### 7. Wire billing callbacks in all 5 screens
- [x] `SettingsTabScreen.kt` — wired + added billing state observer with Toast feedback
- [x] `MenuScreen.kt` — wired
- [x] `MainScreen.kt` — wired
- [x] `PhotoScannerScreen.kt` — wired
- [x] `StatsScreen.kt` — wired

### 8. Add billing state feedback in SettingsTabScreen
- [x] Collect `billingManager.billingState` as Compose state
- [x] Show Toast on purchase success or error

## After Implementation: Play Console Setup (Manual)

- [ ] Go to Play Console > Monetize > In-app products
- [ ] Create product with ID `premium_unlock`, price $4.99, type "Managed product"
- [ ] Set status to Active
- [ ] Add your Google account as a License Tester (Settings > License testing) for free test purchases

## Verification

- [ ] Build the project — confirm no compile errors
- [ ] Clear app data on test device — confirm app starts in free/locked mode
- [ ] Tap "Unlock Premium" — confirm Google Play purchase dialog appears (or error Toast if no Play Console product yet)
- [ ] Complete test purchase — confirm premium unlocks immediately and Settings card changes to "Thank you"
- [ ] Kill and reopen app — confirm premium persists
- [ ] Tap "Restore Purchase" on a fresh install with same Google account — confirm premium restores

## Other Play Store Items (From Audit)

These are separate from billing but also needed before submission:

- [x] **Privacy Policy** — Drafted to `docs/privacy-policy.md`
- [x] **ProGuard/R8** — Enabled `isMinifyEnabled = true`, added keep rules
- [x] **Accessibility** — Added missing `contentDescription` to icon buttons
- [ ] **MANAGE_EXTERNAL_STORAGE** — Prepare justification text for Play Console Data Safety form
