package com.echoling.app.transcription

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for the [AutoTranscriptionWorker] WorkManager integration.
 * Owns the `auto-subtitle-<courseId>` unique-work tag so that
 * retry / re-enqueue from different call sites (ImportViewModel,
 * CourseListScreen re-try chip) all land on the same code path.
 *
 * **Why a Singleton facade, not direct WorkManager calls in the
 * ViewModel:** the ViewModel should not know about
 * `OneTimeWorkRequestBuilder` / `Constraints` / `enqueueUniqueWork`
 * — that's plumbing. A facade keeps the VM testable on JVM and
 * gives us one place to change the queueing policy later (e.g.
 * switch to a chained worker for ffmpeg + Vosk).
 *
 * **Why `enqueueUniqueWork` with `REPLACE`:** if the user re-tries
 * a FAILED job, the old work request (if still pending) is
 * cancelled and replaced. `REPLACE` here is the **course-scoped**
 * policy — different courses never conflict because the unique
 * name is `auto-subtitle-<courseId>`.
 *
 * **Why `setRequiresStorageNotLow(true)`:** the worker writes a
 * 30 MB/h temp WAV to `cacheDir/auto_subtitle/`. Under storage
 * pressure the OS may kill the worker mid-write; gating on
 * "not low" lets WorkManager defer until the user frees space.
 */
@Singleton
class AutoTranscriptionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(courseId: String, mediaPath: String) {
        val request = OneTimeWorkRequestBuilder<AutoTranscriptionWorker>()
            .setInputData(
                workDataOf(
                    AutoTranscriptionWorker.KEY_COURSE_ID to courseId,
                    AutoTranscriptionWorker.KEY_MEDIA_PATH to mediaPath,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(WORK_TAG_GLOBAL)
            .addTag("$WORK_NAME_PREFIX-$courseId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_NAME_PREFIX-$courseId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Flow of [WorkInfo] for a given course. The UI uses this to
     * render the in-progress chip with the latest progress value.
     * Returns all states (ENQUEUED, RUNNING, SUCCEEDED, FAILED,
     * CANCELLED) — UI layer filters as needed.
     */
    fun observeWorkInfo(courseId: String): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow("$WORK_NAME_PREFIX-$courseId")
            .map { infos -> infos.filter { it.state != WorkInfo.State.CANCELLED } }

    companion object {
        /**
         * Global WorkManager tag attached to every auto-subtitle work
         * item. Used by UI / debug tooling to list all auto-subtitle
         * jobs across the app, independent of which course they
         * belong to.
         */
        const val WORK_TAG_GLOBAL = "auto-subtitle"

        /**
         * Prefix used to derive the per-course unique work name and
         * tag (`auto-subtitle-<courseId>`). Kept distinct from
         * [WORK_TAG_GLOBAL] even though both currently share the
         * string value `"auto-subtitle"` — renaming one in the
         * future shouldn't silently affect the other.
         */
        const val WORK_NAME_PREFIX = "auto-subtitle"
    }
}