package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.AbbreviationSource
import com.example.splitreader.domain.translator.Abbreviations
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Lists of protected prefixes ("Mrs", "Dr", "St", …) for the engine's sentence splitter. Without one
 * the engine ends a sentence at every abbreviation's full stop ("миссис. Лонг говорит…", translation
 * eval 2026-11-25). The lists are Moses' nonbreaking_prefixes (LGPL 2.1, shipped as separate data
 * files in assets `bergamot/ssplit/`, read at run time); the engine takes them by file path only, so
 * each is copied out once. Languages Moses has no list for get none — the engine's plain splitter.
 */
class SplitPrefixes(private val dir: File, private val openAsset: (String) -> InputStream?) : AbbreviationSource {
    private val parsed = mutableMapOf<Language, Abbreviations>()

    /** The same list, parsed for the app's own splitter ([com.example.splitreader.domain.translator.Sentences]). */
    @Synchronized
    override fun forLanguage(language: Language): Abbreviations = parsed.getOrPut(language) {
        try {
            fileFor(language)?.readText()?.let(Abbreviations::parse)
        } catch (_: IOException) {
            null
        } ?: Abbreviations.NONE
    }

    /**
     * The list's file for the engine, or null when there is none or it could not be copied out
     * (storage full): translation then goes on without the list rather than failing.
     */
    @Synchronized
    fun fileFor(language: Language): File? {
        val name = "nonbreaking_prefix.${language.code}"
        val file = File(dir, name)
        if (file.length() > 0) return file
        val tmp = File(dir, "$name.tmp")
        try {
            val input = openAsset("bergamot/ssplit/$name") ?: return null
            dir.mkdirs()
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (_: IOException) {
            // fall through: no usable file
        } finally {
            tmp.delete()
        }
        return file.takeIf { it.length() > 0 }
    }
}
