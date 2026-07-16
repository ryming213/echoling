package com.echoling.app.transcription

/**
 * One timed segment of speech from a Vosk transcription. Used by
 * [SrtSynthesizer] to build the SRT cue list.
 *
 * `startMs` / `endMs` are the absolute positions in the source WAV
 * file (counting from time 0). They come from Vosk's per-word
 * timestamps when the recognizer is configured with `setWords(true)`.
 *
 * `text` is the joined surface form of the words in the segment —
 * what the user hears. It may contain punctuation; SrtSynthesizer
 * writes it verbatim to the SRT.
 */
data class VoskSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)