package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.ModelPair
import com.google.gson.Gson

data class ManifestFile(val path: String, val size: Long? = null, val sha256: String? = null)

data class ManifestPack(
    val source: String,
    val target: String,
    val architecture: String,
    val model: ManifestFile,
    val vocab: ManifestFile,
    val shortlist: ManifestFile,
) {
    val pair: ModelPair get() = ModelPair(languageOf(source), languageOf(target))
}

/** Pinned copy of Mozilla's model registry for the languages this app supports (assets/bergamot/manifest.json). */
data class BergamotManifest(val version: Int, val baseUrl: String, val packs: List<ManifestPack>) {
    fun find(pair: ModelPair): ManifestPack? =
        packs.firstOrNull { it.source == pair.source.code && it.target == pair.target.code }

    companion object {
        fun parse(json: String): BergamotManifest {
            val m = Gson().fromJson(json, BergamotManifest::class.java)
            m.packs.forEach { it.pair }   // fails fast on an unknown code
            return m
        }
    }
}

private fun languageOf(code: String): Language =
    Language.entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("unknown language code $code")
