# bergamot-translator native build (Offline HQ)

`libbergamot_jni.so` is the native half of Mirrolit's **Offline HQ** translation engine: a thin
JNI wrapper around [bergamot-translator](https://github.com/browsermt/bergamot-translator), which
runs Mozilla's Firefox-Translations models entirely on device.

## Why the `.so` is committed

The library is a **prebuilt artifact checked into `app/src/main/jniLibs/`**. A normal
`./gradlew assembleDebug` therefore needs no NDK, no CMake and no 20-minute native build —
it just packages the two `.so` files. Only someone bumping the engine runs `build.sh`.

| ABI | size (stripped) |
|---|---|
| `arm64-v8a` | ~7.8 MB |
| `x86_64` | ~8.0 MB |

`app/build.gradle.kts` sets `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }`, so the APK
carries exactly these two. Note the real consequence: **32-bit-only devices (`armeabi-v7a`,
`x86`) cannot install the app at all** — they fail with `INSTALL_FAILED_NO_MATCHING_ABIS`, not
with a missing engine. That is accepted because Play has required 64-bit support since 2019.
To restore those devices, build `armeabi-v7a` in `build.sh` and add it to `abiFilters`.

## Pinned versions

| Item | Value |
|---|---|
| bergamot-translator | `9271618ebbdc5d21ac4dc4df9e72beb7ce644774` (v0.4.5+9271618, 2024-05-12) |
| vendored marian | `v1.9.56+2781d735` |
| Android NDK | `30.0.16138531` (clang 21) |
| CMake / ninja | Android SDK `cmake/4.1.2` |
| `ANDROID_PLATFORM` | `android-26` (matches `minSdk`) |

## Building

```bash
tools/bergamot/build.sh
```

Environment overrides: `NDK`, `SDK_CMAKE_BIN`, `WORK` (default `build/bergamot`), `JOBS`.
The script clones and patches the tree, builds the `bergamot-translator` static libraries for
both ABIs, links `jni/bergamot_jni.cpp` against them into one shared object, strips it and copies
it into `app/src/main/jniLibs/<abi>/`. It is re-runnable: patches are skipped when already
applied, and CMake configures incrementally.

Verify afterwards with the instrumented smoke test, which loads the library and checks that a
malformed config comes back as a failure rather than a crash:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.bergamot.BergamotNativeSmokeTest
```

## Patches (`patches/`, keyed to commit `9271618`)

Four source patches to vendored third-party code; two more fixes are CMake flags baked into
`build.sh` (`CMAKE_POLICY_VERSION_MINIMUM=3.5` for CMake 4's dropped `<3.5` compatibility, and
`CMAKE_HAVE_LIBC_PTHREAD=ON` because bionic's pthreads live in libc and `FindThreads` probes fail
under the NDK toolchain).

| Patch | Applied to | Why |
|---|---|---|
| `0001-pathie-no-iconv-on-android.patch` | marian-dev | bionic has no `iconv`; Android filesystem encoding is always UTF-8, so `convert_encodings()` is an identity function there. |
| `0002-pathie-no-glob-below-api-28.patch` | marian-dev | bionic gained `glob(3)` only at API 28. Nothing outside pathie calls `Path::glob`. |
| `0003-faiss-include-immintrin-on-x86.patch` | marian-dev | faiss uses `__m128` under `#ifdef __SSE__` without including `<immintrin.h>`; it only works upstream by transitive include. x86_64 only. |
| `0004-sentencepiece-trainer-kanytype-const.patch` | marian-dev's sentencepiece submodule | clang 21 rejects the out-of-range `static_cast` in a `constexpr` initialiser. Trainer code, never executed on device. |

## Pinning the models (`pin-manifest.py`)

`app/src/main/assets/bergamot/manifest.json` is a pinned copy of Mozilla's live `models.json`,
narrowed to the languages in `Language.kt`. Regenerate it with:

```bash
python3 tools/bergamot/pin-manifest.py --version N
```

`--version` is required and is written into the manifest. It is not decoration: the store stamps
every pack directory on disk with it and treats a pack whose marker names a different version as
not installed. **Bump it whenever any pack changes** — a new URL, a new hash, a different
architecture — or devices that already hold the old files will keep them forever, and a half-
finished `.part` from the old version can be resumed into the new pack. The script refuses to
write when the packs differ from the committed manifest but `--version` has not moved.

## Licences and attribution

The repo-root `THIRD_PARTY_LICENSES.md` is the authoritative list (name, licence, copyright holder,
source URL) and is what the in-app About screen points readers at. In summary:

- **bergamot-translator** — MPL-2.0, © the Bergamot project contributors.
- **marian-nmt** (vendored) and **intgemm** — MIT, © the Marian NMT authors / University of Edinburgh.
- **ssplit-cpp** — Apache-2.0, © University of Edinburgh. Its `nonbreaking_prefixes` data files are
  LGPL-2.1 (read at runtime, not compiled in).
- **sentencepiece**, **ruy**, **abseil** — Apache-2.0, © Google.
- **pcre2** — BSD-3-Clause; **protobuf-lite**, **cpuinfo**, **darts-clone** — BSD-style.
- **Translation models** — Mozilla's [translations](https://github.com/mozilla/translations) models,
  **MPL-2.0** ("The model files are distributed under the MPL 2.0 license"). They are downloaded at
  runtime, not bundled, and are attributed in-app. *(This previously said CC-BY-SA-4.0, taken from
  the archived `firefox-translations-models` repo; the models the app actually downloads come from
  the `mozilla/translations` bucket and are MPL-2.0.)*

MPL-2.0 obliges us to offer the source of the covered files. It is the pinned upstream commit —
`browsermt/bergamot-translator@9271618ebbdc5d21ac4dc4df9e72beb7ce644774` — recorded here and in
`THIRD_PARTY_LICENSES.md`.
