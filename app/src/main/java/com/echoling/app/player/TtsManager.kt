package com.echoling.app.player

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped Text-to-Speech wrapper.
 *
 * Lives in `player/` next to [AudioPlayer] (the ExoPlayer wrapper) — same
 * architectural role: a side-effect service that the ViewModels inject
 * directly without a UseCase layer (CLAUDE.md §5.3 — only Repository
 * calls need UseCases).
 *
 * **Multi-engine fallback** — different devices ship with different TTS
 * engines, and many devices (especially Chinese Xiaomi without Google
 * services) ship with NONE installed. We try a curated list of engine
 * package names in order, taking the first one that binds successfully.
 * If all fail, [isAvailable] stays false and the ViewModels surface a
 * "please install a TTS engine" snackbar instead of silently failing.
 *
 * **Defensive** — every operation on a TTS engine (shutdown, setLanguage,
 * setAudioAttributes, etc.) is wrapped in try/catch because third-party
 * engines (especially Chinese ones like iFlytek) often have quirky
 * implementations that throw on standard API calls. If any single
 * engine throws during init or config, we recover and try the next one
 * rather than crashing the whole app.
 *
 * Init is async (the [TextToSpeech.OnInitListener] callback fires on the
 * binder thread). We expose [isReady] as a StateFlow and **queue any
 * speak() calls that arrive before init completes**, draining the queue
 * on init success.
 *
 * **Critical: `setAudioAttributes` is called first thing on API 21+.**
 * Without it, TTS audio routes to STREAM_MUSIC with default attributes,
 * which on some devices (especially Chinese OEM ROMs) means the audio
 * plays through the ringer/notification stream and is muted by silent
 * mode, or doesn't reach the media volume control at all. With
 * USAGE_MEDIA + CONTENT_TYPE_SPEECH the audio follows the user's media
 * volume — same stream as a song.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Nullable backing field. Cannot be `private val tts = TextToSpeech(...) {
    //     tts.setLanguage(...) }` because the init lambda references `tts`
    // while Kotlin is still initializing it — the compiler (correctly)
    // refuses with "Variable 'tts' must be initialized". Defer assignment
    // to an init block so the property is fully set before any callback
    // can observe it.
    private var tts: TextToSpeech? = null

    // True once at least one engine has successfully bound. False while
    // we're trying engines / after all have failed. ViewModels read this
    // to decide whether to surface an "install a TTS engine" snackbar.
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // True once the current engine is bound and ready to speak. False
    // while we're still trying engines. speak() gates on this.
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Pending speak() calls accumulated before any engine binds. Drained
    // on the first SUCCESS. Synchronized because init callbacks run on a
    // binder thread and speak() is called from the main thread. Capped
    // at [MAX_PENDING] to prevent unbounded growth if no engine ever
    // binds.
    private val pending = mutableListOf<String>()
    private val lock = Any()

    // Main-thread handler for the per-engine watchdog and inter-engine
    // spacing delay.
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Engine candidate list. Tried in order; first one whose
     * OnInitListener returns SUCCESS wins. `null` at the end asks the
     * system for its default engine.
     *
     * **CRITICAL: must be declared BEFORE [init]** — Kotlin
     * initializes properties in source order, and the `init` block
     * runs immediately after the primary constructor body (before
     * any property declared after the init block is assigned). If
     * `engineCandidates` is declared below `init`, it will still be
     * its default null value when [tryNextEngine] reads
     * `engineCandidates.size` → NullPointerException → app crash
     * before the engine loop even starts. The crash was observed on
     * the user's Xiaomi Mi 11 after installing iFlytek: the first
     * time TtsManager was injected (on tapping 高中英语词汇), the
     * `init { tryNextEngine(0) }` ran and threw on
     * `engineCandidates.size` — TtsManager was never usable.
     *
     * **List construction** — the static "known good" candidate list
     * is augmented at init time with any TTS engine the system has
     * actually installed and registered as a `TTS_SERVICE`
     * IntentService (queried via [PackageManager.queryIntentServices]).
     * This catches TTS engines whose package names we didn't
     * hardcode — notably 「讯飞语记」/iFlytek Yuji (`com.iflytek.yujizhushou`),
     * which is a note-taking app that bundles its own TTS engine
     * service but is NOT exposed under the SDK package
     * `com.iflytek.speechsuite`. Without the runtime query, our
     * hardcoded list misses every Chinese vendor's TTS that ships
     * under a "wrapper app" package name (note app, dictionary app,
     * keyboard app, …).
     *
     * Order rationale:
     *  1. Google TTS — best English quality, ubiquitous on GMS devices
     *  2. Xiaomi TTS — pre-installed on global MIUI builds
     *  3. iFlytek — common on CN Xiaomi/Huawei
     *  4. Baidu — alternative CN option
     *  5. Android Pico — old AOSP fallback
     *  6. svox.pico — even older AOSP
     *  7. any TTS service the system knows about via PackageManager
     *     (catches vendor-specific package names we didn't list)
     *  8. null — system default (last resort)
     */
    private val engineCandidates: List<String?> = run {
        val known = listOf(
            "com.google.android.tts",
            "com.xiaomi.tts",
            "com.iflytek.speechsuite",
            "com.baidu.duersdk.tts",
            "com.android.tts",
            "com.svox.pico",
        )
        val discovered = try {
            context.packageManager
                .queryIntentServices(
                    android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                    0,
                )
                .mapNotNull { it.serviceInfo?.packageName }
                .filter { it !in known }
        } catch (e: Throwable) {
            Log.w(TAG, "queryIntentServices for TTS failed — using hardcoded list only", e)
            emptyList()
        }
        Log.i(TAG, "Discovered TTS engines via PackageManager: $discovered")
        known + discovered + null
    }

    init {
        tryNextEngine(0)
    }

    /**
     * Attempt to bind to engineCandidates[index]. On success, configure
     * the engine. On failure (or watchdog timeout, or any exception),
     * try the next candidate. After all candidates exhausted, log +
     * flip isAvailable=false.
     *
     * Every operation is wrapped in try/catch because third-party TTS
     * engines (notably iFlytek on Xiaomi CN devices) have been observed
     * to throw on `shutdown()` when called before init completes, and
     * to throw on `setLanguage()` when the language data isn't fully
     * loaded. Without the defensive wrappers, a single bad engine would
     * crash the app.
     */
    private fun tryNextEngine(index: Int) {
        if (index >= engineCandidates.size) {
            Log.w(TAG, "All ${engineCandidates.size} TTS engine candidates " +
                    "failed to init — no TTS available on this device.")
            _isAvailable.value = false
            _isReady.value = false
            return
        }
        val engine = engineCandidates[index]
        Log.d(TAG, "Trying TTS engine [$index/${engineCandidates.size}]: $engine")

        // Safely shut down the previous instance. Some engines throw
        // if shutdown is called before init completes; catch + log.
        try {
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.w(TAG, "shutdown() threw for previous engine — ignoring", e)
        }
        tts = null

        // Inter-engine spacing: 50ms delay so the previous shutdown
        // has time to dispatch its disconnect message before we try
        // to bind the next engine. Without this, some engines (notably
        // iFlytek) get into a confused state and crash on the next
        // bind attempt.
        mainHandler.postDelayed({
            tryBindEngine(index, engine)
        }, INTER_ENGINE_DELAY_MS)
    }

    /**
     * Actually create the TextToSpeech for [engine]. Separated from
     * [tryNextEngine] so we can apply an inter-engine delay via
     * [mainHandler.postDelayed].
     */
    private fun tryBindEngine(index: Int, engine: String?) {
        try {
            tts = TextToSpeech(context, { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        Log.w(TAG, "Engine [$index] $engine init SUCCESS")
                        onEngineSuccess(engine, index)
                    } else {
                        Log.w(TAG, "Engine [$index] $engine init FAILED, status=$status")
                        // removeCallbacksAndMessages(null) drops ALL
                        // pending messages on the handler — cancels
                        // the per-engine watchdog.
                        mainHandler.removeCallbacksAndMessages(null)
                        tryNextEngine(index + 1)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "onInit callback threw for engine $engine — trying next", e)
                    mainHandler.removeCallbacksAndMessages(null)
                    tryNextEngine(index + 1)
                }
            }, engine)
        } catch (e: Throwable) {
            // Some engines throw synchronously from the constructor
            // (e.g. on devices where the engine package is partially
            // installed but broken). Skip and try next.
            Log.w(TAG, "TextToSpeech() constructor threw for engine $engine — trying next", e)
            mainHandler.removeCallbacksAndMessages(null)
            tryNextEngine(index + 1)
            return
        }

        // Per-engine watchdog. If the callback doesn't fire within
        // [ENGINE_BIND_TIMEOUT_MS], give up on this engine and try
        // the next.
        mainHandler.postDelayed({
            if (!_isReady.value && tts != null) {
                Log.w(TAG, "Engine [$index] $engine did not respond within " +
                        "${ENGINE_BIND_TIMEOUT_MS}ms — trying next")
                tryNextEngine(index + 1)
            }
        }, ENGINE_BIND_TIMEOUT_MS)
    }

    /**
     * Engine bound successfully — finish configuration. Every config
     * call is wrapped in try/catch because some engines don't support
     * all standard APIs (e.g. some iFlytek builds throw on
     * setAudioAttributes).
     */
    private fun onEngineSuccess(engine: String?, index: Int) {
        // Cancel the watchdog for this engine.
        mainHandler.removeCallbacksAndMessages(null)

        // CRITICAL — set audio attributes FIRST, before any
        // setLanguage / speak call.
        try {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        } catch (e: Throwable) {
            Log.w(TAG, "setAudioAttributes threw for $engine — continuing", e)
        }

        try {
            val langResult = tts?.setLanguage(Locale.US)
            Log.w(TAG, "  setLanguage(Locale.US)=$langResult")
        } catch (e: Throwable) {
            Log.w(TAG, "setLanguage threw for $engine — continuing", e)
        }

        // Explicit defaults — some engines don't initialize these on
        // their own. Wrap each in case the engine doesn't support them.
        try { tts?.setSpeechRate(1.0f) } catch (e: Throwable) {
            Log.w(TAG, "setSpeechRate threw — continuing", e)
        }
        try { tts?.setPitch(1.0f) } catch (e: Throwable) {
            Log.w(TAG, "setPitch threw — continuing", e)
        }

        // UtteranceProgressListener — diagnostic only.
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "utterance onStart: $utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "utterance onDone: $utteranceId")
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "utterance onError: $utteranceId")
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "utterance onError: $utteranceId, code=$errorCode")
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "setOnUtteranceProgressListener threw — continuing", e)
        }

        _isAvailable.value = true
        _isReady.value = true
        drainPendingQueue()
    }

    /**
     * Speak [word] aloud. Safe to call before any engine binds — the
     * word is queued and played once the engine reports ready.
     * Re-tapping the speaker cancels any in-flight utterance
     * (QUEUE_FLUSH) rather than chaining.
     */
    fun speak(word: String) {
        if (word.isBlank()) return
        if (_isReady.value) {
            try {
                // Flush mode: re-tapping cancels the prior utterance.
                val result = tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, utteranceId(word))
                Log.d(TAG, "speak('$word') result=$result")
            } catch (e: Throwable) {
                Log.w(TAG, "speak() threw for '$word'", e)
            }
        } else {
            synchronized(lock) {
                if (pending.size >= MAX_PENDING) {
                    pending.removeAt(0)
                }
                pending.add(word)
            }
            Log.d(TAG, "speak('$word') queued (isReady=false), pending.size=${pending.size}")
        }
    }

    private fun drainPendingQueue() {
        val drained: List<String>
        synchronized(lock) {
            drained = pending.toList()
            pending.clear()
        }
        drained.lastOrNull()?.let { last ->
            try {
                tts?.speak(last, TextToSpeech.QUEUE_FLUSH, null, utteranceId(last))
            } catch (e: Throwable) {
                Log.w(TAG, "drainPendingQueue speak() threw", e)
            }
        }
    }

    private fun utteranceId(word: String): String = "tts-${word.hashCode()}"

    private companion object {
        const val TAG = "TtsManager"
        // Per-engine bind timeout.
        const val ENGINE_BIND_TIMEOUT_MS = 2_000L
        // Delay between consecutive engine attempts. Gives the
        // previous engine's shutdown message time to dispatch before
        // we try to bind the next one — some engines (iFlytek) get
        // confused with back-to-back shutdown+bind.
        const val INTER_ENGINE_DELAY_MS = 50L
        const val MAX_PENDING = 5
    }
}