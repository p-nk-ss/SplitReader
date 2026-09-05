# Third-party licences

Mirrolit ships and downloads software written by other people. This file names each piece, its
licence, its copyright holder and where the source can be had. It is what the app's
**Settings → About → Open-source licences** row points at.

Everything below concerns the **Offline HQ** translation engine, which is the only part of the app
built from third-party native source. Ordinary Android/Jetpack, Firebase, OkHttp/Retrofit, Gson and
Jsoup dependencies are resolved by Gradle from their published artifacts under their own licences
(Apache-2.0 except Jsoup, MIT).

## Source offer (MPL-2.0 §3.2)

`libbergamot_jni.so` statically links MPL-2.0 code. Complete corresponding source for those files —
in the exact revision the shipped library was built from — is available at:

**<https://github.com/browsermt/bergamot-translator/tree/9271618ebbdc5d21ac4dc4df9e72beb7ce644774>**

That commit is pinned as `BERGAMOT_COMMIT` in `tools/bergamot/build.sh`, together with the four
patches in `tools/bergamot/patches/` that are applied to it. Its recursive submodules pin every
component listed below; `tools/bergamot/README.md` documents how to reproduce the build.

## The native engine

| Component | Licence | Copyright | Source |
|---|---|---|---|
| bergamot-translator | MPL-2.0 | The Bergamot project contributors | <https://github.com/browsermt/bergamot-translator/tree/9271618ebbdc5d21ac4dc4df9e72beb7ce644774> |
| marian-dev (marian-nmt) | MIT | Marcin Junczys-Dowmunt, the University of Edinburgh, Adam Mickiewicz University | <https://github.com/browsermt/marian-dev/tree/2781d735d4a10dca876d61be587afdab2726293c> |
| intgemm | MIT | University of Edinburgh, Nikolay Bogoychev, Mateusz Chudyk, Kenneth Heafield, Microsoft Corporation | <https://github.com/kpu/intgemm/tree/f7401513da71758dacce52fed1c7855549abee59> |
| ssplit-cpp | Apache-2.0 | University of Edinburgh (2019) | <https://github.com/browsermt/ssplit-cpp/tree/a311f9865ade34db1e8e080e6cc146f55dafb067> |
| ssplit-cpp `nonbreaking_prefixes` data | LGPL-2.1 | The Moses decoder authors | <https://github.com/moses-smt/mosesdecoder/tree/master/scripts/share/nonbreaking_prefixes> |
| sentencepiece | Apache-2.0 | Google LLC | [browsermt/sentencepiece@ae41b77](https://github.com/browsermt/sentencepiece/tree/ae41b7740d7006596bb9257e83340b2620db9d00), a fork of [google/sentencepiece](https://github.com/google/sentencepiece) |
| abseil (vendored in sentencepiece) | Apache-2.0 | The Abseil Authors / Google LLC | <https://github.com/abseil/abseil-cpp> |
| protobuf-lite (vendored in sentencepiece) | BSD-3-Clause | Google Inc. (2008) | <https://github.com/protocolbuffers/protobuf> |
| darts-clone (vendored in sentencepiece) | BSD-2-Clause | Susumu Yata (2008–2011) | <https://github.com/s-yata/darts-clone> |
| esaxx (vendored in sentencepiece) | MIT | Daisuke Okanohara (2010) | <https://github.com/hillbig/esaxx> |
| ruy | Apache-2.0 | Google LLC | <https://github.com/google/ruy/tree/2d950b3bfa7ebfbe7a97ecb44b1cc4da5ac1d6f0> |
| cpuinfo | BSD-2-Clause | Google LLC (2019), Facebook Inc. (2017–2018), Georgia Institute of Technology (2012–2017), Marat Dukhan (2010–2012) | <https://github.com/pytorch/cpuinfo/tree/5916273f79a21551890fd3d56fc5375a78d1598d> |
| PCRE2 10.39 | BSD-3-Clause | University of Cambridge, Philip Hazel, Zoltan Herczeg | <https://github.com/PCRE2Project/pcre2/tree/pcre2-10.39> |
| yaml-cpp (vendored in marian-dev) | MIT | Jesse Beder (2008–2015) | <https://github.com/jbeder/yaml-cpp> |
| zlib (vendored in marian-dev) | zlib License | Jean-loup Gailly and Mark Adler (1995–2017) | see upstream — <https://github.com/madler/zlib>; no `LICENSE` file in the vendored copy, licence text is embedded in `zlib.h`'s header comment |
| pathie-cpp (vendored in marian-dev) | BSD-2-Clause | Marvin Gülker (2015, 2017) | <https://github.com/Quintus/pathie-cpp> |
| faiss (vendored in marian-dev) | MIT | Facebook, Inc. and its affiliates | <https://github.com/facebookresearch/faiss> |
| cnpy (vendored in marian-dev) | MIT | Carl Rogers (2011) | <https://github.com/rogersce/cnpy> |
| phf (vendored in marian-dev) | MIT | William Ahern (2014–2015) | <https://github.com/wahern/phf> |
| CLI11 (vendored in marian-dev as `3rd_party/CLI`, header-only) | BSD-3-Clause | University of Cincinnati, developed by Henry Schreiner under NSF AWARD 1414736 (2017–2018) | <https://github.com/CLIUtils/CLI11> |
| onnx protobuf schema (vendored in marian-dev as `3rd_party/onnx`) | MIT (per the vendored file's own header) | Facebook Inc. and Microsoft Corporation | see upstream — <https://github.com/onnx/onnx>; no `LICENSE` file in the vendored copy, `onnx-ml.proto`'s header comment states "Copyright (c) Facebook Inc. and Microsoft Corporation. Licensed under the MIT license." |
| half_float / umHalf (vendored in marian-dev, header-only, included unconditionally by `common/types.h`) | BSD-3-Clause | Chris Maiwald, Alexander Gessler (2006–2008) | see upstream — <https://github.com/acgessler/half_float>; no `LICENSE` file in the vendored copy, licence stated in `Readme.md` ("3-clause BSD license") and in `umHalf.h`'s header comment |
| zstr (vendored in marian-dev, header-only, included by `common/file_stream.h` whenever `WASM_COMPATIBLE_SOURCE` is undefined — true for both Android ABIs) | MIT | Matei David, Ontario Institute for Cancer Research (2015) | <https://github.com/mateidavid/zstr> |
| mio (vendored in marian-dev, header-only, included unconditionally by `translator/translator.h`, `translator/scorers.h`, `data/shortlist.h`) | MIT | https://github.com/mandreyel/ (2018) | <https://github.com/mandreyel/mio> |
| simd_utils (vendored in marian-dev, header-only, included by `functional/operators.h` only under `__ARM_NEON`/`__ARM_NEON__` — **arm64-v8a only**, not compiled on x86_64) | BSD-2-Clause | JishinMaster (2019) | <https://github.com/browsermt/simd_utils> (submodule fork of <https://github.com/JishinMaster/simd_utils>) |

Per-ABI note: `intgemm` (real SIMD matrix-multiply kernels) is compiled and linked only into the
**x86_64** build; the arm64-v8a build instead links marian's `wasm_intgemm_fallback.cpp` stub and uses
**ruy** for quantized matmul, so `intgemm`'s licence terms above apply to the x86_64 `.so` only, while
`simd_utils` (above) is the mirror case — compiled only into the **arm64-v8a** `.so`, gated on NEON.

`spdlog` is vendored under `3rd_party/spdlog` but is **not** linked into `libbergamot_jni.so`: it is
referenced only from spdlog's own `bench/`/test sources, never from marian's actual library sources, and
no `spdlog*.o` object file exists in either ABI's build tree — so it is omitted from the table above.

### NOTICE files

The Apache-2.0 components above (sentencepiece, ruy, abseil, ssplit-cpp) ship **no `NOTICE` file** in
the revisions pinned by `BERGAMOT_COMMIT`; the checkout produced by `tools/bergamot/build.sh` contains
none. There is therefore no NOTICE text to reproduce, and the attributions in the table above are the
required notice. Should a future bump introduce one, its text belongs verbatim in this section.

## Translation models

| Component | Licence | Copyright | Source |
|---|---|---|---|
| Mozilla Firefox-Translations models | MPL-2.0 | Mozilla and contributors | <https://github.com/mozilla/translations> |

The models are **not bundled** in the app. Offline HQ downloads the pinned set
(`app/src/main/assets/bergamot/manifest.json`) from Mozilla's public Google Cloud Storage bucket the
first time a reader picks a language pair. The `mozilla/translations` README states: *"The model
files are distributed under the MPL 2.0 license."* Source, training pipeline and the models
themselves are at the URL above.
