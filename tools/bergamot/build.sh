#!/usr/bin/env bash
#
# Builds libbergamot_jni.so for arm64-v8a and x86_64 and drops them into app/src/main/jniLibs.
#
# This is a one-shot developer script: the resulting .so files are committed, so a normal
# `./gradlew assembleDebug` needs no NDK. Re-run it only when bumping BERGAMOT_COMMIT or the
# JNI glue in tools/bergamot/jni/bergamot_jni.cpp. See tools/bergamot/README.md.
#
# Requires: Android NDK 30.0.16138531 and the SDK's CMake 4.1.2 (which ships ninja).
# Override with NDK=..., SDK_CMAKE_BIN=..., WORK=... in the environment.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK="${NDK:-$HOME/Library/Android/sdk/ndk/30.0.16138531}"
SDK_CMAKE_BIN="${SDK_CMAKE_BIN:-$HOME/Library/Android/sdk/cmake/4.1.2/bin}"
WORK="${WORK:-$ROOT/build/bergamot}"
BERGAMOT_REPO="https://github.com/browsermt/bergamot-translator.git"
BERGAMOT_COMMIT="9271618ebbdc5d21ac4dc4df9e72beb7ce644774"   # v0.4.5+9271618, 2024-05-12
API=26
JOBS="${JOBS:-$(sysctl -n hw.logicalcpu 2>/dev/null || echo 8)}"

# CMake 4 dropped compatibility with `cmake_minimum_required(<3.5)`, which ruy's clog and pcre2
# still declare. The variable has to be in the *environment* too: pcre2 is an ExternalProject
# whose sub-cmake does not inherit cache variables.
export CMAKE_POLICY_VERSION_MINIMUM=3.5
export PATH="$SDK_CMAKE_BIN:$PATH"

CMAKE="$SDK_CMAKE_BIN/cmake"
NINJA="$SDK_CMAKE_BIN/ninja"
TOOLCHAIN_BIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"

for f in "$CMAKE" "$NINJA" "$TOOLCHAIN_BIN/clang++" "$TOOLCHAIN_BIN/llvm-strip"; do
  [ -x "$f" ] || { echo "missing required tool: $f" >&2; exit 1; }
done

SRC="$WORK/src"
MARIAN="$SRC/3rd_party/marian-dev"
SENTENCEPIECE="$MARIAN/src/3rd_party/sentencepiece"
PATCHES="$ROOT/tools/bergamot/patches"

mkdir -p "$WORK"

echo "==> checkout $BERGAMOT_COMMIT"
[ -d "$SRC/.git" ] || git clone --recursive "$BERGAMOT_REPO" "$SRC"
git -C "$SRC" checkout --quiet "$BERGAMOT_COMMIT"
git -C "$SRC" submodule update --init --recursive --quiet

# Applies a patch unless it is already applied, so the script is re-runnable.
apply_patch() {
  local repo="$1" patch="$2"
  if git -C "$repo" apply -R --check "$patch" >/dev/null 2>&1; then
    echo "    already applied: $(basename "$patch")"
  else
    git -C "$repo" apply "$patch"
    echo "    applied: $(basename "$patch")"
  fi
}

echo "==> patches"
apply_patch "$MARIAN" "$PATCHES/0001-pathie-no-iconv-on-android.patch"
apply_patch "$MARIAN" "$PATCHES/0002-pathie-no-glob-below-api-28.patch"
apply_patch "$MARIAN" "$PATCHES/0003-faiss-include-immintrin-on-x86.patch"
apply_patch "$SENTENCEPIECE" "$PATCHES/0004-sentencepiece-trainer-kanytype-const.patch"

