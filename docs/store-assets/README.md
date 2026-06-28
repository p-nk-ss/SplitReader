# Store assets (Google Play listing)

Graphics for the Play Console listing. All screenshots captured on the Pixel Tablet
emulator (2560×1600, landscape) running the release-equivalent debug build with the
Mirrolit branding and a real book (public-domain *Alice's Adventures in Wonderland*).

| File | Use | Status |
|------|-----|--------|
| `feature-graphic.png` | **Feature graphic** (1024×500) | ✅ ready |
| `01-library.png` | Screenshot — Library / home (continue reading, streak, shelf) | ✅ ready |
| `02-reading-stats.png` | Screenshot — Almanac (streak, minutes, heatmap, by-book/language) | ✅ ready |
| `03-translation-engines.png` | Screenshot — translator picker (ML Kit + 5 providers) | ✅ ready |
| `04-appearance.png` | Screenshot — Settings (themes + 9 typefaces + typography) | ✅ ready |
| `05-reader.png` | Screenshot — Reader split-pane (EN original + live translation) | ✅ ready |
| `ic_launcher-playstore.png` (in `app/src/main/`) | App icon (512×512) | ✅ ready |
| `full-description.txt` | Listing full description (EN, <4000 chars) | ✅ ready |
| `short-description.txt` | Listing short description (EN, <80 chars) | ✅ ready |

`05-reader.png` is the hero shot: the English original on the left and a live on-device
translation on the right, side by side (captured once the ML Kit language model finished
downloading).

## Play Console requirements (reminder)

- **Phone screenshots:** 2–8, PNG/JPG, 16:9 or 9:16, each side 320–3840 px.
- **Tablet screenshots:** these landscape 2560×1600 shots suit the 10" tablet slots.
  For phone slots, also capture a few in portrait on a phone/emulator.
- **Feature graphic:** exactly 1024×500, no alpha.
- **Icon:** 512×512 32-bit PNG (see `app/src/main/ic_launcher-playstore.png`).
- Screenshots must show only **public-domain** book content (these use Alice in Wonderland).

## Regenerating the graphics

`feature-graphic.png` and the icons are generated from `MiroLit_logo.png` by
`../../generate_icons.py` (icons) and an inline Pillow snippet (feature graphic). Re-run
with the project's Python + Pillow.
