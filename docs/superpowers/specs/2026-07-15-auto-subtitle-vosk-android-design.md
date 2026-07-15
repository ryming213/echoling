# Auto Subtitle Generation (On-device Vosk + ffmpeg-kit) — Design Spec

**Date:** 2026-07-15
**Status:** Approved
**Author:** Claude (brainstorming session)

---

## 1. Problem

Today, importing a course in Echo Ling requires the user to manually attach a subtitle file (`.srt` / `.ass` / `.lrc`). If they only have an audio or video file, the course lands in the practice tabs but **shows nothing** — the user can't follow along sentence-by-sentence during "泛听" (listening) or get a real-time prompt during "测试" (testing).

We already ship a 68 MB on-device English STT model (Vosk `vosk-model-small-en-us-0.15`, deployed 2026-07-04 per [CLAUDE.md §12.26](CLAUDE.md)). It powers the **跟读测试** (test-the-recording) flow today. We want to reuse it for a second purpose: **read a fresh-imported audio/video file, transcribe it into an English SRT, and slot it into the same data pipeline that imported subtitles use** — so a course with no subtitles becomes a course with auto-generated subtitles, indistinguishable from a course with hand-written ones.

The user-visible goal: when the user picks an audio/video file in `ImportScreen`, **if they don't also pick a subtitle file**, give them a one-tap button to generate the subtitle on-device. No network. No new permissions. No third-party cloud STT.

---

## 2. Goal

When a user imports an audio/video file in `ImportScreen` without a subtitle:

- They see a "自动生成字幕" affordance with two buttons:
  - **立即转字幕** — block in ImportScreen until the subtitle is ready (10 s – 10 min depending on audio length), then import normally.
  - **稍后转字幕** — import the course immediately with `subtitleUri = null` and `autoSubtitleStatus = PENDING`; a background worker generates the subtitle; the user can leave the screen, come back later, and find the course ready.
- After generation, the subtitle file lives at `filesDir/courses/<courseId>.srt` and is referenced by the same `CourseEntity.subtitleUri` field that hand-imported subtitles use. **Zero downstream code changes** — `SrtParser` / `PracticeViewModel.loadSubtitles()` / `SentenceDao` / the three practice tabs all consume it identically.
- If the course has **any** subtitle file (user-picked or auto-generated), `autoSubtitleStatus` is left `null` — these are user-provided and trusted.