build_abi() {
  local abi="$1" march="$2" target="$3"; shift 3
  local out="$WORK/build-$abi"

  echo "==> cmake configure $abi"
  "$CMAKE" -S "$SRC" -B "$out" -G Ninja \
    -DCMAKE_MAKE_PROGRAM="$NINJA" \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_HAVE_LIBC_PTHREAD=ON \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_ARCH="$march" \
    -DCOMPILE_CUDA=OFF -DCOMPILE_TESTS=OFF -DCOMPILE_WASM=OFF \
    -DUSE_SENTENCEPIECE=ON -DUSE_MKL=OFF -DUSE_FBGEMM=OFF -DUSE_NCCL=OFF -DUSE_DOXYGEN=OFF \
    -DUSE_STATIC_LIBS=ON -DSSPLIT_USE_INTERNAL_PCRE2=ON \
    "$@"

  echo "==> build $abi"
  "$CMAKE" --build "$out" --target bergamot-translator -j "$JOBS"

  echo "==> link libbergamot_jni.so ($abi)"
  # Order matters for static archives; --start-group lets the linker iterate over cycles.
  local archives=(
    "$out/src/translator/libbergamot-translator.a"
    "$out/libmarian.a"
    "$out/libssplit.a"
    "$out/lib/libpcre2-8.a"
  )
  # sentencepiece, ruy/cpuinfo/clog (arm64) or intgemm (x86_64)
  while IFS= read -r a; do archives+=("$a"); done < <(find "$out/3rd_party" -name '*.a' | sort)

  "$TOOLCHAIN_BIN/clang++" --target="$target$API" \
    -std=gnu++17 -O3 -DNDEBUG -fPIC -shared -pthread -static-libstdc++ \
    -Wno-unused-result -Wno-unknown-warning-option -Wno-unused-value \
    -Wno-enum-constexpr-conversion -fno-strict-aliasing -Wno-comment \
    -Wno-deprecated-declarations -Wno-unknown-pragmas \
    -DCOMPILE_CPU=1 -DUSE_SENTENCEPIECE -D_USE_INTERNAL_STRING_VIEW -DUSE_PTHREADS \
    "${JNI_DEFINES[@]}" \
    -I"$SRC" -I"$SRC/src" \
    -I"$MARIAN/src" -I"$MARIAN/src/3rd_party" \
    -I"$MARIAN/src/3rd_party/SQLiteCpp/include" \
    -I"$MARIAN/src/3rd_party/sentencepiece" \
    -I"$MARIAN/src/3rd_party/sentencepiece/third_party/protobuf-lite" \
    -I"$MARIAN/src/3rd_party/intgemm" \
    -I"$MARIAN/src/3rd_party/ruy" \
    -I"$MARIAN/src/3rd_party/ruy/third_party/cpuinfo/include" \
    -I"$SRC/3rd_party/ssplit-cpp/src/ssplit" \
    -I"$SRC/3rd_party/ssplit-cpp/src/3rd-party/CLI11" \
    -I"$out/3rd_party/marian-dev/src/3rd_party/intgemm" \
    -I"$out/3rd_party/marian-dev/src/3rd_party" \
    -I"$out/local/include" -I"$out/include" \
    "$ROOT/tools/bergamot/jni/bergamot_jni.cpp" \
    -Wl,--gc-sections -Wl,--no-undefined -Wl,--exclude-libs,ALL \
    -Wl,--start-group "${archives[@]}" -Wl,--end-group \
    -landroid -llog -ldl -latomic -lm \
    -o "$out/libbergamot_jni.so"

  "$TOOLCHAIN_BIN/llvm-strip" --strip-all "$out/libbergamot_jni.so"
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  cp "$out/libbergamot_jni.so" "$ROOT/app/src/main/jniLibs/$abi/"
  ls -l "$ROOT/app/src/main/jniLibs/$abi/libbergamot_jni.so"
}

# ruy/USE_RUY_SGEMM/USE_SIMD_UTILS are switched on automatically by marian's CMake when it
# detects an arm arch — passing them explicitly is not needed and BUILD_ARCH must be set
# because marian defaults it to `native`, which on an Apple-Silicon host resolves to a CPU
# name the NDK's cross compilers reject.
# -DSSE/-DFMA select simd_utils' NEON emulation of the SSE intrinsics marian's
# functional/operators.h uses; marian's own CMake passes the same set on arm.
JNI_DEFINES=(-DARM -DSSE -DFMA -DUSE_RUY_SGEMM=1 -DCPUINFO_SUPPORTED_PLATFORM=1
             -march=armv8-a -flax-vector-conversions)
build_abi arm64-v8a armv8-a aarch64-linux-android

JNI_DEFINES=(-DUSE_INTGEMM=1 -march=x86-64 -msse4.1)
build_abi x86_64 x86-64 x86_64-linux-android -DUSE_INTGEMM=ON -DUSE_RUY_SGEMM=OFF

echo "==> done"
