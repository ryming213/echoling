package com.echoling.app.transcription

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Stub worker for Task 4.1. The full 4-step pipeline
 * (ffmpeg -> Vosk -> SRT -> mark completed) is added in Task 4.2.
 *
 * Lives in the `transcription` package per the side-effect-services
 * bypass rule (CLAUDE.md §5.3): workers are infrastructure, not
 * domain UseCases.
 */
@HiltWorker
class AutoTranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val KEY_COURSE_ID = "courseId"
        const val KEY_MEDIA_PATH = "mediaPath"
        const val KEY_PROGRESS = "progress"
    }
}