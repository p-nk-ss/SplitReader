package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.ModelPair

/** Models to run, in order. Empty = nothing to translate; two = pivot through English. */
fun routeFor(source: Language, target: Language): List<ModelPair> = when {
    source == target -> emptyList()
    source == Language.ENGLISH || target == Language.ENGLISH -> listOf(ModelPair(source, target))
    else -> listOf(ModelPair(source, Language.ENGLISH), ModelPair(Language.ENGLISH, target))
}
