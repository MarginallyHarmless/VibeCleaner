# Play Store Graphics Design

**Date**: 2026-02-12
**Status**: Approved
**Tool**: ChatGPT / AI image generation for feature graphic; real screenshots + AI mockup assembly for screenshots

---

## Feature Graphic (1024 x 500 px)

**Style**: Bold swipe arrows on dark background — immediately communicates the core mechanic.

**Layout**:
- Background: Dark charcoal (#1E1B18) with subtle radial center glow
- Center: "Stash or Trash" in large white bold sans-serif
- Below name: "Swipe your gallery clean." in light gray (#B0B0B0)
- Above name: Small app logo (dual curved swooshes — coral-red left, teal right)
- Left side: Large curved swoosh arrow pointing left in dusty mauve (#A64253) with white X icon
- Right side: Large curved swoosh arrow pointing right in seagrass teal (#61988E) with white checkmark icon
- Arrows should look like finger-swipe gestures — fluid and dynamic, not rigid geometric

**AI Prompt**:
```
A wide banner graphic (1024x500 pixels), dark charcoal-black background (#1E1B18) with a subtle center glow. In the center, bold white text "Stash or Trash" in modern sans-serif font, with smaller light gray text "Swipe your gallery clean." below it. A small minimalist logo above the text (two curved swooshes, one coral-red, one teal). On the left side, a large curved swoosh arrow pointing left in dusty rose-red (#A64253) with a white X icon. On the right side, a large curved swoosh arrow pointing right in teal (#61988E) with a white checkmark icon. The arrows should look like finger-swipe gestures, fluid and dynamic, not rigid. Clean, modern, professional graphic design. No photos, no people, no devices.
```

---

## Screenshots (4 total, 1080 x 1920 px each)

### General Assembly

Take real screenshots from the app, then use AI to wrap them in phone mockups with captions.

**Mockup template prompt** (replace `[CAPTION]` and `[DESCRIPTION]` for each):
```
A Google Play Store screenshot mockup. Dark charcoal background (#1E1B18). At the top, bold white text: "[CAPTION]". Below the text, a modern Android phone mockup (thin bezels, rounded corners) displaying this screenshot: [DESCRIPTION]. The phone is centered and takes up about 70% of the vertical space. Clean, minimal, professional. Dimensions: 1080x1920 pixels (9:16 portrait).
```

---

### Screenshot 1: "Swipe Right to Keep, Left to Delete"

**What to capture**: The swipe screen mid-swipe, showing the teal "KEEP" overlay on a photo card.

**How to take it**:
1. Open the app, navigate to a month with photos
2. Start swiping right — while the card is mid-swipe, take a screenshot
3. The teal overlay with checkmark and "KEEP" text should be visible
4. Progress bar at top should show volume (e.g., "24 of 450")

**Purpose**: Hero screenshot. Immediately communicates the core mechanic.

---

### Screenshot 2: "Find & Remove Duplicate Photos"

**What to capture**: Scanner screen > Duplicates tab with detected duplicate groups.

**How to take it**:
1. Run a scan so you have duplicate groups
2. Navigate to Scanner > Duplicates tab
3. Screenshot showing at least one group with 2-3 similar photos
4. Storage savings indicator should be visible

**Purpose**: Strongest premium value prop. Users with large collections see immediate benefit.

---

### Screenshot 3: "Detect Blurry & Dark Photos"

**What to capture**: Scanner screen > Low Quality tab showing the grid of flagged photos.

**How to take it**:
1. After scanning, switch to the "Low Quality" tab
2. Screenshot the grid showing photos flagged as blurry, dark, or screenshots
3. Ideally show a mix of quality issue types

**Purpose**: Shows the app is smart — it proactively finds quality problems.

---

### Screenshot 4: "See How Much Space You Saved"

**What to capture**: Stats screen showing hero pods and activity grid.

**How to take it**:
1. Use the app enough to generate some stats
2. Navigate to the Stats tab
3. Screenshot showing reviewed count, space recovered, and delete/keep ratio

**Purpose**: Gamification and visible progress. Users love seeing accomplishment metrics.

---

## Color Reference

| Color | Hex | Usage |
|-------|-----|-------|
| Carbon Black | #1E1B18 | Backgrounds |
| Seagrass Teal | #61988E | Keep/positive actions |
| Dusty Mauve | #A64253 | Delete/negative actions |
| Honey Bronze | #F6AE2D | Premium features |
| White | #FFFFFF | Primary text |
| Light Gray | #B0B0B0 | Secondary text |

## Google Play Requirements

- Feature graphic: exactly 1024 x 500 px, PNG or JPEG
- Screenshots: 1080 x 1920 px (9:16), minimum 2, maximum 8 per device type
- Screenshots must show actual app functionality (Play Store policy)
- No misleading content or fake UI elements
