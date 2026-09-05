package com.example.splitreader.domain.translator

import com.example.splitreader.domain.model.Language
import kotlinx.coroutines.flow.Flow

/** One direction of an on-device translation model. Bergamot packs always have English on one side. */
data class ModelPair(val source: Language, val target: Language) {
    init { require(source == Language.ENGLISH || target == Language.ENGLISH) { "one side must be English" } }
    val id: String get() = "${source.code}-${target.code}"
}

data class InstalledPack(val pair: ModelPair, val bytes: Long)

/** Capability port: on-device model packs for the Offline HQ provider. Domain knows no URLs or files. */
interface OfflineModelStore {
    fun isInstalled(pair: ModelPair): Boolean
    /** Downloads if needed. Emits progress 0f..1f and completes when the pack is ready. */
    fun ensure(pair: ModelPair): Flow<Float>
    suspend fun delete(pair: ModelPair)
    fun installed(): Flow<List<InstalledPack>>
}
