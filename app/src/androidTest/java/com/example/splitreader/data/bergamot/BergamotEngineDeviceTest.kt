package com.example.splitreader.data.bergamot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitreader.data.translator.BergamotTranslationProvider
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.translator.ModelPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Needs network (real Mozilla bucket) and the prebuilt library for this ABI. Run on demand:
 * ./gradlew :app:connectedDebugAndroidTest --tests '*BergamotEngineDeviceTest*'
 */
@RunWith(AndroidJUnit4::class)
class BergamotEngineDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manifest = BergamotManifest.parse(context.assets.open("bergamot/manifest.json").bufferedReader().readText())
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val store = BergamotModelStore(File(context.filesDir, "bergamot-test"), manifest, OkHttpPackFetcher(client), Dispatchers.IO)
    private val bridge = JniNativeBridge.loadOrNull { throw it }
    private val engine = BergamotEngine(bridge, Dispatchers.Default)
    private val provider = BergamotTranslationProvider(store, engine, store::packDir)

    private val paragraph = ("It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of " +
        "foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the " +
        "season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had " +
        "nothing before us, we were all going direct to Heaven, we were all going direct the other way. ")

    @Test
    fun downloads_en_ru_and_translates_cyrillic() = runBlocking {
        assertNotNull("library must load on this ABI", bridge)
        store.ensure(ModelPair(Language.ENGLISH, Language.RUSSIAN)).collect()
        val out = provider.translate("Good morning, my friend.", Language.ENGLISH, Language.RUSSIAN)
        assertTrue(out, out.any { it in 'А'..'я' })
    }

    @Test
    fun pivot_ru_de_produces_german() = runBlocking {
        val out = provider.translate("Доброе утро, мой друг.", Language.RUSSIAN, Language.GERMAN)
        assertTrue(out, out.contains("Morgen", ignoreCase = true) || out.contains("Freund", ignoreCase = true))
    }

    @Test
    fun warm_latency_for_100_words_is_under_300ms() = runBlocking {
        provider.translate(paragraph, Language.ENGLISH, Language.RUSSIAN)   // warm-up (load + first run)
        val times = (1..5).map {
            val t0 = System.nanoTime(); provider.translate(paragraph + it, Language.ENGLISH, Language.RUSSIAN); (System.nanoTime() - t0) / 1_000_000
        }
        val median = times.sorted()[2]
        assertTrue("median ${median}ms, all=$times", median <= 300)
    }
}
