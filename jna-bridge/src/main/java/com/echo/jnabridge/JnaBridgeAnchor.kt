package com.echo.jnabridge

/**
 * Anchor class so AGP treats this library module as a real Android library
 * and merges both its compiled classes AND its `resources/` + `jniLibs/`
 * into the consuming app's APK.
 *
 * JNA on Android looks for libjnidispatch.so at the classpath resource
 * path `com/sun/jna/android-<arch>/libjnidispatch.so` via
 * [ClassLoader.getResource]. AGP only packages `src/main/resources/`
 * from a library module when the module has at least one compiled class
 * (otherwise the dex merger drops the whole module as empty). This
 * class is the minimal anchor that forces AGP to keep the module alive.
 *
 * Do not remove this class — the JNA native loading chain (Vosk ->
 * LibVosk -> JNA Native.<clinit> -> ClassLoader.getResource) depends on
 * these .so files being on the classpath at the right path. Without
 * this anchor, AGP will not package the resources and Vosk will fail
 * with `UnsatisfiedLinkError: Native library
 * (com/sun/jna/android-aarch64/libjnidispatch.so) not found in
 * resource path`.
 *
 * See: `memory/vosk-jna-android-native-packaging.md`
 */
internal object JnaBridgeAnchor {
    @Suppress("unused")
    const val VERSION: String = "1.0.0"
}