package com.example.splitreader.eval

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitreader.data.bergamot.BergamotEngine
import com.example.splitreader.data.bergamot.BergamotManifest
import com.example.splitreader.data.bergamot.BergamotModelStore
import com.example.splitreader.data.bergamot.JniNativeBridge
import com.example.splitreader.data.bergamot.OkHttpPackFetcher
import com.example.splitreader.data.bergamot.SplitPrefixes
import com.example.splitreader.data.translator.BergamotTranslationProvider
import com.example.splitreader.data.translator.MLKitTranslationProvider
import com.example.splitreader.data.translator.QuickTranslateProvider
import com.example.splitreader.data.translator.api.QuickTranslateApi
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.Sentences
import com.example.splitreader.domain.translator.TranslationProviderApi
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Translation-quality evaluation harness — NOT a test. Skipped unless run with
 * `-e translationEval true`. Translates the corpus in androidTest assets `eval/texts.json`
 * with the free engines the way the app does (on-device engines split CJK/long paragraphs into
 * sentences, as TranslationRepositoryImpl does; no cache) and appends one JSON line per paragraph
 * to `<external files>/eval/<engine>.jsonl`. Re-running resumes: done lines are skipped.
 *
 * adb shell am instrument -w -e translationEval true -e engines bergamot,mlkit,quick \
 *   -e class com.example.splitreader.eval.TranslationQualityHarness \
 *   io.mirrolit.app.test/androidx.test.runner.AndroidJUnitRunner
 * Quick Translate: one request at a time, a pause between them; on HTTP 429 it stops for good.
 */
@RunWith(AndroidJUnit4::class)
class TranslationQualityHarness {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext
    private val prefixes = SplitPrefixes(File(context.filesDir, "bergamot-ssplit")) { path ->
        try { context.assets.open(path) } catch (_: java.io.FileNotFoundException) { null }
    }

    @Test
    fun translateCorpus() = runBlocking {
        assumeTrue("evaluation harness: run with -e translationEval true", args.getString("translationEval") == "true")
        val corpus = JsonParser.parseString(
            instrumentation.context.assets.open("eval/texts.json").bufferedReader().readText(),
        ).asJsonObject
        val engines = (args.getString("engines") ?: "bergamot,mlkit,quick").split(',').map { it.trim() }
        val only = args.getString("dirs")?.split(',')?.map { it.trim() }?.toSet()
        val directions = DIRECTIONS.filter { only == null || "${it.first}-${it.second}" in only }
        val outDir = File(context.getExternalFilesDir(null), "eval").apply { mkdirs() }
        // Experiments: `-e tag x` writes <engine>-x.jsonl; `-e cut N` sends on-device engines every
        // paragraph longer than N chars as sentence pieces of at most N chars (Sentences.split).
        val tag = args.getString("tag")?.let { "-$it" } ?: ""
        val cut = args.getString("cut")?.toInt()

        for (name in engines) {
            val provider = provider(name)
            val out = File(outDir, "$name$tag.jsonl")
            val done = if (out.exists()) out.readLines().mapNotNull { line ->
                runCatching { JsonParser.parseString(line).asJsonObject.let { it["dir"].asString + "|" + it["id"].asString } }.getOrNull()
            }.toSet() else emptySet()
            var stopped = false
            for ((source, target) in directions) {
                if (stopped) break
                val src = Language.fromCode(source)
                val tgt = Language.fromCode(target)
                val dir = "$source-$target"
                val paragraphs = corpus[source]?.asJsonArray ?: continue
                if (!provider.supports(src, tgt)) {
                    out.appendText(line(dir, "-", "", null, "unsupported", 0) + "\n"); continue
                }
                val prepareError = runCatching { provider.prepare(src, tgt).collect { } }.exceptionOrNull()
                for (p in paragraphs) {
                    val o = p.asJsonObject
                    val id = o["id"].asString
                    if ("$dir|$id" in done) continue
                    val text = o["text"].asString
                    val t0 = System.nanoTime()
                    val result = if (prepareError != null) Result.failure(prepareError) else runCatching {
                        val abbr = prefixes.forLanguage(src)
                        if (name != "quick" && cut != null && text.length > cut) {
                            Sentences.join(Sentences.split(text, src, cut, abbr).map { provider.translate(it, src, tgt) }, tgt)
                        } else if (name != "quick" && Sentences.needsSplitting(text, src)) {
                            // As TranslationRepositoryImpl does for the on-device engines.
                            Sentences.join(Sentences.split(text, src, abbreviations = abbr).map { provider.translate(it, src, tgt) }, tgt)
                        } else {
                            provider.translate(text, src, tgt)
                        }
                    }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    val err = result.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message }
                    out.appendText(line(dir, id, text, result.getOrNull(), err, ms) + "\n")
                    if (name == "quick") {
                        if ((result.exceptionOrNull() as? HttpException)?.code() == 429) { stopped = true; break }
                        delay(QUICK_PAUSE_MS)
                    }
                }
            }
        }
    }

    private fun line(dir: String, id: String, text: String, translation: String?, error: String?, ms: Long): String =
        JsonObject().apply {
            addProperty("dir", dir); addProperty("id", id); addProperty("source", text)
            addProperty("translation", translation); addProperty("error", error); addProperty("ms", ms)
        }.toString()

    private fun provider(name: String): TranslationProviderApi {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        return when (name) {
            "bergamot" -> {
                val manifest = BergamotManifest.parse(context.assets.open("bergamot/manifest.json").bufferedReader().readText())
                val store = BergamotModelStore(File(context.filesDir, "bergamot-eval"), manifest, OkHttpPackFetcher(client), Dispatchers.IO)
                val engine = BergamotEngine(JniNativeBridge.loadOrNull { throw it }, Dispatchers.Default)
                BergamotTranslationProvider(store, engine, manifest, prefixFor = prefixes::fileFor, packDir = store::packDir)
            }
            "mlkit" -> MLKitTranslationProvider()
            "quick" -> QuickTranslateProvider(
                Retrofit.Builder().baseUrl("https://translate.googleapis.com/").client(client)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build().create(QuickTranslateApi::class.java),
            )
            else -> error("unknown engine $name")
        }
    }

    companion object {
        private const val QUICK_PAUSE_MS = 4_000L
        private val OTHERS = listOf("uk", "de", "fr", "es", "it", "pt", "nl", "pl", "zh", "ja", "ko", "ar", "hi", "tr", "sv", "cs", "ru")
        val DIRECTIONS: List<Pair<String, String>> =
            OTHERS.map { "en" to it } + OTHERS.map { it to "en" } + listOf(
                "ja" to "ru", "zh" to "ru", "ko" to "ru", "de" to "ru", "fr" to "uk", "es" to "ru",
                "ar" to "ru", "hi" to "ru", "tr" to "ru", "it" to "ru", "pl" to "uk", "sv" to "ru",
            )
    }
}
