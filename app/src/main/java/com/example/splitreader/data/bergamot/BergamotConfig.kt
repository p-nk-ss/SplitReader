package com.example.splitreader.data.bergamot

import java.io.File

/** en→zh/ja/ko packs ship a separate target vocab; every other pack shares one SentencePiece model. */
private fun targetVocab(packDir: File): File =
    File(packDir, BergamotModelStore.TARGET_VOCAB).takeIf { it.exists() } ?: File(packDir, BergamotModelStore.VOCAB)

/** Marian config for one Firefox-Translations pack. Same settings Firefox uses for base-memory/tiny models. */
fun bergamotConfigYaml(packDir: File): String = """
    models: [${File(packDir, BergamotModelStore.MODEL).absolutePath}]
    vocabs: [${File(packDir, BergamotModelStore.VOCAB).absolutePath}, ${targetVocab(packDir).absolutePath}]
    shortlist: [${File(packDir, BergamotModelStore.SHORTLIST).absolutePath}, false]
    beam-size: 1
    normalize: 1.0
    word-penalty: 0
    max-length-break: 128
    mini-batch-words: 1024
    workspace: 128
    max-length-factor: 2.0
    skip-cost: true
    cpu-threads: 0
    quiet: true
    quiet-translation: true
    gemm-precision: int8shiftAlphaAll
    alignment: soft
""".trimIndent()