**Non-goals (this iteration):**
- Chinese / bilingual translation of the generated subtitle (English-only SRT, matching today's imported format).
- Cancellation UI ("cancel a running 稍后转 job" — YAGNI; WorkManager handles process death; 立即转 has natural progress visibility).
- Editing the generated SRT in-app before saving (the user can re-import with a manually edited SRT).
- Replacing Vosk small with a larger model (would add 92 MB to APK; small model WER ≈ 10% is acceptable for an opt-in auto-generation path).
- Replacing the import flow's hand-pick-subtitle path (auto-generation is **additive**; users still choose to attach subtitles).

---

## 3. Scope

**In scope:**
- On-device English STT for any media file the user can hand to `ImportScreen` today: `.mp4` / `.mkv` / `.webm` / `.mov` / `.mp3` / `.m4a` / `.wav` / `.flac` / `.ogg` / `.opus`.
- Long-file tolerance (no hard cap; soft warning at >30 min; hard reject at >3 h to bound RAM).
- Pause / resume: if the app process dies mid-转录, WorkManager re-launches the worker from the last persisted checkpoint (`autoSubtitleProgress`).
- Re-try: a `FAILED` course exposes a chip → tap → re-enqueue the same worker.
- §11.2 brand consistency on the new "自动生成字幕" affordance card and the course-list status chip.
- §11.6 splash-screen and §12.25 edge-to-edge status-bar behavior unchanged.
- §12.33 16 KB page-size alignment covers the new `lib/arm64-v8a/libavcodec.so` (and friends) from `ffmpeg-kit-min-gpl-6.0-2`.
- §12.26 fully-offline branding: no new permissions, no network code path.

**Out of scope:**
- ffmpeg-kit's GPL licensing — **the min-gpl package is GPL, which forces the whole APK to be GPL-licensed at distribution**. We accept this as a conscious trade-off (documented in README) and explicitly choose min-gpl over min (LGPL — codec coverage is too narrow; DTS / FLAC / Opus unsupported) and over full (LGPL — 80 MB more, no use cases we need beyond min-gpl).
- Batch import ("import this folder of 30 videos as 30 courses") — single-file only.
- Real-time / streaming transcription (the use case is file-based, not microphone).
- SRT-to-bilingual translation pass (no `SrtTranslator` this round; the import flow's existing bilingual handling for hand-attached SRTs is unchanged).
- Re-using this pipeline for non-`ImportScreen` audio (e.g. live recording → SRT in practice tabs) — not requested.

---

## 4. Architecture

```
[ImportScreen]
  ├─ User picks audio/video → subtitleUri stays null
  ├─ New "自动生成字幕" card appears with [立即转字幕] / [稍后转字幕]
  │
  └─ ImportViewModel.importCourseWithImmediateTranscription()
       OR  .importCourseWithDeferredTranscription()
       ↓
[ImportViewModel]
  ├─ copyUriToInternalStorage(...) — already exists (§12.36 era)
  ├─ importCourseUseCase(course) — inserts CourseEntity with subtitleUri=null
  ├─ if immediate: run transcription inline (block ImportScreen)
  ├─ if deferred:  AutoTranscriptionScheduler.enqueue(courseId, mediaPath)
  │                  → WorkManager.enqueueUniqueWork(REPLACE)
  │
[AutoTranscriptionWorker]   (CoroutineWorker, HiltWorkerFactory)
  Step 1 ── FfmpegAudioExtractor.extractMono16kWav(inputPath, courseId)
            └─ ffmpeg-kit: -vn -ac 1 -ar 16000 -f wav
            → cacheDir/auto_subtitle/<courseId>.wav
            → publish progress: 0% → 30%
  Step 2 ── VoskSpeechRecognizer.transcribeFileWithSegments(wavPath)
            └─ setWords(true) + setMaxAlternatives(1)
            → List<VoskSegment(startMs, endMs, text)>
            → publish progress: 30% → 70%
  Step 3 ── SrtSynthesizer.toSrt(segments)
            └─ if segment too long (>8 s OR >12 words) → redistribute_timestamps
            └─ END_PAD_MS = 400
            → filesDir/courses/<courseId>.srt
            → publish progress: 70% → 95%
  Step 4 ── CourseRepository.markTranscriptionCompleted(courseId, srtPath, totalSentences)
            └─ PracticeViewModel.syncSentencesUseCase(srtPath)  [re-uses §12 existing path]
            └─ autoSubtitleStatus = READY
            → publish progress: 100%
  on fail ── CourseRepository.markTranscriptionFailed(courseId, errorMessage)
            └─ cleanup cacheDir/auto_subtitle/<courseId>.wav
            └─ keep filesDir/courses/<courseId>.srt only if non-empty
```

Three new files in `app/src/main/java/com/echoling/app/transcription/`:
- `FfmpegAudioExtractor.kt` — wraps `FFmpegKit` (configures cmd, awaits `Session` complete, reads return code, throws on non-zero).
- `SrtSynthesizer.kt` — pure Kotlin; takes `List<VoskSegment>` → writes `.srt` string; no Android dependencies (unit-testable on JVM).
- `AutoTranscriptionWorker.kt` + `AutoTranscriptionScheduler.kt` — WorkManager wrapper, owns Hilt injection and progress publishing.

Two modified files:
- `app/build.gradle.kts` — adds `com.arthenica:ffmpeg-kit-min-gpl:6.0-2` and `androidx.work:work-runtime-ktx:2.9.1`.
- `app/src/main/java/com/echoling/app/data/local/db/entity/CourseEntity.kt` — adds 3 columns.

DB migration: `MIGRATION_5_6` adds three nullable columns to `courses`. `fallbackToDestructiveMigration()` (CLAUDE.md §9.2) stays as the safety net — the new migration is the **correct** path; destructive is the backstop.

**Why not just delete §12.19-§12.43 and rewrite from scratch**: the entire downstream pipeline (`SrtParser` → `SyncSentencesUseCase` → `PracticeViewModel.loadSubtitles()` → the three practice tabs) already consumes a file path + course id. The auto-generated SRT is byte-for-byte the same format. The work is upstream of that pipeline.

---

## 5. Data flow

### 5.1 DB schema (CourseEntity)

```
+ autoSubtitleStatus      : String?   ("PENDING" | "IN_PROGRESS" | "READY" | "FAILED")
+ autoSubtitleErrorMessage: String?   (user-facing error; null unless FAILED)
+ autoSubtitleProgress    : Int       (0..100; throttled 1 Hz from worker)
```

`null` on `autoSubtitleStatus` means "user-provided subtitle file, no auto-generation needed". Default `""` (empty string) is **not** used for this distinction — `null` is the canonical "no auto subtitle" sentinel.

`Course` domain model mirrors: `val autoSubtitleStatus: AutoSubtitleStatus?` (enum, nullable).

### 5.2 Worker progress → UI

`AutoTranscriptionWorker.doWork()` calls `setProgress(workDataOf("progress" to N))` on a 1 Hz throttle (don't emit faster; avoids Room write amplification). `ImportViewModel` and `CourseListScreen` observe the `WorkInfo` flow via `WorkManager.getWorkInfosByTagFlow("auto-subtitle-<courseId>")`.

Two observation paths:

| Path | Source | Sink |
|---|---|---|
| **立即转** | `ImportViewModel.runBlockingImmediateTranscription()` polls worker progress via a long-lived `WorkManager` observer (it's already enqueued, just blocks UI). | `ImportScreen` "正在识别中… 30%" |
| **稍后转** | `CourseListScreen` collects `workInfosForCourseId` Flow. | Course-list card chip "字幕识别中 X%" |

After `SUCCEEDED`, both paths converge: `CourseRepository.markTranscriptionCompleted` writes the new `subtitleUri` + `autoSubtitleStatus = READY`. The course-list `Flow<List<Course>>` re-emits with the new state. UI layer treats **both `null` and `READY` as "no chip"** because in both cases `subtitleUri` is set (the row has a usable subtitle). The chip only renders for `PENDING` / `IN_PROGRESS` / `FAILED`.

### 5.3 Cancellation / pause / resume

- **Process death**: WorkManager automatically re-launches the worker with the same `WorkRequest`. The worker checks `autoSubtitleProgress` in the DB on entry — if it's >30 (i.e., ffmpeg completed), it **skips ffmpeg** and resumes from Vosk; if >70, **skips ffmpeg + Vosk** and resumes from SRT synthesis. (The wav temp file in `cacheDir/auto_subtitle/` is preserved because Android keeps `cacheDir` across process death until low-storage GC.)
- **User-initiated cancel**: not implemented this round (§2 non-goal).
- **Stuck jobs** (worker in `IN_PROGRESS > 1 h`): on next app launch, `EchoLingApplication.onCreate()` sweeps once and marks them `FAILED` with message "识别被中断,请重试". No new background process needed.

### 5.4 Re-try

`CourseListScreen` detects `autoSubtitleStatus = FAILED` and renders a clickable chip. Tap → `CompactConfirmDialog` ("重试识别 / 取消") → on confirm, `AutoTranscriptionScheduler.retry(courseId, mediaPath)`:

1. Delete existing `.srt` if present.
2. Reset `autoSubtitleStatus = PENDING`, `autoSubtitleProgress = 0`, `autoSubtitleErrorMessage = null`.
3. Re-enqueue the worker with `enqueueUniqueWork("auto-subtitle-$courseId", REPLACE, request)`.

---

## 6. Components

### 6.1 `FfmpegAudioExtractor` (`transcription/` package)

```kotlin
@Singleton
class FfmpegAudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extractMono16kWav(
        inputPath: String,
        courseId: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(context.cacheDir, "auto_subtitle/$courseId.wav")
            outFile.parentFile?.mkdirs()
            val session = FFmpegKit.execute(
                "-y -i \"$inputPath\" -vn -ac 1 -ar 16000 -f wav \"${outFile.absolutePath}\""
            )
            val returnCode = session.returnCode
            check(returnCode.isValueSuccess) {
                "ffmpeg exit code ${returnCode.value}: ${session.failStackTrace?.take(500)}"
            }
            check(outFile.length() > 1024) { "ffmpeg produced empty WAV" }
            outFile
        }
    }
}
```

**Key decisions:**
- `cacheDir` (not `filesDir`) for the temp WAV — Android may GC it; the WAV is re-creatable from the user's source audio.
- `-vn` to drop video (this is an audio extractor, not a remuxer).
- `-ac 1 -ar 16000` for mono 16 kHz (Vosk's required input format, per `VoskSpeechRecognizer.transcribeFile()`).
- No `-c:a pcm_s16le` flag — WAV's default codec is PCM 16-bit; Vosk's hard validation (audioFormat=1, channels=1, sampleRate=16000) will reject anything else.

### 6.2 `VoskSpeechRecognizer` extension

Add one method next to the existing `transcribeFileAlternatives` ([VoskSpeechRecognizer.kt:60](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt#L60)):

```kotlin
suspend fun transcribeFileWithSegments(
    wavPath: String,
): Result<List<VoskSegment>> = withContext(Dispatchers.IO) {
    runCatching {
        val model = modelManager.ensureModelReady().getOrThrow()
        val recognizer = Recognizer(model, 16000.0f).apply {
            setWords(true)              // we need per-word timestamps for boundary derivation
            setMaxAlternatives(1)       // we don't need n-best for SRT synthesis
        }
        val segments = mutableListOf<VoskSegment>()
        FileInputStream(wavPath).use { fis ->
            val buf = ByteArray(4096)
            while (fis.read(buf) >= 0) {
                if (recognizer.acceptWaveForm(buf, buf.size)) {
                    appendSegmentFromResult(recognizer, segments)
                }
            }
            appendSegmentFromResult(recognizer, segments, final = true)
        }
        recognizer.close()
        segments.toList()
    }
}
```

`VoskSegment(startMs, endMs, text)` is a new data class:

```kotlin
data class VoskSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
```

We always call `setWords(true)` even though we don't expose words to the caller — the `result` JSON contains a `result` array of `{word, start, end}` only when `setWords(true)`. We pull `first.start` and `last.end` to derive segment boundaries. Without `setWords(true)`, Vosk returns only `{"text": "..."}` in the partial, with no timing — the worker cannot compute SRT timestamps.

`Recognizer` is per-call (existing pattern, [VoskSpeechRecognizer.kt:156](app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt#L156)). The cost is ~150 ms to construct + close per 30-min audio chunk; not a bottleneck.

### 6.3 `SrtSynthesizer` (`transcription/` package)

```kotlin
object SrtSynthesizer {
    private const val END_PAD_MS = 400L          // Vosk commits ~700ms into silence; audio tail continues
    private const val MAX_SEGMENT_DURATION_MS = 8000L
    private const val MAX_SEGMENT_WORDS = 12

    fun toSrt(segments: List<VoskSegment>): String = buildString {
        var cueIndex = 1
        for (segment in segments) {
            val paddedEnd = segment.endMs + END_PAD_MS
            val pieces = redistributeTimestamps(
                startMs = segment.startMs,
                endMs = paddedEnd,
                text = segment.text,
                maxDurationMs = MAX_SEGMENT_DURATION_MS,
                maxWords = MAX_SEGMENT_WORDS,
            )
            for (piece in pieces) {
                appendLine(cueIndex)
                appendLine("${formatTimestamp(piece.startMs)} --> ${formatTimestamp(piece.endMs)}")
                appendLine(piece.text)
                appendLine()
                cueIndex++
            }
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000; val milli = (ms % 1_000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }
}
```

`redistributeTimestamps` is a port of `c:/Users/MING/myagent/split_srt_sentences.py:684` (Python), adapted to Kotlin. Splits a long segment by word count and/or duration, proportional time distribution, `OVERLAP_MS = 750`. Unit-testable on JVM — no Android imports.

### 6.4 `AutoTranscriptionWorker`

```kotlin
@HiltWorker
class AutoTranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ffmpeg: FfmpegAudioExtractor,
    private val vosk: VoskSpeechRecognizer,
    private val srt: SrtSynthesizer,
    private val courseRepo: CourseRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val courseId = inputData.getString(KEY_COURSE_ID)!!
        val mediaPath = inputData.getString(KEY_MEDIA_PATH)!!
        val startProgress = courseRepo.getCourse(courseId)?.autoSubtitleProgress ?: 0

        return runCatching {
            // 0% → 30%: ffmpeg (skip if resume)
            if (startProgress < 30) {
                markStarted(courseId)
                val wav = ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                publishProgress(courseId, 30)
            }
            // 30% → 70%: Vosk
            if (startProgress < 70) {
                val wavPath = File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav").absolutePath
                val segments = vosk.transcribeFileWithSegments(wavPath).getOrThrow()
                publishProgress(courseId, 70)
                // 70% → 95%: SRT synthesis
                val srtText = SrtSynthesizer.toSrt(segments)
                publishProgress(courseId, 95)
                val srtFile = File(applicationContext.filesDir, "courses/$courseId.srt")
                srtFile.parentFile?.mkdirs()
                srtFile.writeText(srtText)
                courseRepo.markTranscriptionCompleted(
                    courseId = courseId,
                    srtPath = srtFile.absolutePath,
                    totalSentences = (srtText.split("\n\n").size - 1).coerceAtLeast(0),
                )
            } else if (startProgress < 95) {
                // resume from 70%–95% (unlikely but possible)
                // re-run SRT synthesis from a previously-transcribed segment cache
                // (out of scope for v1; documented as a known limitation)
            }
            publishProgress(courseId, 100)
            cleanupTempWav(courseId)
            Result.success()
        }.getOrElse { e ->
            courseRepo.markTranscriptionFailed(courseId, e.message ?: "未知错误")
            Result.failure(workDataOf("error" to e.message))
        }
    }

    private suspend fun publishProgress(courseId: String, progress: Int) {
        setProgress(workDataOf(KEY_PROGRESS to progress, KEY_COURSE_ID to courseId))
        courseRepo.updateTranscriptionProgress(courseId, progress)
    }

    private suspend fun markStarted(courseId: String) {
        courseRepo.markTranscriptionStarted(courseId)
    }

    private fun cleanupTempWav(courseId: String) {
        File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav").delete()
    }

    companion object {
        const val KEY_COURSE_ID = "courseId"
        const val KEY_MEDIA_PATH = "mediaPath"
        const val KEY_PROGRESS = "progress"
    }
}
```

### 6.5 `AutoTranscriptionScheduler`

```kotlin
@Singleton
class AutoTranscriptionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue(courseId: String, mediaPath: String) {
        val request = OneTimeWorkRequestBuilder<AutoTranscriptionWorker>()
            .setInputData(workDataOf(
                AutoTranscriptionWorker.KEY_COURSE_ID to courseId,
                AutoTranscriptionWorker.KEY_MEDIA_PATH to mediaPath,
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)   // don't kill user's storage during STT
                    .build()
            )
            .addTag("auto-subtitle")
            .addTag("auto-subtitle-$courseId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "auto-subtitle-$courseId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
```

`REPLACE` means: if the user re-tries or re-imports, the in-flight job is cancelled and replaced. (REPLACE here is the **course-scoped** policy, not the global one — different courses never conflict.)

### 6.6 HiltWorkerFactory setup

`EchoLingApplication` is currently `@HiltAndroidApp class EchoLingApplication : Application()`. To make HiltWorkerFactory wire in, three changes:

1. Add `androidx.work:work-runtime-ktx:2.9.1` to `build.gradle.kts`.
2. Make `EchoLingApplication` implement `Configuration.Provider`:
   ```kotlin
   @HiltAndroidApp
   class EchoLingApplication : Application(), Configuration.Provider {
       @Inject lateinit var workerFactory: HiltWorkerFactory
       override val workManagerConfiguration: Configuration
           get() = Configuration.Builder()
               .setWorkerFactory(workerFactory)
               .build()
   }
   ```
3. Disable default WorkManager initialization in `AndroidManifest.xml`:
   ```xml
   <provider
       android:name="androidx.startup.InitializationProvider"
       android:authorities="${applicationId}.androidx-startup"
       android:exported="false"
       tools:node="merge">
       <meta-data
           android:name="androidx.work.WorkManagerInitializer"
           android:value="androidx.startup"
           tools:node="remove" />
   </provider>
   ```
   This pattern is the canonical Hilt-Worker wiring; documented in [androidx.work.WorkManager docs](https://developer.android.com/training/dependency-injection/workmanager#hilt).

---

## 7. UI flow

### 7.1 ImportScreen — "自动生成字幕" card

Visible **only when** `subtitleUri == null && (audioUri != null || videoUri != null)`. Place it directly under the subtitle FileSelectorCard, before the "导入素材" main button. Uses `surfaceVariant` background (matches the "难度选择" card at [ImportScreen.kt:247](app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt#L247) per §11.2 brand consistency).

```
┌────────────────────────────────────────────┐
│ ✨ 自动生成字幕                              │  ← titleSmall, FontWeight.SemiBold
│ 未检测到字幕文件,可用 Vosk 离线识别音频生成... │  ← bodySmall, onSurfaceVariant
│                                            │
│ ┌──────────────┐  ┌──────────────┐         │
│ │  立即转字幕   │  │  稍后转字幕   │         │  ← OutlinedButton + Filled Button
│ └──────────────┘  └──────────────┘         │
└────────────────────────────────────────────┘
```

When the user taps **立即转字幕**:
1. `ImportScreen` swaps the card content to a progress UI: a determinate `CircularProgressIndicator` + "正在识别中… 30%" + "预计还需 X 秒" (estimated from `(70 - currentProgress) / 70 * elapsed`).
2. The two buttons become disabled.
3. The main "导入素材" button is also disabled.
4. On completion: `ImportScreen.onImportComplete()` fires (same path as today), user lands in `CourseDetailScreen` with `subtitleUri` already set.

When the user taps **稍后转字幕**:
1. The course is inserted immediately (`autoSubtitleStatus = PENDING`).
2. The user lands in `CourseDetailScreen` showing "字幕识别中…" overlay (or in `CoursesScreen` listing the new course).

### 7.2 CourseListScreen — status chip

Each `CourseListItem` adds a status chip in the top-right area next to the title:

| State | Chip | Tap behavior |
|---|---|---|
| `null` (user subtitle) | (no chip) | (n/a) |
| `PENDING` | Spinner + "字幕待识别" | (disabled) |
| `IN_PROGRESS` | Spinner + "字幕识别中 X%" | (disabled) |
| `READY` | (no chip; `subtitleUri` is set, indistinguishable from user subtitle) | (n/a) |
| `FAILED` | Red warning + "字幕识别失败,点击重试" | Re-try dialog (§5.4) |

Cards with `autoSubtitleStatus ∈ {PENDING, IN_PROGRESS}` are **not clickable to enter Practice**. `CoursesScreen` disables the `onClick` and shows the chip as the only interactive element. `CompactConfirmDialog` (CLAUDE.md §12.15) handles the re-try confirm.

### 7.3 PracticeScreen — empty-subtitle state

If the user does enter a `PENDING` / `IN_PROGRESS` course (e.g. via Continue Learning deep link), `PracticeViewModel.loadSubtitles()` detects `subtitleUri == null && autoSubtitleStatus in (PENDING, IN_PROGRESS)` and emits `PracticeUiState.SubtitleNotReady(courseName, status)` instead of `LoadError`. The three tabs render a small "字幕正在识别中… 请稍后回来" card with a "返回课程列表" button. `FAILED` courses fall through to `LoadError` (different UX — user can re-try from the course list).

---

## 8. Edge cases & error handling

| Scenario | Worker behavior | DB state | UI state |
|---|---|---|---|
| Vosk model not yet decompressed | ModelManager loads from assets (synchronous on first call); no special handling needed — the existing `ModelManager.ensureModelReady()` already takes 1–3 s on cold start. | IN_PROGRESS until success | progress 0–5% |
| ffmpeg codec unsupported (e.g. ALAC, AMR) | `extractMono16kWav` returns `Result.failure(IllegalStateException("ffmpeg exit code -1"))` | FAILED with message | chip "字幕识别失败: 音频格式不支持" |
| WAV empty after ffmpeg | Same as above with message "音频提取失败,源文件无音轨" | FAILED | same |
| Vosk returns 0 segments (silent audio / music-only) | `segments` list is empty, `SrtSynthesizer.toSrt(emptyList())` returns "" | FAILED with "未识别到任何语音" | chip "字幕识别失败: 未识别到任何语音" |
| User process killed during worker | WorkManager re-launches. Worker reads `autoSubtitleProgress` from DB, skips completed steps. WAV in cacheDir may or may not be present (Android may GC); if missing, re-run ffmpeg. | resumes from checkpoint | chip continues from last persisted progress |
| App uninstall + reinstall | `cacheDir` and `filesDir/courses/` are wiped. Course in DB no longer has a valid `subtitleUri`. | (orphan courses; we don't auto-clean these — user can re-import) | chip "字幕识别失败" (because `subtitleUri` exists but file doesn't, on next open) |
| User kills the worker manually via `WorkManager.cancelAllByTag` | N/A — UI doesn't expose this. WorkManager may cancel on storage-low conditions (we use `setRequiresStorageNotLow(true)` to minimize). | (unchanged; progress stays at last value) | chip stays at last value; sweep on next app launch marks it FAILED |
| User imports the **same** audio file twice | Two courses with different `courseId`s; both workers run independently (each course has its own `auto-subtitle-<courseId>` tag). | independent | independent |
| `merge_close_segments`-style: 100+ short segments within 30 seconds | Vosk's endpoint detection already cuts at ~0.7 s silence — no Python-style merge pass needed (verified by reading `c:/Users/MING/myagent/echoling/scripts/generate_subtitles.py` merge logic; it's Python-Whisper specific and assumes different silence behavior). | n/a | n/a |
| File >3 hours | Worker rejects with "音频过长(超过 3 小时),请剪辑后重试" before ffmpeg runs. | FAILED with the above | chip shows the same |
| File 30 min–3 h | Worker runs. `ImportScreen` shows a `Snackbar` warning "音频较长(>30 分钟),识别可能需要 10 分钟以上" when **立即转字幕** is tapped. | IN_PROGRESS | progress UI |

---

## 9. Verification

### 9.1 Build

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The 16 KB alignment hooks (CLAUDE.md §12.33) run automatically on `packageDebug.doLast`. New `.so` entries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libffmpegkit.so`) from `ffmpeg-kit-min-gpl-6.0-2` are covered by the existing `patchNativeLibsFor16KB` + `repackApk16kb` pipeline. **First build after adding the dep will take longer (~3 min for ffmpeg-kit AAR extraction)**.

APK size: **+20–25 MB** (ffmpeg-kit-min-gpl 20 MB AAR + 2 MB of new Kotlin code + 1 MB resources). v1.0 was 84.8 MB → v1.1 ≈ 105–110 MB.

### 9.2 Unit tests (`./gradlew testDebugUnitTest`)

| Test file | Coverage |
|---|---|
| `SrtSynthesizerTest.kt` (new) | 12 tests covering: empty segments (0 cues); single segment (1 cue); multi-segment ordering; END_PAD_MS applied (boundary segment end + 400); max-words split (13-word segment → 2 cues); max-duration split (10-second segment → 2 cues); long-segment redistribute (15-word / 12-second → 3 cues); special characters (apostrophe, brackets, ampersand pass through); `formatTimestamp(0)`; `formatTimestamp(3661500)` = "01:01:01,500"; `formatTimestamp(99)` = "00:00:00,099"; concurrent segments with overlapping windows. |
| `AutoSubtitleStatusTest.kt` (new) | 4 tests covering: enum ↔ DB string round-trip for all 4 values; `null` ↔ `""` distinction; `hasAutoSubtitleIssue` (PENDING/IN_PROGRESS/FAILED return true; READY/null return false). |
| Existing `VoskSpeechRecognizerTest.kt` | None exists; Vosk requires native model loading and is hard to mock — defer to instrumentation tests in §9.3. |

Target: **16 new tests, 100% of `SrtSynthesizer` + `AutoSubtitleStatus` code paths covered**, ~5 s wall-clock.

### 9.3 Instrumentation / e2e (`./gradlew connectedDebugAndroidTest`)

The new worker is heavy to test at the JVM level (Hilt + WorkManager + ffmpeg native). Instead:

| Test ID | Manual (real device) | What to check |
|---|---|---|
| E1 | 30 s MP3 → 立即转 | progress 0 → 30 → 70 → 95 → 100; subtitle file written; `subtitleUri` set; `Practice → 泛听` shows the sentence at the right timestamp. |
| E2 | 3 min MP4 (HEVC+AAC) → 立即转 | ffmpeg drops video; Vosk runs on extracted audio; progress visible; result has ≥10 sentences. |
| E3 | 60 s MKV with DTS audio → 立即转 | Worker fails with "音频格式不支持 DTS"; chip shows the error; no crash. |
| E4 | 3 min MP3 → 稍后转 | Course appears in list with chip "字幕识别中 0%"; background worker runs; progress increments; `READY` chip disappears; subtitle file present. |
| E5 | 60 s silent MP3 → 立即转 | Worker fails with "未识别到任何语音"; chip "字幕识别失败" visible; re-try → same failure (deterministic). |
| E6 | Force-kill app mid-立即转 (3 min file at 30%) | Re-open app → WorkManager re-launches worker → progress resumes from 30% (or higher if Vosk already started). |
| E7 | Two courses imported with `稍后转` in quick succession | Both workers run **sequentially** (WorkManager default executor is serial). Verify no OOM. |

### 9.4 Real-device targets

- **Primary**: Xiaomi Mi 11 CN (CLAUDE.md §11 验证基准设备). Android 14, 16 KB page-size, 128 GB storage.
- **Secondary** (manual): Any Android 15+ device (validates 16 KB alignment under the new `libavcodec.so`).
- **Optional** (manual): Android 8 / API 26 — validates `minSdk = 26` still works with the new dependencies.

### 9.5 Acceptance criteria

- All 6 §9.2 unit tests pass.
- All 7 §9.3 e2e tests pass on Xiaomi Mi 11 CN.
- APK installs cleanly on Android 15+ (no `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`, no 16 KB warning from Android Studio).
- 16 KB alignment verification: `python scripts/repack_apk_16kb.py app/build/outputs/apk/debug/app-debug.apk` reports `(0 → 0 misaligned)` on second run.
- APK size ≤ 110 MB.
- `grep -r "INTERNET" app/src/main/AndroidManifest.xml` returns no matches (offline brand preserved).
- CLAUDE.md §9.3 R8 minification still works (release APK minified; the new `WorkManager` + Hilt annotations are properly retained by `proguard-rules.pro`).

---

## 10. Files

### Created (8 files)

| Path | Purpose |
|---|---|
| `app/src/main/java/com/echoling/app/transcription/FfmpegAudioExtractor.kt` | Wraps `FFmpegKit`, extracts mono 16 kHz WAV to `cacheDir/auto_subtitle/`. |
| `app/src/main/java/com/echoling/app/transcription/SrtSynthesizer.kt` | Pure Kotlin: `List<VoskSegment>` → `.srt` string. |
| `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionWorker.kt` | `@HiltWorker CoroutineWorker`, 4-step pipeline. |
| `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionScheduler.kt` | `@Singleton` facade for `enqueue` / `retry` / `cancel`. |
| `app/src/main/java/com/echoling/app/transcription/VoskSegment.kt` | New `data class` for Vosk's segment output. |
| `app/src/main/java/com/echoling/app/domain/model/AutoSubtitleStatus.kt` | Enum (`PENDING` / `IN_PROGRESS` / `READY` / `FAILED`). |
| `app/src/main/java/com/echoling/app/data/local/db/Migrations.kt` (or extend) | `MIGRATION_5_6` adding 3 columns. |
| `app/src/test/java/com/echoling/app/transcription/SrtSynthesizerTest.kt` | ~12 unit tests. |

### Modified (13 files)

| Path | Change |
|---|---|
| `app/build.gradle.kts` | Add `com.arthenica:ffmpeg-kit-min-gpl:6.0-2` + `androidx.work:work-runtime-ktx:2.9.1`. |
| `app/src/main/AndroidManifest.xml` | Disable default `WorkManager` init (per §6.6.3). |
| `app/src/main/java/com/echoling/app/EchoLingApplication.kt` | Implement `Configuration.Provider` + inject `HiltWorkerFactory`. |
| `app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt` | Add `transcribeFileWithSegments(wavPath)` method. |
| `app/src/main/java/com/echoling/app/data/local/db/entity/CourseEntity.kt` | Add 3 columns (§5.1). |
| `app/src/main/java/com/echoling/app/data/local/db/dao/CourseDao.kt` | Add 3 `@Query` methods: `updateAutoSubtitleStatus`, `updateAutoSubtitleProgress`, `markTranscriptionCompleted`. |
| `app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt` | Add `markTranscriptionStarted/Completed/Failed` + `updateTranscriptionProgress`. |
| `app/src/main/java/com/echoling/app/domain/model/Course.kt` | Add `autoSubtitleStatus: AutoSubtitleStatus?` + `autoSubtitleErrorMessage: String?` + `autoSubtitleProgress: Int`. |
| `app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt` | New "自动生成字幕" card (§7.1). |
| `app/src/main/java/com/echoling/app/presentation/viewmodel/ImportViewModel.kt` | `importCourseWithImmediateTranscription` + `importCourseWithDeferredTranscription`. |
| `app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CourseListItem.kt` | Status chip + disable-clickable-when-IN_PROGRESS (§7.2). |
| `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt` | Empty-subtitle state UI (§7.3). |
| `app/src/main/res/values/strings.xml` | 7 new strings (§8.5 字符串资源管理). |

### Untouched (explicitly)

- `SrtParser`, `Subtitle.kt`, `PracticeViewModel.loadSubtitles()`, `SentenceDao` — all consume `subtitleUri` regardless of who wrote the file.
- `SyncSentencesUseCase` — invoked by `AutoTranscriptionWorker.markTranscriptionCompleted` via the existing path.
- The three practice tabs (泛听 / 精听 / 测试) — their `subtitleUri == null` paths may need a single conditional branch in `PracticeViewModel`, but the rendering code is unchanged.
- iOS counterpart — out of scope; `c:/Users/MING/EchoLing-iOS` is independently maintained.

---

## 11. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | ffmpeg-kit-min-gpl **forces GPL on the entire APK** | High | Documented in README "已知限制"; user has already accepted this trade-off (consensus from brainstorming Section 2). If the store-side blocker turns out to be material, fallback is to ship a separate APK without auto-subtitle (`productFlavors`) — but that's a §12.X follow-up, not this iteration. |
| R2 | 16 KB alignment on ffmpeg-kit's `.so` files | High | Mitigated by the existing `patchNativeLibsFor16KB` + `repackApk16kb` pipeline (CLAUDE.md §12.33). Verified by `python scripts/repack_apk_16kb.py app-debug.apk` reporting `(0 → 0 misaligned)`. |
| R3 | OOM on 4 GB device for >2 h audio | Medium | Hard cap at 3 h; the ffmpeg temp WAV at 16 kHz mono = ~30 MB/h, so 3 h ≈ 90 MB temp WAV + Vosk 100 MB model + ffmpeg 50 MB runtime = 240 MB peak — fits 4 GB devices but tight. |
| R4 | Two parallel workers OOM | Medium | `enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE` per course; WorkManager's default executor is single-threaded, so different courses serialize naturally. Verified in E7. |
| R5 | `cacheDir/auto_subtitle/<courseId>.wav` accumulates | Low | Worker `finally` deletes the WAV on success or failure. App process death may leak one WAV per crash; mitigated by Android's cacheDir auto-GC under storage pressure. |
| R6 | `setWords(true)` costs +20% CPU vs `setWords(false)` | Low | Already accepted in §6.2 — we need the timestamps. |
| R7 | No cancellation UI | Low | Documented as YAGNI (§2 non-goal); 立即转 has natural progress visibility; 稍后转 can be "abandoned" by leaving the app (worker continues, eventually completes). |
| R8 | WorkManager init race during `Application.onCreate` | Low | The `Configuration.Provider` pattern (§6.6) is the canonical Hilt-Worker wiring; works because Hilt's `@HiltAndroidApp` initialization happens before `Application.onCreate` completes. |
| R9 | DB migration fails on real user data | Low | `MIGRATION_5_6` is pure `ALTER TABLE ADD COLUMN` — three nullable columns, no data movement. `fallbackToDestructiveMigration` (CLAUDE.md §9.2) stays as the safety net. Worst case: user loses their imported-courses list (acceptable; courses are recoverable from the source audio/video file). |
| R10 | Strings not migrated to `strings.xml` per CLAUDE.md §8.5 | Low | All 7 new user-visible strings added to `strings.xml` with Chinese + English values per §8.5 mandate; not hard-coded in composables. Listed in §10 "Modified files". |

---

## 12. Out of scope (explicit follow-ups, YAGNI)

- **SRT-to-bilingual translation** — would require either on-device translation (offline dictionary already covers some words but not full sentences) or a network translation API (forbidden by §12.26). Defer.
- **In-app SRT editor** — out of scope; users can re-import with a hand-edited SRT.
- **Replace Vosk small with `vosk-model-en-us-0.22-lgraph`** (129 MB) — accuracy trade-off; user rejected this in brainstorming Section 2.
- **Cancellation UI** — see §2 non-goal.
- **Batch import** — single-file only; multi-file import is a separate product decision.
- **Live-recording → SRT** — out of scope; the existing 跟读测试 flow already uses Vosk for short utterances; extending it to full session SRT is a different feature.
- **GPL relicensing mitigation** (R1) — `productFlavors` to ship a non-GPL variant is a follow-up if store review turns it down.

---

## 13. Open questions

None remaining from brainstorming. User approved all 6 sections on 2026-07-15.

---

## 14. Implementation notes

- **Commits**: 5 commits per the brainstorming Section 6.6 plan — (1) `chore(deps)`; (2) `feat(db)`; (3) `feat(transcription)`; (4) `feat(worker)`; (5) `feat(ui)`. Each commit compiles independently (`./gradlew assembleDebug` between commits).
- **CLAUDE.md §12.X entry** — after implementation, append a §12.43 or higher entry documenting: (a) ffmpeg-kit-min-gpl 6.0-2 integration; (b) WorkManager + HiltWorkerFactory wiring; (c) the new MIGRATION_5_6; (d) §11.5 brand-visual checklist updated for the new "自动生成字幕" card. Mirror the detail level of §12.19 / §12.26 / §12.33.
- **README.md** — append the "GPL notice" paragraph to "已知限制"; bump "11 个分类" line to "11 个分类 + 自动字幕生成"; add §功能概览 bullet for "自动字幕生成" with the offline-Vosk angle.
- **Store submission** — already covered by `docs/release/app-store-hardening.md`; no changes needed because we don't add new permissions.
- **APK signing** — release build uses existing `keystore/echoling.keystore` (CLAUDE.md §发布); `repack_apk_16kb.py` re-signs the release APK too (existing behavior from §12.33).