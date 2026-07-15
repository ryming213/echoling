# =============================================================================
# Echo Ling ProGuard / R8 rules
# =============================================================================
#
# R8 (the default minifier since AGP 7.x) reads this file in addition to
# the AGP defaults from proguard-android-optimize.txt. Rules here either
# KEEP a class/member that R8 would otherwise strip, or rename without
# removing (used for native interop where the .so file looks up the
# Java-side symbol by its original name).
#
# After every R8 change: ./gradlew assembleRelease → install on device
# → run through the full practice flow (record / transcribe / import
# course / play video). If anything ClassNotFoundException's, the rule
# you need is below.
#
# Reference for the AGP defaults:
#   https://developer.android.com/build/shrink-code#keep-code
# =============================================================================


# -----------------------------------------------------------------------------
# Vosk offline STT — org.vosk.Model / Recognizer call into native code
# via JNI. JNI binds Java methods to C functions by their DECLARED Java
# name (e.g. nativeStart()), not the obfuscated one. Stripping these
# classes or renaming their methods = "Method not found" inside libvosk.so
# at the first transcription call.
# -----------------------------------------------------------------------------
-keep class org.vosk.** { *; }
-keepclassmembers class org.vosk.** {
    native <methods>;
}


# -----------------------------------------------------------------------------
# JNA (Java Native Access) — vosk-android uses JNA to load libvosk.so
# at runtime via the Native class. JNA's own classes (Native, Library,
# Function, Structure, etc.) do reflection on the user's @FieldOrder /
# Structure subclasses to marshal data across the JNI boundary. R8 must
# not rename or strip JNA's own classes or any class that extends them.
#
# libjnidispatch.so (the JNA JNI shim) is loaded by name lookup, so the
# jna.Native class itself must survive both stripping AND renaming.
# -----------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.Native { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    public <fields>;
    public <init>(...);
}
-keep @com.sun.jna.Structure.FieldOrder class * { *; }


# -----------------------------------------------------------------------------
# Gson — DictionaryRepositoryImpl deserializes the bundled vocab JSON
# (assets/vocab_*.json) into nested mirror classes then into DictEntry.
# Gson reads field names via reflection (or @SerializedName); both must
# survive.
#
# Two failure modes if these rules are missing:
#   (a) R8 renames a field → Gson reads the JSON key, doesn't find a
#       matching Java field, silently sets the @SerializedName'd field
#       to null. Symptom: "no translation / no example sentence" for
#       every word.
#   (b) R8 strips a nested mirror class because it's "only used by
#       Gson" → Gson throws "Unable to invoke no-args constructor for
#       NestedWordContent". Crashes on first dictionary lookup.
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class com.echoling.app.data.repository.DictionaryRepositoryImpl$* { *; }
-keep class com.echoling.app.data.repository.SrtTranslationMerger$* { *; }

# Any class that uses @SerializedName must keep its field names.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}

# Generic-type signature preservation — Gson needs to know List<X> /
# Map<K, V> generics at runtime to instantiate the right inner types.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken


# -----------------------------------------------------------------------------
# Hilt / Dagger — generated _HiltModules / Hilt_* classes are loaded by
# the Hilt runtime via reflection on the @AndroidEntryPoint /
# @HiltAndroidApp classes. Dagger ships consumer rules that mostly cover
# this, but the @InstallIn entry points (the @Module classes themselves)
# must keep their fully-qualified names so Hilt can resolve them.
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }


# -----------------------------------------------------------------------------
# Room — generated _Impl classes (e.g. SentenceDao_Impl) and the @Entity
# / @Dao classes themselves must keep their names. Room ships consumer
# rules but the @Database class is referenced by name from
# DatabaseModule, so it must survive.
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**


# -----------------------------------------------------------------------------
# Media3 / ExoPlayer — uses ServiceLoader to discover audio renderers /
# extractors at runtime. Renaming the renderer classes or stripping the
# META-INF/services files breaks both audio and (for MKV) video.
# -----------------------------------------------------------------------------
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.muxer.** { *; }
-keep class androidx.media3.container.** { *; }
-keep class androidx.media3.text.** { *; }
-keep class androidx.media3.extractor.text.** { *; }

# Service-loader config files
-keep class * implements androidx.media3.common.util.UnstableApi
-keep,allowobfuscation @interface androidx.media3.common.util.UnstableApi
-keep class * implements androidx.annotation.OptIn { *; }
-keep @androidx.annotation.OptIn class * { *; }


# -----------------------------------------------------------------------------
# Kotlinx Coroutines — uses reflection on DebugProbes / coroutine
# internal classes when -Xjvm-default=all-compatibility is enabled or
# when stack-trace recovery is needed. Coroutines ships its own consumer
# rules but a few internals need pinning.
# -----------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.flow.**inlined**
-dontwarn kotlinx.coroutines.debug.**


# -----------------------------------------------------------------------------
# Jetpack Compose — composables are rewritten by the Compose compiler
# and their generated code uses several internal classes. The Compose
# compiler plugin's consumer rules cover most of this; we add the
# @Composable annotation retention so stack traces remain readable.
# -----------------------------------------------------------------------------
-keep,allowobfuscation @interface androidx.compose.runtime.Composable
-keep,allowobfuscation,allowshrinking @interface androidx.compose.runtime.Stable


# -----------------------------------------------------------------------------
# Subtitle parsers — SrtParser / LrcParser / AssParser read timestamp
# text from JSON-able strings. The Subtitle data class has a public
# constructor; nothing reflective, but pin the package to keep stack
# traces (and any future Gson-based serialization) useful.
# -----------------------------------------------------------------------------
-keep class com.echoling.app.player.subtitle.** { *; }


# -----------------------------------------------------------------------------
# General Android — keep Activities, Application, Service, BroadcastReceiver
# subclasses by virtue of being referenced from AndroidManifest.xml.
# AGP's defaults already do this, but make it explicit so a future
# minifier config swap doesn't lose the contract.
# -----------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.startup.Initializer

# Keep R class fields (used for resource lookup by name in places)
-keepclassmembers class **.R$* {
    public static <fields>;
}


# -----------------------------------------------------------------------------
# Kotlin metadata — needed for kotlin-reflect-style introspection. We
# don't use kotlin-reflect today, but the KotlinPoet / serialization
# tooling does, and the metadata is small enough to keep.
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**


# -----------------------------------------------------------------------------
# Silence warnings for optional/transitive deps
# -----------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# JNA's Native class has helpers for desktop-AWT interop
# (Native.getComponentID / getWindowID). We don't ship AWT on Android,
# but the .so file still references these — silence the warnings so
# R8 doesn't fail the build. Auto-generated by R8 into
# build/outputs/mapping/release/missing_rules.txt.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
