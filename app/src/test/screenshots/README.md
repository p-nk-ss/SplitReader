# V0 — Screenshot goldens · how to record & verify

JVM screenshot tests (Roborazzi + Robolectric, `@GraphicsMode NATIVE`) that render the production
**stateless** composables and snapshot them to committed PNG goldens. They are the visual regression
net for the V1–V6 redesign phases: any unintended change to a screen's rendering fails `verify`.

## Where things live

| Thing | Path |
|-------|------|
| Test sources | `app/src/test/java/com/example/splitreader/screenshot/` |
| Golden PNGs (committed) | `app/src/test/screenshots/` |
| Harness base class | `screenshot/ScreenshotTest.kt` |
| Fake-state fixtures | `screenshot/ScreenFixtures.kt` |
| Diff/compare output (on failure, **not** committed) | `app/build/outputs/roborazzi/*_compare.png` |

Reference device: tablet landscape `w1280dp-h800dp-xhdpi`, pinned SDK 34 (Robolectric 4.13 ships SDK
jars up to API 34), stub `android.app.Application` (no Firebase). See `ScreenshotTest.kt` header.

## Commands

```bash
# Verify every golden (the regression check — run this before merging UI work)
./gradlew :app:verifyRoborazziDebug

# Verify one class
./gradlew :app:verifyRoborazziDebug --tests "*LibraryScreensScreenshotTest"

# Re-record goldens after an INTENTIONAL visual change (see below), all or one class
./gradlew :app:recordRoborazziDebug
./gradlew :app:recordRoborazziDebug --tests "*ReadingScreensScreenshotTest"
```

> Plain `./gradlew :app:testDebugUnitTest` does **not** compare pixels (Roborazzi is a no-op without
> the record/verify flag) — it only proves the tests compile and run. Use `verifyRoborazziDebug` for
> the actual visual check.

## Compare tolerance

`ScreenshotTest` compares with `CompareOptions(changeThreshold = 0.01f)` — up to 1% of pixels may
differ. Text-heavy Material chrome shows sub-pixel anti-aliasing jitter on glyph edges that varies
run-to-run (a few pixels flip even between a record and an immediately-following verify); an exact
0-tolerance compare fails on that noise. 1% absorbs the jitter while still catching real regressions
— a palette swap, layout shift, or text change moves far more than 1% of the frame.

## Intentionally changing a screen (V1–V6)

When you deliberately restyle a screen, its golden **should** change — that's the point.

1. Make the UI change.
2. `./gradlew :app:verifyRoborazziDebug` → it fails for the affected goldens.
3. Open the `*_compare.png` for each failure (`app/build/outputs/roborazzi/`) and confirm the diff is
   exactly what you intended — reference | diff | new, side by side.
4. Re-record: `./gradlew :app:recordRoborazziDebug --tests "*TheAffectedClass"`.
5. `git add app/src/test/screenshots/` and commit the updated goldens **with** the UI change, so the
   new baseline and the code that produced it land together.

Never re-record blindly to make a red build green — always eyeball the compare image first.

## Coverage (V0 baseline)

Base matrix per screen: palette `{PAPER, NIGHT}` × fontScale `{1f, 1.3f}`.

- **Library** (`LibraryScreensScreenshotTest`): Home, Words (+ empty state), Catalog.
- **Forms** (`FormScreensScreenshotTest`): Settings, Profile, Auth.
- **Reading** (`ReadingScreensScreenshotTest`): Almanac, ReaderContent (fully-translated, settled — no
  shimmer placeholder).
- **Spot cases** (`PaletteAndRtlScreenshotTest`): Home in `SEPIA` + `AMOLED`; RTL layout for Reader
  and Words.
- Harness smoke: `HarnessSmokeTest`.
