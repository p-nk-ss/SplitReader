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
| `05-reader-draft.png` | Screenshot — Reader split-pane | ⚠️ **retake on a real device** |
| `ic_launcher-playstore.png` (in `app/src/main/`) | App icon (512×512) | ✅ ready |

## ⚠️ The reader screenshot (`05-reader-draft.png`)

The split-pane is real (original on the left, translation column on the right), but the
**translation didn't render** in this capture: the emulator couldn't download the ML Kit
offline language model (an emulator SSL/IPv6 limitation — `DownloadManager` failed with
`DECRYPTION_FAILED_OR_BAD_RECORD_MAC`), so the right pane shows the loading shimmer.

**Action:** retake this one shot on a **real device with normal network** — open Alice
(or any book), set the target language (e.g. EN → RU), wait a few seconds for the model
to download, and capture once the right pane fills with the live translation. That filled
side-by-side view is the app's hero shot.

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
