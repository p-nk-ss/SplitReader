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
carries exactly these two. On any other ABI the Offline HQ provider is simply absent and ML Kit
stays the offline engine.

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

Three source patches to vendored third-party code; two more fixes are CMake flags baked into
`build.sh` (`CMAKE_POLICY_VERSION_MINIMUM=3.5` for CMake 4's dropped `<3.5` compatibility, and
`CMAKE_HAVE_LIBC_PTHREAD=ON` because bionic's pthreads live in libc and `FindThreads` probes fail
under the NDK toolchain).

| Patch | Applied to | Why |
|---|---|---|
| `0001-pathie-no-iconv-on-android.patch` | marian-dev | bionic has no `iconv`; Android filesystem encoding is always UTF-8, so `convert_encodings()` is an identity function there. |
| `0002-pathie-no-glob-below-api-28.patch` | marian-dev | bionic gained `glob(3)` only at API 28. Nothing outside pathie calls `Path::glob`. |
| `0003-faiss-include-immintrin-on-x86.patch` | marian-dev | faiss uses `__m128` under `#ifdef __SSE__` without including `<immintrin.h>`; it only works upstream by transitive include. x86_64 only. |
| `0004-sentencepiece-trainer-kanytype-const.patch` | marian-dev's sentencepiece submodule | clang 21 rejects the out-of-range `static_cast` in a `constexpr` initialiser. Trainer code, never executed on device. |

## Licences and attribution

- **bergamot-translator** — MPL-2.0, © the Bergamot project contributors.
- **marian-nmt** (vendored) — MIT, © the Marian NMT authors.
- **sentencepiece**, **ruy**, **cpuinfo**, **intgemm**, **pcre2**, **ssplit-cpp** and the other
  vendored third-party libraries keep their own licences; see the sources under the pinned commit.
- **Translation models** — Mozilla's [firefox-translations-models](https://github.com/mozilla/firefox-translations-models),
  CC-BY-SA-4.0. They are downloaded at runtime, not bundled, and must be attributed in-app.
