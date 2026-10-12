package com.example.splitreader.fakes

import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.translator.InstalledPack
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Holds [packs] in memory; nothing downloads. */
internal class FakeOfflineModelStore : OfflineModelStore {
    val packs = MutableStateFlow<List<InstalledPack>>(emptyList())
    override fun isInstalled(pair: ModelPair) = packs.value.any { it.pair == pair }
    override fun ensure(pair: ModelPair): Flow<Float> = emptyFlow()
    override suspend fun delete(pair: ModelPair) {
        packs.value = packs.value.filterNot { it.pair == pair }
    }
    override fun installed(): Flow<List<InstalledPack>> = packs
}

internal class FakeEntitlementRepository : EntitlementRepository {
    override val isPremium = MutableStateFlow(false)
    override fun setPremium(premium: Boolean) {
        isPremium.value = premium
    }
}
