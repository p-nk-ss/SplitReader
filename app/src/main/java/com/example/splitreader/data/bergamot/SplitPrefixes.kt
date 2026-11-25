package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import java.io.File
import java.io.InputStream

/**
 * Lists of protected prefixes ("Mrs", "Dr", "St", …) for the engine's sentence splitter. Without one
 * the engine ends a sentence at every abbreviation's full stop ("миссис. Лонг говорит…", translation
 * eval 2026-11-25). The lists are Moses' nonbreaking_prefixes (LGPL 2.1, shipped as separate data
 * files in assets `bergamot/ssplit/`, read at run time); the engine takes them by file path only, so
 * each is copied out once. Languages Moses has no list for get none — the engine's plain splitter.
 */
class SplitPrefixes(private val dir: File, private val openAsset: (String) -> InputStream?) {
    @Synchronized
    fun fileFor(language: Language): File? {
        val name = "nonbreaking_prefix.${language.code}"
        val file = File(dir, name)
        if (file.length() > 0) return file
        val input = openAsset("bergamot/ssplit/$name") ?: return null
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        return file
    }
}
