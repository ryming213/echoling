import java.util.Properties

// Tiny 4-tuple used by the 16KB realignment hook below. Gradle's
// build-script Kotlin DSL doesn't have a built-in Quadruple type and
// pulling in kotlin-stdlib just for this would be a heavy dependency
// for a one-line use.
data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.echoling.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.echoling.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing — reads credentials from `keystore/keystore.properties`
    // (which is in .gitignore; the file is generated locally from
    // `keystore/keystore.properties.example`). If the file is missing
    // (CI without secrets, fresh clone) the release build still runs
    // but emits an unsigned APK that can't be installed — that's the
    // correct failure mode.
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore/keystore.properties")
            if (propsFile.exists()) {
                val props = Properties()
                propsFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // (2026-07-04) R8 minification + resource shrinking ON for
            // release. Required for the 国内应用商店 review:
            //   - 360 / 腾讯 / 梆梆 加固 services refuse APKs > 200 MB
            //     (Echo Ling is ~150 MB today, mostly from the bundled
            //     Vosk model — R8 typically cuts the dex by 40-60% but
            //     the asset stays the same)
            //   - Some stores do a quick "is this APK minified?" check
            //     and ask for a justification if not
            //   - Smaller APK = faster install, especially on low-end
            //     devices common in 国内低端机型
            //
            // isShrinkResources requires isMinifyEnabled. The
            // 16KB-alignment hooks in this file (patchNativeLibsFor16KB
            // + repackApk16kb) are AGP-version-agnostic and run on
            // packageRelease too — R8 doesn't conflict with them.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp — REMOVED 2026-07-04 along with the
    // alphacephei.com model-download fallback in
    // [com.echoling.app.speech.ModelManager]. The Vosk model
    // is now bundled in `assets/models/vosk-model-small-en-us-0.15/`
    // (~68 MB) and copied to `filesDir/models/` on first launch
    // from APK assets — no network call anywhere in the app, which
    // lets us drop `android.permission.INTERNET` from the manifest
    // (required for the 国内应用商店 "用不到的网络权限" review).
    // Re-add this dependency + the corresponding ModelManager
    // download path if you ever switch back to a lean APK that
    // ships the model separately.

    // Vosk offline STT — used by Testing page to transcribe the
    // user's recorded audio file. Recording is done separately via
    // WavRecorder (AudioRecord) so we can keep a real .wav file for
    // playback while still feeding PCM to Vosk for transcription.
    // The Vosk AAR bundles libvosk.so for arm64-v8a, armeabi-v7a,
    // x86, x86_64. The acoustic model is bundled in
    // assets/models/vosk-model-small-en-us-0.15/ and copied to
    // filesDir/models/ on first launch by ModelManager.
    //
    // Vosk's org.vosk.LibVosk class loads its native code through
    // JNA. The vosk-android:0.3.45 AAR does NOT bundle JNA — without
    // an explicit dependency the runtime fails with:
    //   UnsatisfiedLinkError: Native library (libjnidispatch.so) not found
    // and every transcription silently returns empty text.
    //
    // Version matching: JNA's Native.<clinit> compares the JNI
    // protocol version baked into the loaded libjnidispatch.so
    // against the version the bundled Java classes were compiled
    // against. jna:5.18.1's .so provides protocol 7.0.4, but
    // vosk-android's transitive jna:4.4.0 jar expects protocol 5.1.0
    // — mismatch → "There is an incompatible JNA native library
    // installed on this system" error.
    //
    // Solution: replace the transitive jna:4.4.0 with the matching
    // jna:5.18.1 jar. The jna-bridge Android library module ships
    // the .so files at lib/<abi>/ (extracted from jna-5.18.1.aar).
    // exclude(group = "net.java.dev.jna") drops vosk-android's
    // pinned jna:4.4.0, so the only jna on the classpath is the
    // explicitly-declared jna:5.18.1 — both jar classes AND .so
    // binaries come from the same version.
    implementation(project(":jna-bridge"))
    implementation("com.alphacephei:vosk-android:0.3.45") {
        exclude(group = "net.java.dev.jna")
    }
    implementation("net.java.dev.jna:jna:5.18.1")

    // (2026-07-16) ffmpeg-kit AAR vendored locally because Maven Central
    // dropped the com.arthenica group after ffmpeg-kit was retired in
    // early 2025. The .aar file is committed to app/libs/ (~35 MB — the
    // min-gpl flavor ships 10 native libs: libavcodec, libavdevice,
    // libavfilter, libavformat, libavutil, libswresample, libswscale,
    // libffmpegkit, libffmpegkit_abidetect, libc++_shared × 4 ABIs) and
    // referenced via files(...) — no POM metadata, but the AAR is
    // self-contained (no transitive Maven deps). Native .so files are
    // 16 KB-aligned by the existing patchNativeLibsFor16KB hook
    // (CLAUDE.md §12.33).
    implementation(files("libs/ffmpeg-kit-min-gpl-6.0-2.aar"))

    // WorkManager 2.9.1 — required for AutoTranscriptionWorker (spec §6).
    // HiltWorkerFactory wiring is done in EchoLingApplication.kt (Task 4).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Hilt-Work integration (provides @HiltWorker + HiltWorkerFactory).
    // ksp processor is already on classpath via the Hilt plugin.
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ============================================================================
// 16 KB page-size alignment for native libraries (two-layer fix)
// ============================================================================
//
// Android Studio's APK Analyzer flags libvosk.so / libjnidispatch.so with
// "libraries are not aligned at 16 KB zip boundaries". Required for Android
// 15+ devices with 16 KB page sizes; mandatory for Google Play submissions
// from Nov 1 2025.
//
// Two layers are required:
//   Layer 1 (ELF internal): every PT_LOAD.p_align = 16384 inside each .so.
//     Handled by `patchNativeLibsFor16KB` below (doFirst on packageDebug).
//   Layer 2 (zip external): every .so file data offset % 16384 == 0 inside
//     the APK. Handled by `repackApk16kb` further down (doLast on
//     packageDebug, after AGP zips but before signing).
//
// On Android < 15 devices the bumped p_align is harmless (the loader
// trusts the hint and falls through to normal 4 KB mapping) and the zip
// entry padding bytes are ignored — no regression on older devices.
//
// We can't use AGP's built-in zipalign for Layer 2: tested with
// build-tools 34.0.0 on Windows, `zipalign -f -p -v 16` reports
// "Verification successful" but does NOT actually move any .so entry to
// a 16 KB boundary. The output APK's file data offsets are unchanged.
// Our own Python implementation (scripts/repack_apk_16kb.py) asserts the
// alignment before writing, so we know the bytes are correct.

// ---------------------------------------------------------------------------
// Layer 1: patch ELF PT_LOAD.p_align on disk before AGP zips
// ---------------------------------------------------------------------------
//
// doFirst on packageDebug runs after mergeDebugNativeLibs +
// stripDebugDebugSymbols but before the actual APK packaging reads the
// .so files. AGP detects the mtime change and re-zips with the patched
// content.
//
// IMPORTANT: AGP 8.x's packageDebug reads from `stripped_native_libs/...`
// (NOT `merged_native_libs/`). `stripDebugDebugSymbols` copies the merged
// .so files into the stripped dir and strips their debug symbols. So we
// must patch the STRIPPED output, not the merged output — otherwise strip
// copies the unpatched merged .so and overwrites our changes.
//
// The .so files are still 4 KB-aligned on disk (a strict subset of
// 16 KB alignment), so the larger p_align hint is safe — the ELF loader
// just trusts "this segment is at least 16 KB aligned" and skips its
// dynamic mprotect-based realignment.
afterEvaluate {
    listOf(
        "packageDebug" to "debug",
        "packageRelease" to "release"
    ).forEach { (pkgTask, variant) ->
        tasks.findByName(pkgTask)?.doFirst {
            // AGP 8.2.x path: stripped_native_libs/<variant>/out/lib/<abi>/*.so
            // AGP 8.5.x path: stripped_native_libs/<variant>/strip<Variant>DebugSymbols/out/lib/<abi>/*.so
            // We probe both so the patch works regardless of AGP version.
            val candidates = listOf(
                "$buildDir/intermediates/stripped_native_libs/$variant/strip${variant.replaceFirstChar { it.uppercase() }}DebugSymbols/out/lib",
                "$buildDir/intermediates/stripped_native_libs/$variant/out/lib"
            )
            val outDir = candidates.map { file(it) }.firstOrNull { it.exists() }
            if (outDir == null) {
                logger.warn("patchNativeLibsFor16KB: none of $candidates exist — skipping")
                return@doFirst
            }
            val soFiles = fileTree(outDir) { include("**/*.so") }
            if (soFiles.isEmpty) {
                logger.warn("patchNativeLibsFor16KB: no .so files under $outDir — skipping")
                return@doFirst
            }
            val script = file("$rootDir/scripts/patch_native_libs_16kb.py")
            if (!script.exists()) {
                throw GradleException("patch_native_libs_16kb.py not found at $script")
            }
            soFiles.forEach { so ->
                logger.lifecycle("patchNativeLibsFor16KB: patching ${so.name}")
                exec {
                    commandLine("python", script.absolutePath, so.absolutePath)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layer 2: realign .so zip entries after AGP packages the APK, then
// re-sign with the debug keystore (signing already happened inside
// packageDebug's action — our byte changes invalidated it). Also
// 4-byte-align resources.arsc (Android R+ requirement, otherwise
// install fails with INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED).
// ---------------------------------------------------------------------------
//
// IMPORTANT: in AGP 8.x's debug pipeline, signing happens INSIDE
// packageDebug's own action (not as a separate downstream task). Our
// doLast hook therefore runs AFTER signing has already signed the
// unaligned bytes; we MUST re-sign the realigned APK with the same
// debug keystore AGP used, or installation fails with
// `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.
//
// AGP writes TWO APKs: `intermediates/apk/<variant>/app-<variant>.apk`
// (the build artifact, ~85 MB) and `outputs/apk/<variant>/app-<variant>.apk`
// (a transformed copy that AGP produces for IDE visibility, slightly
// larger because of additional resources). The user's Android Studio
// install dialog reports installing the INTERMEDIATE one — that's the
// one we MUST patch or the dialog fails. So we patch both.
//
// Standard debug keystore credentials (what AGP itself uses):
//   path:   ~/.android/debug.keystore (or %USERPROFILE%\.android\debug.keystore)
//   alias:  androiddebugkey
//   storePass / keyPass: android
afterEvaluate {
    listOf(
        "packageDebug" to "debug",
        "packageRelease" to "release"
    ).forEach { (pkgTask, variant) ->
        tasks.findByName(pkgTask)?.doLast {
            // Find ALL APKs AGP produced. AGP's packageDebug is an
            // "incognito" task (no declared outputs), so we glob the
            // build dir instead of using task.outputs.files.
            val apks = fileTree("$buildDir") {
                include("intermediates/apk/$variant/*.apk")
                include("outputs/apk/$variant/*.apk")
            }.files.filter { it.isFile }
            if (apks.isEmpty()) {
                logger.warn("repackApk16kb: no APK found under intermediates/apk/$variant or outputs/apk/$variant — skipping")
                return@doLast
            }
            val script = file("$rootDir/scripts/repack_apk_16kb.py")
            if (!script.exists()) {
                throw GradleException("repack_apk_16kb.py not found at $script")
            }

            // Locate keystore + signing credentials.
            //
            // Two cases:
            //   - variant == "debug"  → use the standard AGP debug
            //     keystore at $USER_HOME/.android/debug.keystore with
            //     the well-known androiddebugkey / android / android
            //     triple.
            //   - variant == "release" → use the release keystore from
            //     keystore/keystore.properties (which is .gitignored —
            //     the property file holds the password, not the .keystore
            //     file itself). Same as the signingConfig above.
            //
            // Picking the wrong one is a SILENT FAILURE: a release APK
            // re-signed with the debug keystore will still install (it
            // has a valid v2 signature), but it will be a different
            // signing identity from your previous releases — AppGallery
            // and the other 国内 stores reject that with "package
            // already exists with a different signature". 1Password /
            // encrypted USB backup of keystore/echoling-release.keystore
            // is mandatory.
            val (keystoreFile, ksPass, keyPass, keyAlias) = if (variant == "release") {
                val propsFile = rootProject.file("keystore/keystore.properties")
                if (!propsFile.exists()) {
                    logger.warn("repackApk16kb: $variant variant but no keystore/keystore.properties — APK will be unsigned after repack")
                    Tuple4(null as java.io.File?, null, null, null)
                } else {
                    val props = Properties()
                    propsFile.inputStream().use { props.load(it) }
                    Tuple4(
                        rootProject.file(props.getProperty("storeFile")),
                        props.getProperty("storePassword"),
                        props.getProperty("keyPassword"),
                        props.getProperty("keyAlias"),
                    )
                }
            } else {
                Tuple4(
                    file("${System.getProperty("user.home")}/.android/debug.keystore"),
                    "android",
                    "android",
                    "androiddebugkey",
                )
            }
            val hasKeystore = keystoreFile != null && keystoreFile.exists()

            // Locate Android SDK (apksigner lives under <sdk>/build-tools/<ver>/).
            // `local.properties` is in the *project root* (parent of `app/`),
            // so we resolve it from $rootDir.
            val androidHome = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: run {
                    val lp = file("$rootDir/local.properties")
                    if (lp.exists()) {
                        val match = Regex("sdk\\.dir=(.+)").find(lp.readText())
                        match?.groupValues?.getOrNull(1)?.trim()
                    } else null
                }

            // Find apksigner.bat once (same for all APKs).
            val apksignerBat = if (androidHome != null && hasKeystore) {
                file("$androidHome/build-tools").listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.mapNotNull { dir ->
                        dir.listFiles()?.firstOrNull {
                            it.name.startsWith("apksigner") && it.name.endsWith(".bat")
                        }
                    }
                    ?.firstOrNull()
            } else null
            if (hasKeystore && apksignerBat == null) {
                logger.warn("repackApk16kb: no apksigner.bat found — re-sign will likely fail")
            }

            apks.forEach { apk ->
                logger.lifecycle("repackApk16kb: realigning ${apk.name} (${apk.parentFile.name})")
                val cmd = mutableListOf("python", script.absolutePath, apk.absolutePath)
                if (hasKeystore) {
                    cmd += "--sign"
                    cmd += "--ks"; cmd += keystoreFile!!.absolutePath
                    cmd += "--ks-pass"; cmd += "pass:$ksPass"
                    cmd += "--key-pass"; cmd += "pass:$keyPass"
                    cmd += "--ks-key-alias"; cmd += keyAlias!!
                    if (apksignerBat != null) {
                        cmd += "--apksigner"; cmd += apksignerBat.parentFile.absolutePath
                    }
                } else {
                    logger.warn("repackApk16kb: no keystore found for $variant variant — APK will be unsigned after repack")
                }
                exec {
                    commandLine(cmd)
                }
                // Touch mtime so anything downstream that depends on the APK
                // mtime re-runs.
                apk.setLastModified(System.currentTimeMillis())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cleanup: delete stale v4 .idsig files (both intermediate AND outputs)
// ---------------------------------------------------------------------------
//
// AGP's `createDebugApkListingFileRedirect` (and similar for release)
// generates a v4 `.idsig` file alongside the final APK in both
// `outputs/apk/<variant>/` and `intermediates/apk/<variant>/`. The
// .idsig is a signature that AGP signed BEFORE our repack+resign
// flow modified the APK bytes — it now points to stale content. If
// Android Studio reads the stale .idsig during install, it fails
// with INSTALL_PARSE_FAILED_NO_CERTIFICATES.
//
// Delete the .idsig files as the very last step of the build.
afterEvaluate {
    listOf(
        "createDebugApkListingFileRedirect",
        "createReleaseApkListingFileRedirect",
    ).forEach { taskName ->
        tasks.findByName(taskName)?.doLast {
            // Both directories get .idsig files; clean them all.
            listOf(
                "$buildDir/outputs/apk",
                "$buildDir/intermediates/apk",
            ).forEach { dir ->
                fileTree(dir) { include("**/*.idsig") }.forEach { idsig ->
                    logger.lifecycle("cleanStaleIdsig: deleting ${idsig.name}")
                    idsig.delete()
                }
            }
        }
    }
}
