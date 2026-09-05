# Offline HQ (Bergamot) — architecture

Offline HQ is the second on-device translator: Mozilla's Firefox-Translations models run by
[bergamot-translator](https://github.com/browsermt/bergamot-translator) through a prebuilt JNI
library. Higher quality than ML Kit, at the cost of a ~17–44 MB pack per language pair. The full
design lives in `docs/superpowers/specs/2026-09-04-bergamot-offline-hq-design.md`; this is the
condensed map of what ships.

## Units

| Unit | Responsibility |
|---|---|
| `tools/bergamot/` | One-shot native build (`build.sh`), source patches, model pinning (`pin-manifest.py`). Not part of the Gradle build. |
| `app/src/main/jniLibs/<abi>/libbergamot_jni.so` | Committed prebuilt engine (arm64-v8a, x86_64 only — 32-bit devices cannot install the app). |
| `data/bergamot/NativeBridge` + `JniNativeBridge` | The four native calls: `load`, `translate`, `unload`, `lastError`. `loadOrNull` turns a missing library into `null`, never a crash. |
| `data/bergamot/BergamotEngine` | Owns every native handle. One mutex, one dedicated thread, LRU of at most `maxLoaded` (2) resident models. |
| `data/bergamot/BergamotManifest` | Parsed `assets/bergamot/manifest.json`: version, base URL, and one `ManifestPack` per direction. |
| `data/bergamot/BergamotModelStore` | Pack files on disk: download, resume, verify, install, delete, list. Implements the `OfflineModelStore` domain port. |
| `data/bergamot/OkHttpPackFetcher` | The store's only door to the network; `Range` requests and status mapping. |
| `data/bergamot/routeFor` | Language pair → the models to run, in order. |
| `data/translator/BergamotTranslationProvider` | `TranslationProviderApi` implementation: route × store × engine. |
| `presentation/settings` Language packs | Install/delete packs and show what they cost on disk. |

## Pack lifecycle on disk

Root is `filesDir/bergamot/`, one directory per pair: `<root>/<src>-<tgt>/`.

1. `ensure(pair)` returns immediately if the **marker** `installed.v1` exists *and* holds this
   manifest's version.
2. Free space is probed (2× the model's uncompressed size) **before** anything is created, walking
   up to a directory that exists — `usableSpace` reports 0 for a path that does not.
3. A directory whose **stamp** `manifest.version` does not match this manifest is wiped: a v1
   `model.bin`, or worse a v1 `.part`, must never be resumed into a v2 pack. The stamp is then
   written first into the fresh directory.
4. Each file is fetched to `<name>.gz.part` (resumable, `Range: bytes=N-`), gunzipped to
   `<name>.tmp`, hashed (model only — the manifest ships no hash for vocab/shortlist), and renamed
   into place. A bad hash deletes the part and retries once.
5. The marker is written **last**, then `installedFlow` is refreshed, then `1f` is emitted. Progress
   is byte-weighted (0.9 model, 0.1 shared by the small files) and capped at 0.99 until the marker
   exists, so a caller waiting for `>= 1f` never sees an uninstalled pack.

`installed()` seeds itself on the io dispatcher on first collection — never in the constructor,
which Hilt runs on the main thread.

## Pivot

`routeFor` yields one model for a pair touching English and two otherwise: `ru→de` runs `ru-en`
then `en-de`, feeding the first model's output into the second. Every `ModelPair` has English on
one side by construction. `prepare()` ensures each leg and averages progress across the route;
`supports()` is false unless **every** leg is in the manifest.

## Errors

| Condition | Exception | What the reader sees |
|---|---|---|
| Pack fetch failed | `OfflinePackDownloadException` | "Couldn't download the Offline HQ language pack (…MB). Check your internet and retry." |
| Bad hash twice | `OfflinePackCorruptException` | "The Offline HQ pack was corrupted and could not be re-downloaded." |
| No room | `InsufficientStorageException` | "Not enough storage for the Offline HQ pack (needs … MB free)." |
| Library missing / native call failed | `OfflineEngineUnavailableException` | "Offline HQ isn't available on this device — using ML Kit." |
| Server ignored `Range` | `RangeNotSupported` (internal) | nothing — the store restarts the file from 0 |

`TranslateTextUseCase` maps these; the pack download runs inside the `DownloadingModel` banner, so
a multi-megabyte wait never looks like a hang.

The provider retires itself for the session (`sessionBroken`) only on a **terminal** failure: the
library is missing, or the engine failed while already `degraded`. A single bad native call on a
healthy engine costs the reader that paragraph, not the provider.

## Memory

Native models are large and invisible to the JVM heap. So: at most two resident (a pivot needs
two), least-recently-used evicted first, everything dropped on `onTrimMemory`. A trim that arrives
while a translation holds the mutex is recorded and honoured by that translation on its way out.
A failed `load` is read as "that many models did not fit": evict everything, drop the ceiling to
one for the rest of the session, retry once — and only then give up.

The native library itself is resolved lazily, on the first translation, because the Application
field-injects the engine and `System.loadLibrary` of an 8 MB `.so` does not belong on a cold-start
main thread. Anything touching `engine.available` must therefore be off the main thread.

## Rebuilding

```bash
tools/bergamot/build.sh                          # NDK 30.0.16138531 + SDK cmake 4.1.2; ~20 min
python3 tools/bergamot/pin-manifest.py --version N   # bump N whenever any pack changes
```

`build.sh` is re-runnable (patches are skipped when applied, CMake configures incrementally) and
drops the stripped `.so` straight into `app/src/main/jniLibs/`. Details, patches and licences:
`tools/bergamot/README.md` and the repo-root `THIRD_PARTY_LICENSES.md`.

## Device tests

The emulator can run the engine, but only a real device gives meaningful latency.

```bash
# native library loads, malformed config fails instead of crashing
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.bergamot.BergamotNativeSmokeTest

# real pack download, pivot translation, warm per-paragraph latency (≤300 ms)
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.bergamot.BergamotEngineDeviceTest
```

Latency tracks the **longest sentence**, not the word count, so the latency test uses ordinary
multi-sentence prose; a single run-on sentence of the same length costs roughly twice as much.
