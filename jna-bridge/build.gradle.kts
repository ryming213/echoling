plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.echo.jnabridge"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        // Bridge module is intentionally empty from a public-API point of
        // view — it just carries JNA's libjnidispatch.so at the resource
        // path JNA's ClassLoader.getResource lookup expects. We don't
        // depend on any consumer classes either.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // We don't want any of the JNI libs to be stripped — the build is
    // already warning about this in the consumer app. Keep them as-is.
}

dependencies {
    // No runtime dependencies. This module exists solely to ship
    // libjnidispatch.so at the classpath resource path JNA looks up:
    //   com/sun/jna/android-<arch>/libjnidispatch.so
    // plus the same .so at the standard jniLibs/<abi>/ path so
    // System.loadLibrary("jnidispatch") works too.
}