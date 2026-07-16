package com.echoling.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EchoLingApplication : Application(), Configuration.Provider {

    // (2026-07-16) Inject HiltWorkerFactory so WorkManager can
    // construct @HiltWorker classes (AutoTranscriptionWorker).
    // The Configuration.Provider interface below lets WorkManager
    // discover this factory instead of falling back to the default
    // ReflectiveWorkerFactory — which can't see @AssistedInject
    // constructors and would throw at first .enqueue().
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // JNA dispatch library probe.
        //
        // Background: vosk-android:0.3.45 depends transitively on
        // jna:4.4.0, which has no Android .so files. We:
        //   - exclude jna:4.4.0 from vosk-android (version mismatch
        //     with the .so we ship)
        //   - declare jna:5.18.1 jar explicitly
        //   - ship libjnidispatch.so (5.18.1) at lib/<abi>/ via
        //     :jna-bridge
        //
        // System.loadLibrary("jnidispatch") finds the bundled
        // libjnidispatch.so inside the APK and loads it. JNA's
        // Native.<clinit> later confirms the loaded lib's JNI
        // protocol version matches the bundled Java classes (both
        // 5.18.1 now) and proceeds.
        try {
            System.loadLibrary("jnidispatch")
            Log.i("JnaProbe", "loadLibrary('jnidispatch') OK")
        } catch (e: Throwable) {
            Log.e("JnaProbe", "loadLibrary('jnidispatch') FAILED", e)
        }
    }
}