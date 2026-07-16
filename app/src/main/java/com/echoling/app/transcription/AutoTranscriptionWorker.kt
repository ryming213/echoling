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

        val existing = courseRepo.getCourseById(courseId)
        val startProgress = existing?.autoSubtitleProgress ?: 0
        // Hoist wavPath so step 2 (delete check + Vosk) and step 3
        // (resume re-run) reference the same path. Two computations
        // here would silently diverge if wavFileFor(...) ever gains
        // per-invocation suffix logic.
        val wavPath = wavFileFor(courseId).absolutePath

        return runCatching {
            // segments is held in memory so step 3 can reuse them
            // without re-running Vosk (Vosk is the slowest step).
            var segments: List<VoskSegment>? = null

            if (startProgress < 30) {
                courseRepo.markTranscriptionStarted(courseId)
                ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                throttleProgress(courseId, 30)
            }

            if (startProgress < 70) {
                if (!File(wavPath).exists()) {
                    // Process death + cacheDir GC — redo step 1.
                    ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                    throttleProgress(courseId, 30)
                }
                segments = vosk.transcribeFileWithSegments(wavPath).getOrThrow()
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
                throttleProgress(courseId, 95)
            }

            // Final 100% — always publish, never throttled (terminal
            // state). We deliberately do NOT runCatching this block:
            // a Room failure mid-terminal would downgrade a successful
            // pipeline to FAILED via the outer runCatching, but the
            // race window (markTranscriptionCompleted already wrote
            // READY, then updateTranscriptionProgress(100) throws) is
            // tiny and recoverable on next re-enqueue.
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_COURSE_ID to courseId))
            courseRepo.updateTranscriptionProgress(courseId, 100)
            cleanupTempWav(courseId)
            // Honour user cancellation during the terminal block —
            // throws CancellationException out of doWork so WorkManager
            // sees the correct CANCELLED state instead of SUCCEEDED.
            if (isStopped) throw CancellationException("Worker stopped by WorkManager")
            Result.success()
        }.getOrElse { e ->
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