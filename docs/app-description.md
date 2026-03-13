# Stash or Trash — App Description

Use this as context when generating social media posts, Reddit posts, ad copy, or any promotional content.

## What It Is

Stash or Trash is an Android app for cleaning up and organizing your phone's photo gallery. You swipe through photos one at a time — right to keep, left to delete — similar to how dating apps work. It also has a built-in scanner that automatically detects duplicate photos, blurry photos, too-dark photos, and screenshots.

## The Problem It Solves

Most people have thousands of photos on their phone and no easy way to go through them. The default gallery app makes you tap, select, delete one at a time. It's slow and tedious, so people put it off and their storage fills up. Stash or Trash makes the process fast and almost mindless — you just swipe.

## Core Features (Free)

- **Swipe interface**: Right to keep, left to delete. Visual overlays confirm your choice.
- **Undo**: Undo your last swipe at any time. Nothing is permanently deleted until you confirm.
- **Browse by date**: Photos organized by month/year. Start from oldest or newest.
- **Browse by album**: Clean one folder at a time.
- **All media view**: See everything at once.
- **To-delete review queue**: All photos marked for deletion go to a review queue before anything is permanently removed.
- **Smart Scanner (view only)**: Scans your gallery and flags duplicates, blurry photos, dark photos, and screenshots. Free users can see results but not bulk-delete.

## Premium Features ($4.99 one-time)

- **Videos**: Include videos in the swipe flow.
- **Random shuffle**: Swipe through photos in random order.
- **Album organization**: Move photos between albums while swiping, without leaving the app.
- **Scanner bulk delete**: Select and delete flagged duplicates and low-quality photos in bulk.
- **Full statistics**: Detailed charts showing review progress, keep/delete ratios, space recovered, quality breakdowns, and milestones.

## How the Scanner Works

The scanner uses perceptual hashing (not simple file comparison) to find photos that look similar even if they have different file sizes or slight edits. It compares brightness patterns, edge outlines, color distribution, and frequency analysis. Photos taken close together in time get extra tolerance for matching (burst shots, rapid-fire photos).

For quality detection, it analyzes sharpness (tiled Laplacian), directional blur (Sobel gradients), brightness distribution, and color count. It's tuned to avoid false positives — it won't flag intentionally soft bokeh photos or dark-mode screenshots.

All processing runs on-device. No photos are uploaded anywhere.

## Privacy

- Everything runs locally on the phone.
- No cloud uploads, no accounts, no data collection.
- No internet connection required to use the app.
- No ads.

## Technical Details

- Android only (Google Play Store).
- Minimum Android 7.0 (API 24), targets Android 14+ (API 35).
- Built with Kotlin, Jetpack Compose, Material Design 3.
- Current version: 1.2.0.
- Package: com.stashortrash.app.

## Brand

- **Name**: Stash or Trash
- **Tagline**: Swipe your gallery clean.
- **Tone**: Fun, casual, slightly cheeky. Not corporate.
- **Positioning**: The fast, fun way to declutter your photo gallery. Tinder for your photos.

## Target Audience

- People with thousands of unorganized photos on their phone.
- People running low on storage.
- People who take lots of similar/burst photos and never clean up.
- Android users who want a simple, private tool (no sign-ups, no cloud).

## Key Differentiators vs Competitors

- **Memorable name and brand** — stands out in a category full of generic "Photo Cleaner" apps.
- **Privacy-first** — no cloud, no accounts, no data collection. Most competitors either collect data or require accounts.
- **Quality detection** — not just duplicates. Also catches blurry, dark, and overexposed photos.
- **Organize while swiping** — move photos to albums during the swipe flow. Most competitors only delete.
- **No ads** — premium is a one-time $4.99, not a subscription. Free version has no ads either.
