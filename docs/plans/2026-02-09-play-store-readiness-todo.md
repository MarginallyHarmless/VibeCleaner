# Play Store Readiness TODO

## Code Changes (Claude can do)

- [x] **Enable ProGuard/R8 minification** — Set `isMinifyEnabled = true` in `build.gradle.kts`, add keep rules to `proguard-rules.pro` for Room entities, ViewModels, WorkManager, and data classes. Test release build for crashes.

- [x] **Add missing accessibility labels** — Add `contentDescription` to icon buttons in MainScreen and PhotoScannerScreen that were missing them.

- [x] **Move hardcoded string to strings.xml** — `"Already in this album"` in PhotoViewModel.kt now uses `R.string.error_already_in_album`.

- [x] **Integrate Google Play Billing** — Created BillingManager, wired purchase callbacks in all 5 screens, changed premium default to false. Dev toggle kept for testing.

- [ ] **Remove dev toggle before release** — Delete the "Premium Mode" switch in SettingsTabScreen.kt and change `premiumEnabled` from mutable state back to `val premiumEnabled = appPreferences.isPremium`.

- [x] **Draft privacy policy text** — Written to `docs/privacy-policy.md`. Replace `[YOUR_EMAIL_HERE]` with your contact email before hosting.

## Play Console Tasks (Manual — done in browser)

- [ ] **Host privacy policy** — Upload the drafted text to a public URL (e.g. Google Sites, GitHub Pages) and link it in the Play Console store listing.

- [ ] **Create in-app product** — In Play Console > Monetize > In-app products, create `premium_unlock` at $4.99 (managed product), set to Active.

- [ ] **Add license testers** — In Play Console > Settings > License testing, add your Google account for free test purchases.

- [ ] **Complete content rating questionnaire** — In Play Console, answer the IARC questionnaire. Select "Everyone" — the app has no mature content.

- [ ] **Fill out Data Safety form** — In Play Console, declare: no data collected, no data shared, all processing on-device. Disclose storage permissions usage.

- [ ] **Prepare MANAGE_EXTERNAL_STORAGE justification** — Write explanation for Play Console: "App requires full storage access to move and organize photos across albums without repeated user confirmation dialogs."

- [ ] **Prepare store listing assets** — App title, short description, full description, 2-8 screenshots, feature graphic (1024x500).
