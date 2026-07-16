package com.echoling.app.speech

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based offline STT using Vosk. Takes a 16 kHz mono 16-bit PCM
 * WAV file (as written by [WavRecorder]) and returns the recognized
 * English text.
 *
 * **Why file-based, not live?** The Testing page (测试) needs both
 * a recording file (for playback) AND a transcription. On Android,
 * the system [android.speech.SpeechRecognizer] gives text but writes
 * no file; [android.media.MediaRecorder] writes a file but does no
 * transcription. They share the mic, so they cannot run in parallel.
 * Vosk accepts a file, which lets us keep the recording AND get text
 * from the same audio — no mic conflict, no extra library, no cloud.
 *
 * **Threading:** Model load is expensive (~1 s on first use after
 * install). The [Model] is cached in-process and reused for subsequent
 * transcriptions. Transcription itself is CPU-bound; we run on
 * [Dispatchers.IO] to keep the main thread free.
 *
 * **Format constraints:** Vosk requires 16 kHz mono 16-bit little-endian
 * PCM. The [WavRecorder] writes exactly that. If you ever feed this
 * from a different source (e.g. a file the user picks), validate the
 * WAV header first.
 */
@Singleton
class VoskSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val modelManager: ModelManager,
) {
    @Volatile private var cachedModel: Model? = null
    private val modelLock = Any()

    /**
     * Transcribe the given WAV file. Returns "" if the file is empty
     * or no speech was recognized (Vosk returns an empty `text` field
     * in that case).
     *
     * Performs the full pipeline: ensures model is downloaded, loads
     * it (cached after first call), reads the WAV file, feeds PCM to
     * the recognizer, parses the JSON result.
     *
     * (2026-07-10) Constrained grammar was tried (Vosk's third
     * ctor arg accepts a JSON-array grammar) but **made WER worse**
     * in the 测试跟读 flow — Vosk's grammar mode is "phrase must
     * match one of the supplied strings *exactly*" — if the user
     * drops a filler word or says "foxes" instead of "fox", the
     * recognizer returns the empty string, which WordMatcher
     * counts as a hard Fail. Open-vocabulary mode (no grammar) at
     * least returns the close-but-wrong text so the user gets
     * partial credit. If grammar comes back someday it should be
     * scoped to short command-style utterances, not whole sentences.
     */
    suspend fun transcribeFile(wavPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel().getOrElse { return@withContext Result.failure(it) }
            val text = transcribeWith(model, wavPath)
            Result.success(text)
        } catch (e: Throwable) {
            Log.e(TAG, "transcribeFile failed", e)
            Result.failure(e)
        }
    }

    /**
     * (2026-07-10) Transcribe with N-best alternatives. Vosk's small
     * model (WER ~10%) often returns a top-1 that is "close but
     * wrong" when the user's speech is ambiguous; the Top-3 contains
     * the correct phrase frequently. Returning the whole candidate
     * list lets [WordMatcher] pick the candidate that aligns best
     * with the expected subtitle, recovering many cases that the
     * top-1-only path would have marked as Failed.
     *
     * Returns candidates in order: [0] is the top-1 (same as
     * [transcribeFile] would have returned), [1..N-1] are
     * Vosk's `finalResult.alternatives[i].text` in order. Endpoint
     * partials (mid-sentence) are prepended to every candidate so
     * multi-segment recordings still get the earlier halves
     * preserved.
     *
     * If Vosk returned no alternatives (very short / very quiet
     * audio), the list is just [top-1] — callers should fall back
     * to whatever it would have done with [transcribeFile].
     *
     * Cost: enabling alternatives increases Vosk's internal search
     * graph cost; with maxAlternatives=3 on vosk-model-small-en-us-0.15
     * the wall-clock on a 5-second test sentence is +15-30% vs
     * top-1-only. Acceptable for the 测试 page where the user is
     * already waiting 1-2s for the small model.
     */
    suspend fun transcribeFileAlternatives(
        wavPath: String,
        maxAlternatives: Int = 3,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel().getOrElse { return@withContext Result.failure(it) }
            val alts = transcribeWithAlternatives(model, wavPath, maxAlternatives)
            Result.success(alts)
        } catch (e: Throwable) {
            Log.e(TAG, "transcribeFileAlternatives failed", e)
            Result.failure(e)
        }
    }

    /**
     * (2026-07-15) Transcribe a WAV file and return a timed segment
     * list, ready for SRT synthesis. Used by the auto-subtitle
     * worker (spec §6.2). Differs from [transcribeFile] /
     * [transcribeFileAlternatives] in two ways:
     *
     *   1. Always calls `setWords(true)` so each segment's
     *      partial / final JSON carries per-word `start` / `end`
     *      timestamps — without this, Vosk returns only
     *      `{"text": "..."}` and we have no way to derive cue
     *      boundaries.
     *   2. Calls `setMaxAlternatives(1)` — we don't need n-best
     *      for SRT synthesis (the synthesized text is what it is;
     *      the user's reading speed decides whether the cue is
     *      long enough, not Vosk's confidence).
     *
     * Returns a [Result] wrapping a list of [com.echoling.app.transcription.VoskSegment].
     * On failure the throwable is wrapped via [Result.failure] (same
     * convention as [transcribeFile]).
     */
    suspend fun transcribeFileWithSegments(
        wavPath: String,
    ): Result<List<com.echoling.app.transcription.VoskSegment>> = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel().getOrElse { return@withContext Result.failure(it) }
            val segments = transcribeToSegments(model, wavPath)
            Result.success(segments)
        } catch (e: Throwable) {
            Log.e(TAG, "transcribeFileWithSegments failed", e)
            Result.failure(e)
        }
    }

    private fun getOrLoadModel(): Result<Model> {
        cachedModel?.let { return Result.success(it) }
        synchronized(modelLock) {
            cachedModel?.let { return Result.success(it) }
            val ready = modelManager.isModelReady()
            if (!ready) {
                return Result.failure(
                    IllegalStateException("Vosk model not downloaded. Call ModelManager.ensureModelReady() first.")
                )
            }
            return try {
                val m = Model(modelManager.modelDir().absolutePath)
                cachedModel = m
                Result.success(m)
            } catch (e: Throwable) {
                Log.e(TAG, "Model load failed", e)
                Result.failure(e)
            }
        }
    }

    private fun transcribeWith(model: Model, wavPath: String): String {
        // Thin wrapper — top-1 only. Use [transcribeWithAlternatives]
        // when the caller wants n-best (see kdoc on
        // [transcribeFileAlternatives]).
        val list = transcribeWithAlternatives(model, wavPath, maxAlternatives = 1)
        return list.firstOrNull().orEmpty()
    }

    private fun transcribeWithAlternatives(
        model: Model,
        wavPath: String,
        maxAlternatives: Int,
    ): List<String> {
        // Skip the 44-byte RIFF header — read sample-rate etc. from
        // the header just enough to validate, but we know WavRecorder
        // writes 16kHz mono 16-bit so the recognizer config matches.
        val sampleRate = 16_000f
        val recognizer = Recognizer(model, sampleRate)
        // Must be called BEFORE acceptWaveForm — Vosk allocates the
        // n-best slot array at construction time.
        recognizer.setMaxAlternatives(maxAlternatives.coerceAtLeast(1))
        // (2026-07-05) Bug fix: Vosk's recognizer has internal endpoint
        // detection — when it sees ~1s of silence it commits the audio
        // up to that pause as a finished segment and treats anything
        // afterwards as a new segment. The previous code only called
        // `recognizer.finalResult` at EOF, which returns ONLY the
        // trailing un-committed segment's text. A user who said half
        // a sentence, paused, then finished the second half would get
        // back only the second half (the first half was committed into
        // a separate segment that the old code threw away).
        //
        // Fix: every time `acceptWaveForm` returns true (endpoint
        // detected), parse `recognizer.result` for that committed
        // segment's text and accumulate it. Then concatenate with
        // `finalResult` at end for the trailing segment. Spaces
        // separate adjacent segments so the joined transcript reads
        // naturally — Vosk's per-segment text never contains spaces
        // except where the user actually said them.
        val allText = StringBuilder()
        try {
            FileInputStream(wavPath).use { fis ->
                val header = ByteArray(44)
                val headerRead = fis.read(header)
                require(headerRead == 44) { "WAV file too short: $wavPath" }
                // Sanity check the header — WavRecorder must produce this.
                val riff = String(header.copyOfRange(0, 4), Charsets.US_ASCII)
                val wave = String(header.copyOfRange(8, 12), Charsets.US_ASCII)
                require(riff == "RIFF" && wave == "WAVE") { "Not a WAV file: $wavPath" }
                val audioFormat = ByteBuffer.wrap(header.copyOfRange(20, 22))
                    .order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val channels = ByteBuffer.wrap(header.copyOfRange(22, 24))
                    .order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val sr = ByteBuffer.wrap(header.copyOfRange(24, 28))
                    .order(ByteOrder.LITTLE_ENDIAN).int
                require(audioFormat == 1) { "WAV is not PCM (format=$audioFormat)" }
                require(channels == 1) { "WAV is not mono (channels=$channels)" }
                require(sr == 16_000) { "WAV sample rate is $sr, need 16000" }

                // Stream PCM in 8 KB chunks. Each int16 sample is 2 bytes.
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val n = fis.read(chunk)
                    if (n <= 0) break
                    val shortLen = n / 2
                    val shorts = ShortArray(shortLen)
                    val bb = ByteBuffer.wrap(chunk, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until shortLen) shorts[i] = bb.short
                    if (recognizer.acceptWaveForm(shorts, shortLen)) {
                        // Endpoint detected — this segment is now
                        // "committed". `recognizer.result` returns
                        // its text in JSON form ({"text": "..."}).
                        // Append it so a multi-segment recording
                        // (e.g. mid-sentence pause) keeps every
                        // segment instead of dropping the earlier
                        // ones.
                        val partialJson = recognizer.result
                        Log.d(TAG, "endpoint partial: $partialJson")
                        val partialText = JSONObject(partialJson)
                            .optString("text", "").trim()
                        if (partialText.isNotEmpty()) {
                            if (allText.isNotEmpty()) allText.append(' ')
                            allText.append(partialText)
                        }
                    }
                }
            }
            // Vosk returns final result on getFinalResult() after EOF.
            // This carries the trailing un-committed segment. The
            // committed segments above are already in [allText].
            val finalJson = recognizer.finalResult
            Log.d(TAG, "final: $finalJson")
            val finalJsonObj = JSONObject(finalJson)
            val finalText = finalJsonObj.optString("text", "").trim()

            // (2026-07-10) n-best collection. Vosk's finalResult JSON
            // shape with setMaxAlternatives(N) is:
            //   {
            //     "text": "the top 1 candidate",
            //     "alternatives": [
            //       {"text": "alt 1", "confidence": 0.93},
            //       {"text": "alt 2", "confidence": 0.71},
            //       ...
            //     ]
            //   }
            // Build candidates = [top1_with_endpoints_prepended,
            //                     alt1_with_endpoints_prepended, ...]
            // so each candidate is independently comparable to the
            // expected subtitle.
            val endpointPrefix = allText.toString().trim()
            val candidates = ArrayList<String>(maxAlternatives.coerceAtLeast(1))

            // Top-1 — already has endpoint prefix concatenated below.
            if (finalText.isNotEmpty()) {
                val combined = if (endpointPrefix.isEmpty()) finalText
                else "$endpointPrefix $finalText"
                candidates.add(combined)
            } else if (endpointPrefix.isNotEmpty()) {
                // Final segment was empty but earlier segments weren't
                // (very rare — user trailed off mid-sentence). Still
                // surface the partials so WordMatcher has something
                // to compare.
                candidates.add(endpointPrefix)
            }

            // Alternatives — only present if setMaxAlternatives > 0
            // AND Vosk found more than one reasonable path through
            // its search lattice. Parse defensively: missing or
            // malformed `alternatives` array just means we get fewer
            // candidates.
            val altsJson = finalJsonObj.optJSONArray("alternatives")
            if (altsJson != null) {
                for (i in 0 until altsJson.length()) {
                    val altObj = altsJson.optJSONObject(i) ?: continue
                    val altText = altObj.optString("text", "").trim()
                    if (altText.isEmpty()) continue
                    val combined = if (endpointPrefix.isEmpty()) altText
                    else "$endpointPrefix $altText"
                    // Skip if identical to an earlier candidate
                    // (Vosk occasionally emits dupes for very
                    // confident top picks).
                    if (combined !in candidates) {
                        candidates.add(combined)
                    }
                    if (candidates.size >= maxAlternatives) break
                }
            }

            Log.d(TAG, "alternatives returned: ${candidates.size} (max=$maxAlternatives)")
            return candidates
        } finally {
            try { recognizer.close() } catch (_: Throwable) {}
        }
    }

    /** Release the cached model — call when STT is no longer needed. */
    fun shutdown() {
        synchronized(modelLock) {
            try { cachedModel?.close() } catch (_: Throwable) {}
            cachedModel = null
        }
    }

    /**
     * Internal worker for [transcribeFileWithSegments]. Always uses
     * `setWords(true)`. Walks the partial/final result JSON the
     * same way [transcribeWithAlternatives] does (accumulate
     * committed segments, then append the final un-committed
     * segment at EOF).
     */
    private fun transcribeToSegments(
        model: Model,
        wavPath: String,
    ): List<com.echoling.app.transcription.VoskSegment> {
        val sampleRate = 16_000f
        val recognizer = Recognizer(model, sampleRate).apply {
            setWords(true)
            setMaxAlternatives(1)
        }
        val segments = ArrayList<com.echoling.app.transcription.VoskSegment>()
        try {
            FileInputStream(wavPath).use { fis ->
                // Skip 44-byte RIFF header (Vosk's recognizer reads
                // raw PCM, not WAV). The format is guaranteed by
                // FfmpegAudioExtractor: PCM 16-bit mono 16 kHz.
                val header = ByteArray(44)
                val headerRead = fis.read(header)
                require(headerRead == 44) { "WAV file too short: $wavPath" }

                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val n = fis.read(chunk)
                    if (n <= 0) break
                    val shortLen = n / 2
                    val shorts = ShortArray(shortLen)
                    val bb = java.nio.ByteBuffer.wrap(chunk, 0, n)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until shortLen) shorts[i] = bb.short
                    if (recognizer.acceptWaveForm(shorts, shortLen)) {
                        appendSegmentFromResult(recognizer, segments)
                    }
                }
            }
            // Final un-committed segment.
            appendSegmentFromResult(recognizer, segments, final = true)
        } finally {
            try { recognizer.close() } catch (_: Throwable) {}
        }
        return segments
    }

    /**
     * Extract one [com.echoling.app.transcription.VoskSegment] from a
     * recognizer partial or final result JSON. `final = true` reads
     * `recognizer.finalResult`; the default reads `recognizer.result`
     * (the most recent partial). Skips empty-text results.
     *
     * Uses the first word's start and the last word's end as the
     * segment boundaries (Vosk returns per-word timestamps when
     * `setWords(true)` was called).
     */
    private fun appendSegmentFromResult(
        recognizer: Recognizer,
        out: MutableList<com.echoling.app.transcription.VoskSegment>,
        final: Boolean = false,
    ) {
        val json = if (final) recognizer.finalResult else recognizer.result
        val obj = JSONObject(json)
        val text = obj.optString("text", "").trim()
        if (text.isEmpty()) return
        val words = obj.optJSONArray("result") ?: return
        if (words.length() == 0) return
        val first = words.getJSONObject(0)
        val last = words.getJSONObject(words.length() - 1)
        val startSec = first.optDouble("start", 0.0)
        val endSec = last.optDouble("end", 0.0)
        out.add(
            com.echoling.app.transcription.VoskSegment(
                startMs = (startSec * 1000).toLong(),
                endMs = (endSec * 1000).toLong(),
                text = text,
            )
        )
    }

    private companion object {
        const val TAG = "VoskSpeechRecognizer"
    }
}
