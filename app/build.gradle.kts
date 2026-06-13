plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.splitreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.mirrolit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.langid)

    implementation(libs.jsoup)
    implementation(libs.gson)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.coroutines.test)
}

// ── QA test fixtures ──────────────────────────────────────────────────────────
// The copyright-protected real books in qa_book/ are gitignored. This task copies them
// into the androidTest assets under stable ASCII names so instrumentation tests can parse
// the actual files. Missing qa_book/ is tolerated (the dir is simply empty and the
// real-file test cases skip via Assume). Only the public-domain Gutenberg EPUB fixture
// is committed (in app/src/androidTest/assets/qa-pd/).
val qaFixtureMap = mapOf(
    "Академик Лидер.fb2" to "academic_leader.fb2",
    "Агриппа (Книга мертвых).fb2" to "agrippa.fb2",
    "1. Зов Ктулху.fb2" to "cthulhu.fb2",
    "02. Джонни Мнемоник (пер. Переводчик неизвестен) .fb2" to "johnny_mnemonic.fb2",
    "Byekker_R._Vtoroyiapokalipsis._Knyaz_Pustotyi_Kniga_Tret.fb2" to "bekker.fb2",
    "Dembski-Bouden_Aaron_[Poveliteli_nochi#1]_Lovets_dush.mobi" to "soul_hunter.mobi",
    "Kouell_Kressida_[Kak_priruchit_drakona#1]_Kak_priruchit_drakona.mobi" to "how_to_train_dragon.mobi",
)
// Staged layout: <buildDir>/generated/qaAssets/root/qa/<ascii> — the "root" dir is registered
// as an androidTest assets srcDir, so tests read the files at asset path "qa/<ascii>".
val qaStagedRoot = layout.buildDirectory.dir("generated/qaAssets/root")
val stageQaFixtures = tasks.register<Copy>("stageQaFixtures") {
    val srcDir = rootProject.layout.projectDirectory.dir("qa_book")
    into(qaStagedRoot.map { it.dir("qa") })
    qaFixtureMap.forEach { (original, ascii) ->
        from(srcDir.file(original)) { rename { ascii } }
    }
    // No-op cleanly when qa_book/ is absent (e.g. CI without the local books).
    onlyIf { srcDir.asFile.exists() }
}

android.sourceSets.getByName("androidTest").assets.srcDir(qaStagedRoot)
// Stage fixtures before any androidTest asset-merge/packaging task consumes the srcDir.
tasks.matching { it.name.contains("AndroidTest") && it.name.contains("Assets") }
    .configureEach { dependsOn(stageQaFixtures) }
