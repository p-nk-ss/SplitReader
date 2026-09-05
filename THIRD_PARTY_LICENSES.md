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
