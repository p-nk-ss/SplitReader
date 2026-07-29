# Setup

How to get this project building from a fresh clone.

## 1. Prerequisites

| | |
|---|---|
| JDK | **17** — required to *run* the Gradle build (AGP 8.11.2). The module itself compiles to Java 11 bytecode. Android Studio ships a bundled JDK that works. |
| Android Studio | any recent release |
| Android SDK Platform | **36** (`compileSdk` / `targetSdk`) |
| Gradle | not needed separately — the wrapper pins **8.13** |
| Device / emulator | Android 8.0+ (API 26) |

## 2. Clone and point Gradle at the SDK

```bash
git clone <repo-url> SplitReader
cd SplitReader
```

Create `local.properties` in the project root (gitignored — it is machine-specific):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Windows uses an escaped path:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Opening the project in Android Studio creates this file for you.

## 3. Firebase config — required, not in the repo

The build applies the `com.google.gms.google-services` and Crashlytics plugins, so
**`app/google-services.json` must exist or the build fails** with
*"File google-services.json is missing"*. The file is gitignored because it is tied to a
specific Firebase project.

Supply your own:

1. Create a Firebase project.
2. Add an Android app with package name **`io.mirrolit.app`** — this must match `applicationId`
   in `app/build.gradle.kts`, or change `applicationId` to your own.
3. Enable **Authentication** (Email/Password + Google) if you want sign-in to work; enable
   **Crashlytics** for release crash reporting.
4. Download `google-services.json` into `app/`.

Reading books and translating work without signing in — Firebase is only needed for the optional
account features and crash reporting.

## 4. Build and test

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # install on a connected device
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:verifyRoborazziDebug   # screenshot-golden regression suite
```

On Windows use `gradlew.bat`. The first run downloads dependencies and takes a while.

### Screenshot goldens

Screenshot tests render production composables under Robolectric with a pinned SDK 34 and a
tablet configuration; goldens live in `app/src/test/screenshots/`. Compare tolerance is 1% to
absorb sub-pixel text antialiasing. To re-record deliberately:

```bash
./gradlew :app:recordRoborazziDebug
```

Then review every `*_compare.png` before committing. See
`app/src/test/screenshots/README.md`.

### Instrumented tests

`app/src/androidTest` needs a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Parser tests read real books staged from a `qa_book/` directory in the project root. Those files
are copyright-protected and therefore **not distributed with the repository**; a missing
`qa_book/` is tolerated — the staged asset dir is simply empty and those cases are skipped. To
run them, drop your own EPUB/FB2/MOBI files into `qa_book/`.

## 5. Release signing — optional

Release builds work unsigned without any extra setup, so a fresh clone and CI keep building.
To sign, copy the template and fill it in:

```bash
cp keystore.properties.template keystore.properties
keytool -genkeypair -v -keystore mirrolit-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keystore.properties` and `*.jks` are gitignored — never commit them.

One guard to know about: if a keystore is present but `billingPublicKey` is blank,
`bundleRelease` and `assembleRelease` **fail on purpose**. That key is the base64 RSA licensing
key from Play Console → Monetization setup → Licensing, and without it purchase-signature
verification silently fails open. Debug and CI builds are unaffected.

```bash
./gradlew :app:bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
```

## 6. Translation engines

The default translator is **on-device ML Kit** — no key, no account, works offline once the
language model is downloaded. Online engines (DeepL, Google Cloud, Azure, LibreTranslate, Quick
Translate) are optional and each needs your own API key, entered in Settings. Keys are encrypted
at rest with an Android Keystore key and excluded from backup.

## 7. Emulator caveats

Two things do not work on a stock emulator and need a physical device:

- **ML Kit model download** — fails on SSL/IPv6.
- **Project Gutenberg catalog** — unreachable over IPv6.

## Further reading

- `docs/release_plan.md` — release checklist
- `docs/play_console_release.md` — Play Console walkthrough
- `docs/test_plan.md` — test backlog
- `docs/privacy_policy.md` — privacy policy source

> Maintainer note: the working context used during development (assistant instructions, design
> specs, knowledge graph) is kept on a private mirror, not in this repository. Its bootstrap
> instructions live there.
