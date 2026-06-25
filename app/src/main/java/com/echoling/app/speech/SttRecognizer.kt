package com.echoling.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System SpeechRecognizer wrapper for the testing tab's STT flow.
 *
 * Wraps Android's [SpeechRecognizer] with a [SharedFlow] of [SttEvent] so
 * callers don't need to manage [RecognitionListener] lifecycle. Uses
 * [MutableSharedFlow] with extraBufferCapacity=4 so events survive a brief
 * collector start race.
 *
 * Not thread-safe for concurrent start() calls — callers must serialize
 * (the ViewModel does this via state checks).
 */
@Singleton
class SttRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class SttEvent {
        data class PartialResults(val text: String) : SttEvent()
        data class Results(val text: String) : SttEvent()
        data class Error(val code: Int, val message: String) : SttEvent()
    }

    private val _events = MutableSharedFlow<SttEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SttEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String = "en-US") {
        if (recognizer != null) stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // v2: emit real amplitude for waveform animation
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "onError: $error")
                    _events.tryEmit(SttEvent.Error(error, "SpeechRecognizer error code: $error"))
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    Log.d(TAG, "onResults: '$text'")
                    _events.tryEmit(SttEvent.Results(text))
                }
                override fun onPartialResults(partial: Bundle?) {
                    // v1: ignore partial results
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(intent)
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "stopListening threw", e)
        }
        try {
            recognizer?.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "destroy threw", e)
        }
        recognizer = null
    }

    private companion object {
        const val TAG = "SttRecognizer"
    }
}
