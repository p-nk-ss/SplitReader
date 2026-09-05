package com.example.splitreader.data.bergamot

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.ModelPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped manifest is the only thing between a Language and a download URL; it must be complete and sane. */
class BergamotManifestTest {
    private val manifest = BergamotManifest.parse(File("src/main/assets/bergamot/manifest.json").readText())

    @Test
    fun `every non-English language has both directions`() {
        val nonEnglish = Language.entries - Language.ENGLISH
        assertEquals(nonEnglish.size * 2, manifest.packs.size)
        for (lang in nonEnglish) {
            assertNotNull("en→${lang.code}", manifest.find(ModelPair(Language.ENGLISH, lang)))
            assertNotNull("${lang.code}→en", manifest.find(ModelPair(lang, Language.ENGLISH)))
        }
    }

    @Test
    fun `model entries carry a 64-hex sha256 and a positive size`() {
        for (p in manifest.packs) {
            assertTrue(p.pair.id, Regex("[0-9a-f]{64}").matches(p.model.sha256 ?: ""))
            assertTrue(p.pair.id, (p.model.size ?: 0) > 0)
        }
    }

    @Test
    fun `paths are relative and baseUrl is https`() {
        assertTrue(manifest.baseUrl.startsWith("https://"))
        for (p in manifest.packs) for (f in listOf(p.model, p.vocab, p.shortlist)) {
            assertTrue(f.path, f.path.startsWith("models/") && f.path.endsWith(".gz"))
        }
    }

    @Test
    fun `parse rejects a manifest with an unknown language code`() {
        val bad = """{"version":1,"baseUrl":"https://x","packs":[{"source":"en","target":"xx","architecture":"tiny",
            "model":{"path":"models/a.gz","size":1,"sha256":"${"a".repeat(64)}"},"vocab":{"path":"models/v.gz"},"shortlist":{"path":"models/s.gz"}}]}"""
        val result = runCatching { BergamotManifest.parse(bad) }
        assertTrue(result.isFailure)
    }
}
