# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# ── Crashlytics: keep line numbers so deobfuscated stack traces are readable ───
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson / Retrofit DTOs ──────────────────────────────────────────────────────
# The online translators (DeepL, Azure, Google Cloud, LibreTranslate) serialize
# request/response data classes through Gson (via Retrofit's GsonConverterFactory).
# Gson maps JSON by reflection, so these model classes and their fields must survive
# R8 renaming. They carry @SerializedName, but the annotation only helps if the
# annotated members and the annotation metadata are kept.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Translator request/response models (com.example.splitreader.data.translator.api.*Api.kt).
-keep class com.example.splitreader.data.translator.api.** { *; }

# ── Retrofit ──────────────────────────────────────────────────────────────────
# Retrofit and OkHttp ship consumer rules, but suspend-fun service methods rely on
# generic Signature metadata to resolve their return types — keep it for our APIs.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation