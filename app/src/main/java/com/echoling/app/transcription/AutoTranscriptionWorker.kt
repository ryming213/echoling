package com.echoling.app.transcription

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.speech.VoskSpeechRecognizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * 4-step auto-subtitle pipeline (spec §4):
 *
 *   Step 1 (0% -> 30%): FfmpegAudioExtractor — extract mono 16 kHz WAV
 *   Step 2 (30% -> 70%): VoskSpeechRecognizer.transcribeFileWithSegments
 *   Step 3 (70% -> 95%): SrtSynthesizer.toSrt
 *   Step 4 (95% -> 100%): markTranscriptionCompleted + cleanup
 *
 * **Resume after process death**: the worker reads
 * `autoSubtitleProgress` from the DB on entry and skips completed
 * steps. The temp WAV in `cacheDir/auto_subtitle/` is preserved
 * (Android keeps cacheDir across process death until low-storage
 * GC); if ffmpeg step 1 was at 30% but the WAV was lost, step 1
 * re-runs.
 *
 * **Throttling**: progress updates are gated by a 1-second
 * minimum interval to avoid Room write amplification
 * (every 50ms × 2 hours = 144,000 useless UPDATEs).
 *
 * **Cancellation**: WorkManager sets `isStopped` when the user
 * cancels; we check it after each `setProgress` call to bail out
 * early and mark FAILED.
 *
 * **Logging**: every step writes one line at `Log.i` so a 5-min
 * video stuck at 0% on a real device can be diagnosed with
 * `adb logcat -s AutoTranscriptionWorker:V` — you should see
 * `doWork START` → `step1 ffmpeg.start` → `step1 ffmpeg.done
 * elapsedMs=...` → `step2 vosk.start` → ... If you only see
 * `doWork START` and nothing else, the worker is hung in ffmpeg
 * or Vosk; if you don't see `doWork START` at all, WorkManager
 * has not started the work (constraint / system deferral).
 */
@HiltWorker
class AutoTranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ffmpeg: FfmpegAudioExtractor,
    private val vosk: VoskSpeechRecognizer,
    private val courseRepo: CourseRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val courseId = inputData.getString(KEY_COURSE_ID)
            ?: return Result.failure(workDataOf("error" to "Missing courseId"))
        val mediaPath = inputData.getString(KEY_MEDIA_PATH)
            ?: return Result.failure(workDataOf("error" to "Missing mediaPath"))

        Log.i(TAG, "doWork START courseId=$courseId mediaPath=$mediaPath")

        val existing = courseRepo.getCourseById(courseId)
        val startProgress = existing?.autoSubtitleProgress ?: 0
        // Hoist wavPath so step 2 (delete check + Vosk) and step 3
        // (resume re-run) reference the same path. Two computations
        // here would silently diverge if wavFileFor(...) ever gains
        // per-invocation suffix logic.
        val wavPath = wavFileFor(courseId).absolutePath
        Log.i(TAG, "doWork startProgress=$startProgress wavPath=$wavPath")

        return try {
            // segments is held in memory so step 3 can reuse them
            // without re-running Vosk (Vosk is the slowest step).
            var segments: List<VoskSegment>? = null

            if (startProgress < 30) {
                courseRepo.markTranscriptionStarted(courseId)
                Log.i(TAG, "step1 ffmpeg.start mediaPath=$mediaPath")
                val ffmpegStart = System.currentTimeMillis()
                ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                Log.i(
                    TAG,
                    "step1 ffmpeg.done elapsedMs=${System.currentTimeMillis() - ffmpegStart} " +
                        "wav=${File(wavPath).length()}bytes",
                )
                throttleProgress(courseId, 30)
            }

            if (startProgress < 70) {
                if (!File(wavPath).exists()) {
                    Log.w(TAG, "step2 wav missing on disk — re-running ffmpeg")
                    // Process death + cacheDir GC — redo step 1.
                    ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                    throttleProgress(courseId, 30)
                }
                Log.i(TAG, "step2 vosk.start wav=${File(wavPath).length()}bytes")
                val voskStart = System.currentTimeMillis()
                // (2026-07-18) Pass a progress callback so the bar
                // advances during the Vosk step (the longest step —
                // 30..70% range). Vosk fires ~4 Hz (8 KB PCM chunks /
                // 16 kHz mono = ~250 ms/chunk); throttleProgress
                // gates publishing to 1 Hz so most callbacks are
                // dropped, and the UI animates between publishes.
                // Remap Vosk's 0..1 byte-fraction to the worker's
                // 30..70 range.
                segments = vosk.transcribeFileWithSegments(
                    wavPath = wavPath,
                    progressCallback = { frac ->
                        throttleProgress(courseId, 30 + (frac * 40).toInt())
                    },
                ).getOrThrow()
                Log.i(
                    TAG,
                    "step2 vosk.done elapsedMs=${System.currentTimeMillis() - voskStart} " +
                        "segments=${segments.size}",
                )
                if (segments.isEmpty()) {
                    throw IllegalStateException("未识别到任何语音")
                }
                throttleProgress(courseId, 70)
            }

            if (startProgress < 95) {
                // Reuse segments from step 2 if available; otherwise
                // re-run Vosk (only happens when resuming from 70-95%
                // after process death — segments are not persisted).
                val resolvedSegments = segments
                    ?: vosk.transcribeFileWithSegments(wavPath).getOrThrow()
                if (resolvedSegments.isEmpty()) {
                    // Resume path: Vosk re-run after process death
                    // may return empty (e.g. corrupted WAV surviving
                    // cacheDir GC). Without this guard, an empty SRT
                    // would be written and the course marked READY
                    // with totalSentences=0 — a hidden "ready but
                    // broken" state.
                    throw IllegalStateException("未识别到任何语音")
                }
                Log.i(TAG, "step3 srt.start segments=${resolvedSegments.size}")
                val srtText = SrtSynthesizer.toSrt(resolvedSegments)
                val srtFile = File(applicationContext.filesDir, "courses/$courseId.srt")
                srtFile.parentFile?.mkdirs()
                srtFile.writeText(srtText)
                // SrtSynthesizer produces N cues with N-1 "\n\n"
                // separators (last cue has trailing "\n" only).
                // count { isNotBlank } correctly reports N: empty=""
                // -> 0; 1 cue -> 1; 2 cues -> 2.
                val totalSentences = srtText.split("\n\n").count { it.isNotBlank() }
                courseRepo.markTranscriptionCompleted(
                    courseId = courseId,
                    srtPath = srtFile.absolutePath,
                    totalSentences = totalSentences.coerceAtLeast(0),
                )
                Log.i(TAG, "step3 srt.done cues=$totalSentences path=${srtFile.absolutePath}")
                throttleProgress(courseId, 95)
            }

            // Final 100% publish — best-effort. markTranscriptionCompleted
            // already wrote READY (source of truth). If we got here, work is
            // done. Any failure in this terminal block (Room, fs) is caught
            // by the outer try/catch and would downgrade READY to FAILED —
            // that's a recoverable race; the next re-enqueue writes READY
            // again. We intentionally return Result.success() so WorkManager
            // observes SUCCEEDED regardless of cancellation state: the chip
            // shows READY because the work IS complete, not because we're
            // ignoring errors.
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_COURSE_ID to courseId))
            courseRepo.updateTranscriptionProgress(courseId, 100)
            cleanupTempWav(courseId)
            Log.i(TAG, "doWork DONE courseId=$courseId")
            Result.success()
        } catch (e: CancellationException) {
            Log.w(TAG, "doWork CANCELLED courseId=$courseId (WorkManager stopped — not a real failure)")
            // Structured-concurrency cancellation: let it propagate; do NOT
            // mark the course FAILED (WorkManager cancelled, not crashed).
            cleanupTempWav(courseId)
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "auto-transcription failed for $courseId", e)
            // Wrap the failure-path side effects: if Room throws
            // inside markTranscriptionFailed (e.g. disk full), the
            // exception would otherwise escape doWork, leaving the DB
            // in prior IN_PROGRESS status — chip-render contract
            // (autoSubtitleStatus = FAILED ↔ WorkInfo.State.FAILED)
            // breaks and the next scheduler enqueue reads stale
            // startProgress. The original exception's message still
            // propagates as Result.failure (primary error preserved).
            runCatching {
                courseRepo.markTranscriptionFailed(courseId, e.message ?: "未知错误")
                cleanupTempWav(courseId)
            }.onFailure { nested ->
                Log.e(TAG, "secondary failure in cleanup for $courseId (primary error preserved)", nested)
            }
            Result.failure(workDataOf("error" to e.message))
        }
    }

    private var lastPublishAtMs = 0L

    private suspend fun throttleProgress(courseId: String, to: Int) {
        // Check isStopped BEFORE publishing — otherwise a cancelled
        // worker still emits one stale progress update (with the `to`
        // value) right before throwing, causing the chip to flicker
        // once before CANCELLED.
        if (isStopped) {
            throw CancellationException("Worker stopped by WorkManager")
        }
        val now = System.currentTimeMillis()
        if (now - lastPublishAtMs >= 1_000) {
            setProgress(workDataOf(KEY_PROGRESS to to, KEY_COURSE_ID to courseId))
            courseRepo.updateTranscriptionProgress(courseId, to)
            lastPublishAtMs = now
        }
    }

    private fun wavFileFor(courseId: String): File =
        File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav")

    private fun cleanupTempWav(courseId: String) {
        wavFileFor(courseId).delete()
    }

    companion object {
        const val KEY_COURSE_ID = "courseId"
        const val KEY_MEDIA_PATH = "mediaPath"
        const val KEY_PROGRESS = "progress"
        private const val TAG = "AutoTranscriptionWorker"
    }
}