package com.echoling.app.speech

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
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
     * (2026-07-18) Added [progressCallback] so the auto-subtitle
     * worker can push byte-level progress to the UI during the Vosk
     * step (30..70% range of the overall pipeline — the longest
     * step). The callback fires once per 8 KB PCM chunk with the
     * fraction of audio bytes processed so far (0..1). Workers
     * should gate publishing (1 Hz is fine — the UI animates between
     * publishes) and remap the fraction into their overall progress
     * range.
     *
     * Returns a [Result] wrapping a list of [com.echoling.app.transcription.VoskSegment].
     * On failure the throwable is wrapped via [Result.failure] (same
     * convention as [transcribeFile]).
     */
    suspend fun transcribeFileWithSegments(
        wavPath: String,
        // (2026-07-18) Suspend so the worker can call its own
        // `throttleProgress` (suspend; uses `setProgress` + Room
        // write) directly inside the callback without runBlocking.
        // The chunk loop runs on Dispatchers.IO but the suspend
        // dispatcher resumes back on IO when `throttleProgress`
        // returns.
        progressCallback: (suspend (Float) -> Unit)? = null,
    ): Result<List<com.echoling.app.transcription.VoskSegment>> = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel().getOrElse { return@withContext Result.failure(it) }
            val segments = transcribeToSegments(model, wavPath, progressCallback)
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
                        // its text in JSON form.
                        //
                        // (2026-07-23) §17.X fix: when
                        // [setMaxAlternatives] is > 0 (we use
                        // [maxAlternatives] here), the result JSON
                        // shape is:
                        //   { "alternatives": [ {"text": "...", ...} ] }
                        // — top-level "text" is absent. Reading it
                        // directly returned "" → endpoint prefixes
                        // were never accumulated → for a recording
                        // like "I went to the store [pause] and
                        // bought some apples", the user got back
                        // ONLY the trailing segment ("and bought
                        // some apples"); the first half was lost.
                        //
                        // Same bug as [appendSegmentFromResult]'s
                        // (2026-07-18) entry — that helper was fixed
                        // for the segments worker but THIS code path
                        // (the testing page's n-best transcription)
                        // was missed. Unwrap `alternatives[0]` here
                        // the same way; fall back to the top-level
                        // object when `alternatives` is absent (the
                        // [setMaxAlternatives(0)] shape — defensive).
                        val partialJson = recognizer.result
                        Log.d(TAG, "endpoint partial: $partialJson")
                        val partialObj = JSONObject(partialJson)
                        val source = partialObj.optJSONArray("alternatives")
                            ?.optJSONObject(0) ?: partialObj
                        val partialText = source.optString("text", "").trim()
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
            // (2026-07-23) §17.X fix: same alternatives[] unwrap as
            // the endpoint partial above. Without this, `finalText`
            // was always "" when [setMaxAlternatives] was > 0 and
            // the top-1 candidate (the combined full transcript)
            // never made it into [candidates] — only the raw
            // alternatives list contributed, which is fine when the
            // alternatives' text already covers everything, but
            // lost the endpoint-accumulated prefix when the trailing
            // segment wasn't in the alternatives (or was empty).
            val finalSource = finalJsonObj.optJSONArray("alternatives")
                ?.optJSONObject(0) ?: finalJsonObj
            val finalText = finalSource.optString("text", "").trim()

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
     *
     * (2026-07-18) [progressCallback] fires after every 8 KB chunk
     * with the fraction of audio samples consumed (`pcmSamples /
     * totalSamples`). `totalSamples` is derived from the WAV file
     * size minus the 44-byte RIFF header; each int16 sample is 2
     * bytes. A `coerceIn(0f, 1f)` guards against rounding when the
     * file size is slightly larger than the actual sample payload
     * (rare — some encoders pad the trailing byte).
     */
    private suspend fun transcribeToSegments(
        model: Model,
        wavPath: String,
        progressCallback: (suspend (Float) -> Unit)? = null,
    ): List<com.echoling.app.transcription.VoskSegment> {
        val sampleRate = 16_000f
        val recognizer = Recognizer(model, sampleRate).apply {
            setWords(true)
            setMaxAlternatives(1)
        }
        val segments = ArrayList<com.echoling.app.transcription.VoskSegment>()
        // PCM diagnostics — computed once while we walk the file.
        // Used to disambiguate "audio was silent" vs "audio had content
        // but Vosk found no speech" vs "Vosk crashed". The recognizer
        // itself doesn't surface why it returned empty, so we look at
        // the input ourselves. RMS is in int16 full-scale units; an
        // MP3 normalized to 0 dBFS sits around 0.05-0.2; a quiet
        // voice recording around 0.01-0.05; < 0.001 means effectively
        // silent (or a bad decode that wrote zeros).
        var pcmSamples = 0L
        var nonZeroSamples = 0L
        var absSum = 0L
        var peakAbs = 0
        // (2026-07-18) Compute totalSamples once from WAV file size
        // minus the 44-byte RIFF header. Used by the progress callback
        // to report bytes-processed fraction (pcmSamples / totalSamples).
        // The chunk loop fires the callback ~4 Hz on a typical 16 kHz
        // mono file (8 KB chunks / 16 kHz / 2 bytes = ~250 ms per chunk);
        // the worker throttles these to 1 Hz before publishing to the DB.
        // Pre-converted to Float so the loop divides Float/Float — the
        // Long/Float division overload chain is the overload-resolution
        // ambiguity that Kotlin trips on if you mix the two directly.
        val totalSamplesF: Float = run {
            val wavBytes = (File(wavPath).length() - 44).coerceAtLeast(0L)
            // bytes / 2 bytes-per-sample = samples
            (wavBytes / 2L).toFloat()
        }
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
                    for (i in 0 until shortLen) {
                        val s = bb.short.toInt()
                        shorts[i] = s.toShort()
                        pcmSamples++
                        if (s != 0) nonZeroSamples++
                        val abs = if (s < 0) -s else s
                        absSum += abs
                        if (abs > peakAbs) peakAbs = abs
                    }
                    if (recognizer.acceptWaveForm(shorts, shortLen)) {
                        appendSegmentFromResult(recognizer, segments)
                    }
                    // (2026-07-18) Fire progress callback. Cheap (~ns);
                    // the worker's throttleProgress drops most calls
                    // anyway. coerceIn defends against off-by-one when
                    // WAV file size is slightly larger than the actual
                    // PCM payload (some encoders pad trailing bytes).
                    progressCallback?.invoke(
                        if (totalSamplesF > 0f) (pcmSamples.toFloat() / totalSamplesF).coerceIn(0f, 1f)
                        else 0f,
                    )
                }
            }
            // Final un-committed segment.
            appendSegmentFromResult(recognizer, segments, final = true)
        } finally {
            try { recognizer.close() } catch (_: Throwable) {}
        }

        val rms = if (pcmSamples > 0) absSum.toDouble() / pcmSamples / 32768.0 else 0.0
        Log.i(
            TAG,
            "PCM stats: samples=$pcmSamples nonZero=$nonZeroSamples peak=$peakAbs/32768 " +
                "rms=%.5f segments=%d".format(rms, segments.size),
        )
        if (pcmSamples > 0 && nonZeroSamples.toDouble() / pcmSamples < 0.001) {
            Log.w(
                TAG,
                "WAV is effectively silent (<0.1% non-zero samples, peak=$peakAbs). " +
                    "Vosk returns 0 segments by design — the source MKV's audio track " +
                    "may have decoded to zeros (broken stream, or non-audio track selected " +
                    "by ffmpeg's default -i mapping)."
            )
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
     *
     * (2026-07-18) **Critical bug fix**: when [Recognizer.setMaxAlternatives]
     * is > 0 (we use 1), Vosk's result JSON is wrapped in an
     * `alternatives[]` array:
     *
     *   { "alternatives": [ { "result": [...], "text": "...", "confidence": N }, ... ] }
     *
     * The OLD code did `obj.optString("text", "")` on the TOP level, which
     * returned "" because top-level `text` doesn't exist in this shape.
     * `appendSegmentFromResult` then early-returned → `segments` stayed
     * empty → worker threw "未识别到任何语音" even though Vosk had
     * actually transcribed the audio correctly. The bug only manifested
     * after a recent Recognizer construction change introduced
     * `setMaxAlternatives(1)` (it had been left at the default of 0
     * before, which produces the simpler `{text, result}` shape).
     *
     * Fix: read from `alternatives[0]` when present, fall back to the
     * top-level object when `alternatives` is absent (the
     * `setMaxAlternatives(0)` shape). Both code paths in this file —
     * the per-chunk `recognizer.result` and the EOF `recognizer.finalResult`
     * — go through this helper so the same fix covers both.
     */
    private fun appendSegmentFromResult(
        recognizer: Recognizer,
        out: MutableList<com.echoling.app.transcription.VoskSegment>,
        final: Boolean = false,
    ) {
        val json = if (final) recognizer.finalResult else recognizer.result
        val obj = JSONObject(json)
        // (2026-07-18) unwrap alternatives[] when present.
        val source = obj.optJSONArray("alternatives")?.optJSONObject(0) ?: obj

        val text = source.optString("text", "").trim()
        if (text.isEmpty()) return
        val words = source.optJSONArray("result") ?: return
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
