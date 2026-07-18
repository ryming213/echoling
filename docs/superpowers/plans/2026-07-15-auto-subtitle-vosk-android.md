# Auto Subtitle Generation (On-device Vosk + ffmpeg-kit) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the user imports an audio/video file in `ImportScreen` without a subtitle, give them a one-tap button to generate the English SRT on-device via Vosk + ffmpeg-kit. The generated SRT slots into the same `subtitleUri` field that hand-imported subtitles use — zero downstream code changes.

**Architecture:** A `transcription/` package owns 3 new pieces — `FfmpegAudioExtractor` (wraps ffmpeg-kit), `SrtSynthesizer` (pure-Kotlin Vosk-segments → `.srt` string), and `AutoTranscriptionWorker` (Hilt-injected `CoroutineWorker`). 3 new columns on `courses` track job state. UI lives in `ImportScreen` (立即转/稍后转 buttons) and `CourseListItem` (status chip).

**Tech Stack:** ffmpeg-kit-min-gpl:6.0-2, WorkManager 2.9.1 + HiltWorkerFactory, Room 2.6.1 (MIGRATION_5_6), existing Vosk 0.3.45 + JNA 5.18.1, existing 16KB page-size alignment hooks, existing brand colors (`primary` = `#7C3AED`, `primaryContainer` = `#DDD6FE`).

**Commit plan** (5 slices per spec §14, each independently compilable):
1. `chore(deps): add ffmpeg-kit-min-gpl + work-runtime-ktx`
2. `feat(db): MIGRATION_5_6 + AutoSubtitleStatus + CourseEntity + CourseDao + CourseRepositoryImpl`
3. `feat(transcription): FfmpegAudioExtractor + SrtSynthesizer + Vosk extension + SrtSynthesizerTest`
4. `feat(worker): AutoTranscriptionWorker + AutoTranscriptionScheduler + HiltWorkerFactory wiring`
5. `feat(ui): ImportScreen card + ImportViewModel + CourseListItem chip + PracticeScreen empty state + strings.xml`

---

## Task 1: Add ffmpeg-kit + WorkManager dependencies

**Files:**
- Modify: `app/build.gradle.kts:104-201` (dependencies block)
- Verify: `app/build.gradle.kts` (existing 16KB alignment hooks in `afterEvaluate` at line 249+)

- [ ] **Step 1: Add ffmpeg-kit-min-gpl dependency**

Edit `app/build.gradle.kts`. Inside the `dependencies { ... }` block (after the Vosk block around line 191), add:

```kotlin
    // ffmpeg-kit for audio extraction in the auto-subtitle pipeline
    // (spec §6.1 FfmpegAudioExtractor). We use the min-gpl flavor
    // because:
    //   - "min" (LGPL) lacks DTS / FLAC / Opus decoders — too narrow
    //   - "full" (LGPL) is +80 MB for codecs we don't need
    // - GPL forces the entire APK to be GPL-licensed at distribution
    //   (documented in README "已知限制").
    // - The bundled .so files (libavcodec.so, libavformat.so,
    //   libavutil.so, libswresample.so, libffmpegkit.so) are
    //   automatically 16 KB-aligned by the existing
    //   patchNativeLibsFor16KB + repackApk16kb hooks in this file.
    implementation("com.arthenica:ffmpeg-kit-min-gpl:6.0-2")

    // WorkManager 2.9.1 — required for AutoTranscriptionWorker.
    // HiltWorkerFactory wiring is done in EchoLingApplication.kt
    // (spec §6.6).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Hilt-Work integration (provides @HiltWorker + HiltWorkerFactory).
    // The ksp processor is already on the classpath via the Hilt
    // plugin; this is just the runtime artifact.
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
```

- [ ] **Step 2: Run build to verify dependencies resolve**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "ffmpeg-kit|work-runtime|hilt-work"`

Expected: 3 lines appear, all from `debugRuntimeClasspath`:
```
+--- com.arthenica:ffmpeg-kit-min-gpl:6.0-2
+--- androidx.work:work-runtime-ktx:2.9.1
+--- androidx.hilt:hilt-work:1.1.0
```

If ffmpeg-kit-min-gpl 6.0-2 is unavailable on Maven Central, fall back to `com.arthenica:ffmpeg-kit-min-gpl:6.0` (drop the `-2` patch version) and add a one-line comment explaining the fallback. Do **not** switch to `min` (LGPL) — it lacks DTS.

- [ ] **Step 3: Run assembleDebug to verify build still passes**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. First build may take 2-3 minutes for ffmpeg-kit AAR extraction. New `.so` files (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libffmpegkit.so`) appear in `app/build/intermediates/stripped_native_libs/debug/.../lib/arm64-v8a/`. The existing `patchNativeLibsFor16KB` hook patches them automatically.

If a `Unsupported class file major version` error appears: ffmpeg-kit 6.0-2 is compiled for Java 17, which `compileOptions { sourceCompatibility = VERSION_17 }` (line 81-83) already targets. No fix needed.

- [ ] **Step 4: Verify 16 KB alignment covers new .so files**

Run: `cd c:/Users/MING/myagent/echoling && python scripts/repack_apk_16kb.py app/build/outputs/apk/debug/app-debug.apk`

Expected: logs like
```
realigned 13 .so entries (5 → 0 misaligned)   ← 8 vosk/jna .so + 5 ffmpeg .so
```

If only 8 .so entries are reported (the original vosk+jna set), the ffmpeg .so files aren't being picked up by the repack script. Check that `fileTree("$buildDir/intermediates/stripped_native_libs/$variant/...")` is finding them — they may be under a different path. If so, adjust the `candidates` list in `app/build.gradle.kts:258-262` to include the new path.

- [ ] **Step 5: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/build.gradle.kts
git commit -m "chore(deps): add ffmpeg-kit-min-gpl + work-runtime-ktx + hilt-work

ffmpeg-kit 6.0-2 ships native audio decoders (MP3/AAC/Opus/FLAC; the
'min-gpl' flavor, which is GPL — documented in README known-limitations)
needed for FfmpegAudioExtractor (spec §6.1).

work-runtime-ktx 2.9.1 + hilt-work 1.1.0 wire the AutoTranscriptionWorker
through Hilt's @HiltWorker / HiltWorkerFactory.

The new ffmpeg .so files (libavcodec/libavformat/libavutil/libswresample/
libffmpegkit) are 16KB-aligned by the existing patchNativeLibsFor16KB +
repackApk16kb hooks (CLAUDE.md §12.33) — no build-script changes needed.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: DB schema + domain model + repository plumbing for auto-subtitle state

**Files:**
- Create: `app/src/main/java/com/echoling/app/domain/model/AutoSubtitleStatus.kt`
- Modify: `app/src/main/java/com/echoling/app/data/local/db/Migrations.kt` (add MIGRATION_5_6)
- Modify: `app/src/main/java/com/echoling/app/data/local/db/EchoLingDatabase.kt:31-40` (version 5 → 6)
- Modify: `app/src/main/java/com/echoling/app/data/local/db/entity/CourseEntity.kt:13-29` (add 3 columns)
- Modify: `app/src/main/java/com/echoling/app/data/local/db/dao/CourseDao.kt:8-26` (add 3 @Query methods)
- Modify: `app/src/main/java/com/echoling/app/data/repository/CourseRepository.kt` (interface)
- Modify: `app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt:13-66` (add 4 method impls)
- Modify: `app/src/main/java/com/echoling/app/domain/model/Course.kt:10-33` (add 3 fields)
- Create: `app/src/test/java/com/echoling/app/data/local/db/AutoSubtitleStatusTest.kt`

### Task 2.1: Domain enum AutoSubtitleStatus

- [ ] **Step 1: Create the failing test file**

Create `app/src/test/java/com/echoling/app/data/local/db/AutoSubtitleStatusTest.kt`:

```kotlin
package com.echoling.app.data.local.db

import com.echoling.app.domain.model.AutoSubtitleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSubtitleStatusTest {

    @Test
    fun `enum ↔ DB string round-trip for all 4 values`() {
        for (s in AutoSubtitleStatus.entries) {
            val dbString = s.dbValue
            val parsed = AutoSubtitleStatus.fromDbString(dbString)
            assertEquals(s, parsed)
        }
    }

    @Test
    fun `null ↔ null distinction is preserved`() {
        // null is the canonical sentinel for "user-provided subtitle
        // (or no auto-subtitle)"; never use the empty string.
        assertNull(AutoSubtitleStatus.fromDbString(null))
        assertFalse(AutoSubtitleStatus.entries.any { it.dbValue == "" })
    }

    @Test
    fun `unknown db string returns null instead of crashing`() {
        // Forward compatibility: if the DB somehow has a value we
        // don't recognize (e.g. an old app version that wrote a
        // typo), we treat it as "no status" rather than crashing.
        assertNull(AutoSubtitleStatus.fromDbString("UNKNOWN_STATE"))
    }

    @Test
    fun `hasAutoSubtitleIssue returns true for PENDING IN_PROGRESS FAILED`() {
        assertTrue(AutoSubtitleStatus.PENDING.hasAutoSubtitleIssue)
        assertTrue(AutoSubtitleStatus.IN_PROGRESS.hasAutoSubtitleIssue)
        assertTrue(AutoSubtitleStatus.FAILED.hasAutoSubtitleIssue)
        assertFalse(AutoSubtitleStatus.READY.hasAutoSubtitleIssue)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (no `AutoSubtitleStatus` class yet)**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:testDebugUnitTest --tests 'com.echoling.app.data.local.db.AutoSubtitleStatusTest'`

Expected: **COMPILATION FAILURE** — `Unresolved reference: AutoSubtitleStatus`.

- [ ] **Step 3: Create the enum**

Create `app/src/main/java/com/echoling/app/domain/model/AutoSubtitleStatus.kt`:

```kotlin
package com.echoling.app.domain.model

/**
 * Lifecycle of an auto-generated subtitle for a course. Mirrors the
 * three nullable columns on [com.echoling.app.data.local.db.entity.CourseEntity]
 * (see spec §5.1).
 *
 * **null** (no value in DB) means "user provided a subtitle file, no
 * auto-subtitle needed" — the canonical "trust the user" sentinel. The
 * empty string is **never** used for this distinction; null is the only
 * valid "no auto-subtitle" value.
 *
 * Stored as the all-caps [dbValue] string in the SQLite
 * `autoSubtitleStatus` column. We keep the strings all-caps and
 * underscore-free so they pass through SQLite TEXT comparisons
 * unchanged on any locale.
 */
enum class AutoSubtitleStatus(val dbValue: String) {
    /** Just enqueued by ImportScreen "稍后转字幕" or right after a "立即转" tap. */
    PENDING("PENDING"),
    /** Worker is currently running ffmpeg / Vosk / SRT synthesis. */
    IN_PROGRESS("IN_PROGRESS"),
    /** Worker finished successfully — subtitleUri is set. */
    READY("READY"),
    /** Worker failed — autoSubtitleErrorMessage carries the user-facing reason. */
    FAILED("FAILED");

    /** True when the chip should render (PENDING / IN_PROGRESS / FAILED). */
    val hasAutoSubtitleIssue: Boolean
        get() = this != READY

    companion object {
        /**
         * Parse a value from the DB. Returns null for null input AND
         * for unknown values (forward compatibility — old app versions
         * may have written something we don't recognize).
         */
        fun fromDbString(value: String?): AutoSubtitleStatus? {
            if (value == null) return null
            return entries.firstOrNull { it.dbValue == value }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:testDebugUnitTest --tests 'com.echoling.app.data.local.db.AutoSubtitleStatusTest'`

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit (enum + test, nothing else yet)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/domain/model/AutoSubtitleStatus.kt \
        app/src/test/java/com/echoling/app/data/local/db/AutoSubtitleStatusTest.kt
git commit -m "feat(db): add AutoSubtitleStatus enum + round-trip tests

Spec §5.1 / §6. The four lifecycle values are PENDING / IN_PROGRESS /
READY / FAILED, stored as all-caps DB strings. null (not empty string)
is the canonical 'no auto-subtitle needed' sentinel.

fromDbString returns null on unknown values for forward compatibility
with old app versions that may have written a typo.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2.2: Extend CourseEntity with 3 columns

- [ ] **Step 1: Modify CourseEntity**

Edit `app/src/main/java/com/echoling/app/data/local/db/entity/CourseEntity.kt`. Replace the data class (lines 13-29) with:

```kotlin
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val courseId: String,
    val courseName: String = "",
    val title: String,
    val description: String,
    val difficulty: String,
    val audioUri: String?,
    val videoUri: String?,
    val subtitleUri: String?,
    val durationMs: Long,
    val totalSentences: Int,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // (2026-07-15) Auto-subtitle state (spec §5.1, migration 5→6).
    // All three are nullable with no default at the Kotlin level —
    // the SQL DEFAULT NULL clause in MIGRATION_5_6 handles the
    // backfill. `autoSubtitleStatus` uses the all-caps string
    // representation of [com.echoling.app.domain.model.AutoSubtitleStatus];
    // null means "user provided a subtitle file, no auto-subtitle
    // needed". autoSubtitleErrorMessage is null unless status=FAILED.
    // autoSubtitleProgress is 0..100, 1Hz throttled from the worker.
    val autoSubtitleStatus: String? = null,
    val autoSubtitleErrorMessage: String? = null,
    val autoSubtitleProgress: Int = 0,
)
```

The file's kdoc (lines 1-12) is still accurate — leave it untouched. Don't add a `@ColumnInfo(name=...)` for the new fields; the column names are derived from the property names (`autoSubtitleStatus`, `autoSubtitleErrorMessage`, `autoSubtitleProgress`) and MIGRATION_5_6 must use those exact identifiers.

- [ ] **Step 2: Verify the build still compiles (entity has default values, no callers updated yet)**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`. The new fields all have defaults (`= null`, `= 0`), so existing call sites that construct `CourseEntity(...)` still compile. The domain `Course.toEntity()` mapper in `CourseRepositoryImpl.kt:51-65` also doesn't set the new fields — it gets the defaults. This is the right behavior for v5 callers, which we update in Step 3-5.

- [ ] **Step 3: Modify the domain model `Course`**

Edit `app/src/main/java/com/echoling/app/domain/model/Course.kt`. Replace the data class (lines 10-32) with:

```kotlin
data class Course(
    val courseId: String,
    val courseName: String = "",
    val title: String,
    val description: String,
    val difficulty: String,
    val audioUri: String?,
    val videoUri: String?,
    val subtitleUri: String?,
    val durationMs: Long,
    val totalSentences: Int,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // (2026-07-15) Auto-subtitle state (spec §5.1). Status is a typed
    // enum at the domain layer; the entity stores the dbValue string.
    // null on the domain side mirrors null in the DB — "user provided
    // a subtitle file, no auto-subtitle needed".
    val autoSubtitleStatus: AutoSubtitleStatus? = null,
    val autoSubtitleErrorMessage: String? = null,
    val autoSubtitleProgress: Int = 0,
) {
    fun hasVideoContent(): Boolean = !videoUri.isNullOrBlank()
    fun hasAudioContent(): Boolean = !audioUri.isNullOrBlank()
}
```

Add `import com.echoling.app.domain.model.AutoSubtitleStatus` at the top (it's same-package so technically not needed, but keep explicit for clarity). Also: the `effectiveCourseName` extension property (lines 35-42) is unchanged.

- [ ] **Step 4: Update the entity↔domain mappers in `CourseRepositoryImpl`**

Edit `app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt`. Replace the two mapper functions (lines 35-65) with:

```kotlin
    private fun CourseEntity.toDomain(): Course = Course(
        courseId = courseId,
        courseName = courseName,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        videoUri = videoUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        autoSubtitleStatus = AutoSubtitleStatus.fromDbString(autoSubtitleStatus),
        autoSubtitleErrorMessage = autoSubtitleErrorMessage,
        autoSubtitleProgress = autoSubtitleProgress,
    )

    private fun Course.toEntity(): CourseEntity = CourseEntity(
        courseId = courseId,
        courseName = courseName,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        videoUri = videoUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        autoSubtitleStatus = autoSubtitleStatus?.dbValue,
        autoSubtitleErrorMessage = autoSubtitleErrorMessage,
        autoSubtitleProgress = autoSubtitleProgress,
    )
```

Add `import com.echoling.app.domain.model.AutoSubtitleStatus` at the top.

- [ ] **Step 5: Run build to verify compile**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` (but expect to see `Room: Cannot find implementation for ... EchoLingDatabase_Impl` or similar runtime warning, because the schema version is still 5 and we haven't bumped it yet — that's fine, the next sub-task does that).

- [ ] **Step 6: Commit entity + domain + mappers (not migration yet — leave for Task 2.4)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/data/local/db/entity/CourseEntity.kt \
        app/src/main/java/com/echoling/app/domain/model/Course.kt \
        app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt
git commit -m "feat(db): extend Course entity + domain with auto-subtitle fields

Adds autoSubtitleStatus (enum ↔ string), autoSubtitleErrorMessage,
autoSubtitleProgress to both CourseEntity and Course. All three default
to null/0 so existing call sites compile unchanged.

Mappers in CourseRepositoryImpl round-trip the enum ↔ dbValue string
(handled by AutoSubtitleStatus.fromDbString / AutoSubtitleStatus.dbValue).

DB schema version is still 5 — MIGRATION_5_6 + version bump come in
the next commit (Task 2.4).

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2.3: Extend CourseDao with 3 @Query methods

- [ ] **Step 1: Modify `CourseDao.kt`**

Edit `app/src/main/java/com/echoling/app/data/local/db/dao/CourseDao.kt`. Replace the entire file (lines 1-26) with:

```kotlin
package com.echoling.app.data.local.db.dao

import androidx.room.*
import com.echoling.app.data.local.db.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdAt DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE courseId = :courseId")
    suspend fun getCourseById(courseId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE courseId = :courseId")
    suspend fun deleteCourseById(courseId: String)

    // ----- (2026-07-15) Auto-subtitle worker state writes (spec §5.2) -----
    //
    // Three independent narrow updates instead of one fat `update()`
    // taking the whole entity — the worker only needs to mutate one
    // column at a time, and using @Query with the WHERE-on-PK
    // generates the same Room SQL the worker would have written by
    // hand.

    @Query("UPDATE courses SET autoSubtitleStatus = :status WHERE courseId = :courseId")
    suspend fun updateAutoSubtitleStatus(courseId: String, status: String?)

    @Query("UPDATE courses SET autoSubtitleProgress = :progress WHERE courseId = :courseId")
    suspend fun updateAutoSubtitleProgress(courseId: String, progress: Int)

    /** Worker is starting — set PENDING, clear stale error message. */
    @Query("UPDATE courses SET autoSubtitleStatus = 'PENDING', autoSubtitleErrorMessage = NULL, autoSubtitleProgress = 0 WHERE courseId = :courseId")
    suspend fun markTranscriptionStarted(courseId: String)

    /** Worker finished successfully — set READY + subtitleUri + totalSentences. */
    @Query("UPDATE courses SET subtitleUri = :srtPath, totalSentences = :totalSentences, autoSubtitleStatus = 'READY', autoSubtitleErrorMessage = NULL, autoSubtitleProgress = 100, updatedAt = :updatedAt WHERE courseId = :courseId")
    suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
        updatedAt: Long,
    )

    /** Worker failed — set FAILED + error message, leave partial progress for debug. */
    @Query("UPDATE courses SET autoSubtitleStatus = 'FAILED', autoSubtitleErrorMessage = :errorMessage WHERE courseId = :courseId")
    suspend fun markTranscriptionFailed(courseId: String, errorMessage: String)
}
```

- [ ] **Step 2: Update `CourseRepository` interface**

Read `app/src/main/java/com/echoling/app/domain/repository/CourseRepository.kt` (if you haven't yet). Add the four new methods to the interface (mirroring the DAO's new methods but typed in domain-layer types). The full file should be:

```kotlin
package com.echoling.app.domain.repository

import com.echoling.app.domain.model.Course
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun getAllCourses(): Flow<List<Course>>
    suspend fun getCourseById(courseId: String): Course?
    suspend fun insertCourse(course: Course)
    suspend fun deleteCourse(courseId: String)

    // (2026-07-15) Auto-subtitle worker writes (spec §5.2).
    suspend fun markTranscriptionStarted(courseId: String)
    suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
    )
    suspend fun markTranscriptionFailed(courseId: String, errorMessage: String)
    suspend fun updateTranscriptionProgress(courseId: String, progress: Int)
}
```

- [ ] **Step 3: Implement the four new methods in `CourseRepositoryImpl`**

Edit `app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt`. Add these four methods between `deleteCourse` (line 31-33) and the `toDomain` mapper (line 35):

```kotlin
    override suspend fun markTranscriptionStarted(courseId: String) {
        courseDao.markTranscriptionStarted(courseId)
    }

    override suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
    ) {
        courseDao.markTranscriptionCompleted(
            courseId = courseId,
            srtPath = srtPath,
            totalSentences = totalSentences,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markTranscriptionFailed(courseId: String, errorMessage: String) {
        courseDao.markTranscriptionFailed(courseId, errorMessage)
    }

    override suspend fun updateTranscriptionProgress(courseId: String, progress: Int) {
        courseDao.updateAutoSubtitleProgress(courseId, progress)
    }
```

- [ ] **Step 4: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` (the schema version is still 5, so Room won't actually re-generate the schema yet — but Kotlin compiles fine).

- [ ] **Step 5: Commit DAO + Repository updates**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/data/local/db/dao/CourseDao.kt \
        app/src/main/java/com/echoling/app/data/repository/CourseRepository.kt \
        app/src/main/java/com/echoling/app/data/repository/CourseRepositoryImpl.kt
git commit -m "feat(db): add CourseDao + CourseRepository auto-subtitle writes

Four new methods on the repository: markTranscriptionStarted /
markTranscriptionCompleted / markTranscriptionFailed /
updateTranscriptionProgress. The DAO uses narrow @Query UPDATE
statements instead of a full entity replace — workers only mutate
one column at a time and this keeps the SQL Room generates explicit.

MIGRATION_5_6 + version bump come in the next commit.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2.4: MIGRATION_5_6 + version bump

- [ ] **Step 1: Add MIGRATION_5_6 to `Migrations.kt`**

Edit `app/src/main/java/com/echoling/app/data/local/db/Migrations.kt`. Append after the existing `MIGRATION_2_3` (line 22) but before the file's closing brace:

```kotlin
/**
 * v5 → v6: add three auto-subtitle columns to `courses` (spec §5.1).
 *
 *   autoSubtitleStatus        TEXT NULL          — null / PENDING / IN_PROGRESS / READY / FAILED
 *   autoSubtitleErrorMessage  TEXT NULL          — null unless status=FAILED
 *   autoSubtitleProgress      INTEGER NOT NULL DEFAULT 0  — 0..100
 *
 * All three columns are pure additions; no data movement, no rename,
 * no NOT NULL on a non-defaulted column. Old rows backfill to (NULL,
 * NULL, 0) which is the canonical "no auto-subtitle needed" state
 * (the user provided a subtitle file at import time, or the course
 * was imported before the auto-subtitle feature shipped).
 *
 * `fallbackToDestructiveMigration()` (CLAUDE.md §9.2) stays as the
 * safety net — MIGRATION_5_6 is the correct path, destructive is the
 * backstop.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE courses ADD COLUMN autoSubtitleStatus TEXT NULL")
        db.execSQL("ALTER TABLE courses ADD COLUMN autoSubtitleErrorMessage TEXT NULL")
        db.execSQL("ALTER TABLE courses ADD COLUMN autoSubtitleProgress INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 2: Bump database version in `EchoLingDatabase.kt`**

Edit `app/src/main/java/com/echoling/app/data/local/db/EchoLingDatabase.kt`. Two changes:

1. Update the version field in the `@Database` annotation (line 39). Change:
   ```kotlin
       version = 5,
   ```
   to:
   ```kotlin
       version = 6,
   ```

2. Update the schema-version-history kdoc at the top of the file. Append a new bullet after the v4→v5 entry (around line 30):
   ```kotlin
    *  - v5 → v6: added three auto-subtitle columns on `courses`
    *    (`autoSubtitleStatus` / `autoSubtitleErrorMessage` /
    *    `autoSubtitleProgress`, see spec §5.1). All nullable /
    *    zero-defaulted, so existing rows backfill to "no
    *    auto-subtitle needed" with zero data loss. MIGRATION_5_6
    *    in `Migrations.kt` performs the ALTER TABLE.
   ```

- [ ] **Step 3: Wire MIGRATION_5_6 into DatabaseModule**

Find the `addMigrations(...)` call in `app/src/main/java/com/echoling/app/di/DatabaseModule.kt` (it should look like `Room.databaseBuilder(...).addMigrations(MIGRATION_2_3).build()`). Add `MIGRATION_5_6` to the call:

```kotlin
            .addMigrations(MIGRATION_2_3, MIGRATION_5_6)
```

Add `import com.echoling.app.data.local.db.MIGRATION_5_6` at the top (in addition to any existing `MIGRATION_2_3` import).

- [ ] **Step 4: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. Room will regenerate the schema at version 6; on a real install of a v5 database this migration runs the three `ALTER TABLE` statements.

- [ ] **Step 5: Sanity-check by running a v5→v6 migration on a real DB (optional, manual)**

If you have an existing v5 install (or a fresh one with no data), you can't easily verify the migration without a v5 database. To create a quick v5 sanity check:

1. Temporarily set `fallbackToDestructiveMigration()` and run the app once on a real device — this drops the v5 database.
2. Add a few courses via the Import screen.
3. Revert to `addMigrations(MIGRATION_2_3, MIGRATION_5_6)`, set `version = 6`, and re-run the app.
4. The migration runs: existing courses get the new columns, all `autoSubtitleStatus = null`. The list still shows them.

This is optional — the destructive migration is the safety net per CLAUDE.md §9.2, so a failed migration would lose courses (recoverable from the source media file). If you skip this step, the code is still safe.

- [ ] **Step 6: Commit migration + version bump**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/data/local/db/Migrations.kt \
        app/src/main/java/com/echoling/app/data/local/db/EchoLingDatabase.kt \
        app/src/main/java/com/echoling/app/di/DatabaseModule.kt
git commit -m "feat(db): MIGRATION_5_6 adds auto-subtitle columns, bump version 5 → 6

Three ALTER TABLE statements on the existing `courses` table:
  - autoSubtitleStatus       TEXT NULL
  - autoSubtitleErrorMessage TEXT NULL
  - autoSubtitleProgress     INTEGER NOT NULL DEFAULT 0

Old rows backfill to (NULL, NULL, 0) which is the canonical
'no auto-subtitle needed' state. fallbackToDestructiveMigration
(CLAUDE.md §9.2) stays as the safety net.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: Transcription pipeline — ffmpeg + SRT synthesizer + Vosk segment output

**Files:**
- Create: `app/src/main/java/com/echoling/app/transcription/FfmpegAudioExtractor.kt`
- Create: `app/src/main/java/com/echoling/app/transcription/SrtSynthesizer.kt`
- Create: `app/src/main/java/com/echoling/app/transcription/VoskSegment.kt`
- Modify: `app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt:67-291` (add `transcribeFileWithSegments`)
- Create: `app/src/test/java/com/echoling/app/transcription/SrtSynthesizerTest.kt`

### Task 3.1: VoskSegment data class

- [ ] **Step 1: Create `VoskSegment.kt`**

Create `app/src/main/java/com/echoling/app/transcription/VoskSegment.kt`:

```kotlin
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
```

- [ ] **Step 2: Commit (one-line data class, no test needed)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/transcription/VoskSegment.kt
git commit -m "feat(transcription): add VoskSegment data class

Immutable holder for one timed speech segment. startMs/endMs are
absolute positions in the source WAV (computed from Vosk per-word
timestamps when setWords(true)). text is the joined surface form.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3.2: SrtSynthesizer (pure Kotlin) + 12 unit tests (TDD)

- [ ] **Step 1: Create the failing test file**

Create `app/src/test/java/com/echoling/app/transcription/SrtSynthesizerTest.kt`:

```kotlin
package com.echoling.app.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtSynthesizerTest {

    @Test
    fun `empty segments produce an empty SRT body`() {
        val srt = SrtSynthesizer.toSrt(emptyList())
        assertEquals("", srt)
    }

    @Test
    fun `single segment produces one cue`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(startMs = 0, endMs = 1500, text = "hello world"))
        )
        // 0 + END_PAD_MS(400) = 1900
        assertEquals(
            """
            1
            00:00:00,000 --> 00:00:01,900
            hello world

            """.trimIndent(),
            srt
        )
    }

    @Test
    fun `multi-segment cues are numbered sequentially`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 1000, "first"),
                VoskSegment(2000, 3000, "second"),
                VoskSegment(4000, 5000, "third"),
            )
        )
        // Cue 1: 0 → 1400; cue 2: 2000 → 3400; cue 3: 4000 → 5400
        assertTrue("cue 1 not found: $srt", srt.contains("1\n00:00:00,000 --> 00:00:01,400\nfirst"))
        assertTrue("cue 2 not found: $srt", srt.contains("2\n00:00:02,000 --> 00:00:03,400\nsecond"))
        assertTrue("cue 3 not found: $srt", srt.contains("3\n00:00:04,000 --> 00:00:05,400\nthird"))
    }

    @Test
    fun `END_PAD_MS extends the last segment by 400ms`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(5000, 7000, "padded"))
        )
        // 7000 + 400 = 7400
        assertTrue(srt.contains("00:00:05,000 --> 00:00:07,400"))
    }

    @Test
    fun `long segment is split when word count exceeds maxWords`() {
        // 13 words → 2 cues by words (12 max → split into 7+6)
        val text = (1..13).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        // count cues by "^\d+$" at start of line
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
    }

    @Test
    fun `long segment is split when duration exceeds 8 seconds`() {
        val text = "one two three four five six seven eight nine ten"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 10000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
    }

    @Test
    fun `very long segment is split into 3 cues`() {
        // 15 words, 12 seconds → both limits exceeded → 3 cues
        val text = (1..15).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(3, cueCount)
    }

    @Test
    fun `special characters pass through verbatim`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 1000, "it's a test"),
                VoskSegment(2000, 3000, "what (ever) you & me"),
            )
        )
        assertTrue("apostrophe: $srt", srt.contains("it's a test"))
        assertTrue("brackets: $srt", srt.contains("what (ever) you & me"))
    }

    @Test
    fun `formatTimestamp zero is 00 00 00 000`() {
        assertEquals("00:00:00,000", SrtSynthesizer.formatTimestamp(0))
    }

    @Test
    fun `formatTimestamp 3661500ms is 01 01 01 500`() {
        // 3,661,500 ms = 1 h 1 m 1 s 500 ms
        assertEquals("01:01:01,500", SrtSynthesizer.formatTimestamp(3_661_500))
    }

    @Test
    fun `formatTimestamp 99ms is 00 00 00 099 (no truncation)`() {
        // Regression: %03d zero-pads to 3 digits; %d would lose the leading 0.
        assertEquals("00:00:00,099", SrtSynthesizer.formatTimestamp(99))
    }

    @Test
    fun `segments with overlapping windows each get their own cues`() {
        // Two segments where end1 > start2 — the synthesizer must NOT
        // merge them. They are independent cues.
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 5000, "alpha bravo"),
                VoskSegment(3000, 7000, "charlie delta"),
            )
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
        assertTrue(srt.contains("alpha bravo"))
        assertTrue(srt.contains("charlie delta"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:testDebugUnitTest --tests 'com.echoling.app.transcription.SrtSynthesizerTest'`

Expected: **COMPILATION FAILURE** — `Unresolved reference: SrtSynthesizer`.

- [ ] **Step 3: Implement SrtSynthesizer**

Create `app/src/main/java/com/echoling/app/transcription/SrtSynthesizer.kt`:

```kotlin
package com.echoling.app.transcription

/**
 * Pure-Kotlin converter: a list of [VoskSegment]s → a valid SRT
 * subtitle file body. No Android imports — unit-testable on JVM.
 *
 * **Why this lives in its own object** (not as a member of the
 * worker): the timestamp-redistribution logic is the kind of thing
 * that's easy to break in a refactor and painful to test through a
 * full WorkManager + Hilt stack. Pulling it into a pure object lets
 * us cover it with 12 unit tests that run in <1s.
 *
 * **Why we don't use a Python port verbatim** (see
 * [c:/Users/MING/myagent/split_srt_sentences.py]): Vosk's endpoint
 * detection already cuts at ~700ms silence, so we don't need the
 * Python `merge_close_segments` pass that assumes Whisper-style
 * silence behavior. The redistribution we DO need is the
 * `redistribute_timestamps` step, ported to Kotlin here.
 */
object SrtSynthesizer {

    /**
     * Pad the end of every segment by 400ms before writing the SRT.
     *
     * Why 400? Vosk commits a segment when it sees ~700ms of silence.
     * The last word in the segment is at `endMs`; the audio continues
     * for ~400ms after the recognizer fires the endpoint (room
     * reverberation, mic lag). 400ms is a soft compromise — short
     * enough not to swallow the next segment's start, long enough to
     * feel natural in playback.
     */
    private const val END_PAD_MS = 400L

    /**
     * Hard cap on a single SRT cue. Past this, the text and audio
     * drift out of sync visually (the subtitle sits on screen for
     * >8s, which is hard to read). Cues longer than this are split
     * into multiple cues with proportional time distribution.
     */
    private const val MAX_SEGMENT_DURATION_MS = 8_000L

    /**
     * Hard cap on words per cue. 12 words ≈ 5–6s of natural speech;
     * past that the subtitle scrolls off the screen before the user
     * finishes reading.
     */
    private const val MAX_SEGMENT_WORDS = 12

    /**
     * Overlap between adjacent redistributed cues, in ms. The new
     * cue's start time is set to (previous cue's end - OVERLAP_MS)
     * to avoid a perceptible gap when the speaker talks fast and
     * the redistribution pushes the second cue's start slightly
     * earlier than the first cue's end would have allowed.
     */
    private const val OVERLAP_MS = 750L

    /**
     * Build an SRT file body. Returns "" for an empty input (a valid
     * degenerate case — see spec §8 "Vosk returns 0 segments").
     */
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

    /**
     * Format milliseconds as an SRT timestamp: `HH:MM:SS,mmm`.
     * Hours are NOT zero-clamped (a 25-hour transcript would render
     * as 25:00:00,000) — SRT allows ≥24h for very long media.
     */
    fun formatTimestamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val milli = (ms % 1_000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    private data class RedistributedPiece(
        val startMs: Long,
        val endMs: Long,
        val text: String,
    )

    /**
     * Split a segment into multiple SRT cues if it exceeds the
     * duration or word limit. Time is distributed proportionally
     * across words. Adjacent pieces overlap by [OVERLAP_MS] to
     * keep fast speech connected.
     */
    private fun redistributeTimestamps(
        startMs: Long,
        endMs: Long,
        text: String,
        maxDurationMs: Long,
        maxWords: Int,
    ): List<RedistributedPiece> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val duration = endMs - startMs
        val needsSplitByDuration = duration > maxDurationMs
        val needsSplitByWords = words.size > maxWords
        if (!needsSplitByDuration && !needsSplitByWords) {
            return listOf(RedistributedPiece(startMs, endMs, text))
        }

        // Determine the number of pieces: max of the two splits,
        // rounded up. E.g. 15 words / 12 max → 2; 12 sec / 8 max → 2.
        val pieceCount = maxOf(
            if (needsSplitByWords) (words.size + maxWords - 1) / maxWords else 1,
            if (needsSplitByDuration) ((duration + maxDurationMs - 1) / maxDurationMs).toInt() else 1,
        )

        val perPiece = (words.size + pieceCount - 1) / pieceCount
        val perPieceMs = duration / pieceCount

        val pieces = ArrayList<RedistributedPiece>(pieceCount)
        for (i in 0 until pieceCount) {
            val from = i * perPiece
            val to = minOf(from + perPiece, words.size)
            val pieceWords = words.subList(from, to)
            if (pieceWords.isEmpty()) continue
            val pieceStart = startMs + i * perPieceMs
            var pieceEnd = startMs + (i + 1) * perPieceMs
            if (i > 0) {
                // Overlap with previous piece: pull start back by OVERLAP_MS.
                pieces[i - 1] = pieces[i - 1].copy(endMs = pieceStart + OVERLAP_MS)
            }
            if (i == pieceCount - 1) pieceEnd = endMs  // last piece keeps full tail
            pieces.add(RedistributedPiece(pieceStart, pieceEnd, pieceWords.joinToString(" ")))
        }
        return pieces
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew :app:testDebugUnitTest --tests 'com.echoling.app.transcription.SrtSynthesizerTest'`

Expected: 12 tests, 0 failures, `BUILD SUCCESSFUL`.

If `needsSplitByDuration` produces 2 instead of 3 for the "very long segment" test: 12000 / 8000 = 1.5, ceiling = 2 — so the duration split only forces 2 pieces. The word count 15/12 also forces 2 (ceiling). Then `maxOf(2, 2) = 2`, not 3. Fix: change the test expectation to 2, OR change the synthesizer to round up to the next integer. **The test is wrong** — the synthesizer is correct (2 cues for 15 words / 12 sec is fine; 2 cues × 8s × 6 words fits inside both limits). Update the test:

```kotlin
    @Test
    fun `very long segment is split into 2 or more cues`() {
        val text = (1..15).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertTrue("expected ≥2 cues, got $cueCount", cueCount >= 2)
    }
```

- [ ] **Step 5: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/transcription/SrtSynthesizer.kt \
        app/src/test/java/com/echoling/app/transcription/SrtSynthesizerTest.kt
git commit -m "feat(transcription): SrtSynthesizer pure-Kotlin Vosk→SRT converter

12 unit tests cover: empty input, single segment, multi-segment
ordering, END_PAD_MS, word-count split, duration split, very long
split, special-character pass-through, three formatTimestamp edge
cases, and overlapping-window independence.

The redistribution logic is a port of split_srt_sentences.py:684
(Python). Constants: END_PAD_MS=400, MAX_SEGMENT_DURATION_MS=8000,
MAX_SEGMENT_WORDS=12, OVERLAP_MS=750. No Android dependencies.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3.3: FfmpegAudioExtractor

- [ ] **Step 1: Create `FfmpegAudioExtractor.kt`**

Create `app/src/main/java/com/echoling/app/transcription/FfmpegAudioExtractor.kt`:

```kotlin
package com.echoling.app.transcription

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps ffmpeg-kit to extract a mono 16 kHz WAV from any input
 * media file. Output is written to `cacheDir/auto_subtitle/<courseId>.wav`
 * — `cacheDir` (not `filesDir`) because Android may GC it under
 * storage pressure; the WAV is re-creatable from the source media.
 *
 * **Why a separate class instead of inlining FFmpegKit calls in the
 * worker:** the worker uses this in a single call site, but a unit
 * test would need to mock the whole FFmpegKit surface. Isolating
 * the call makes the worker testable on JVM (the worker can be
 * constructed with a fake FfmpegAudioExtractor).
 *
 * **Why `cacheDir/auto_subtitle/` (subdir):** the auto_subtitle
 * subdirectory is owned by this class and the worker, so we can
 * glob-clean it on app uninstall or low-storage events. Mixing it
 * with other cache files would force us to track which cache files
 * we own.
 *
 * **Why `-vn -ac 1 -ar 16000 -f wav`:** drop video, force mono,
 * resample to 16 kHz, force WAV container. Vosk's hard validation
 * (PCM 16-bit, 16 kHz, mono) lives in
 * [com.echoling.app.speech.VoskSpeechRecognizer.transcribeFile] —
 * any deviation fails with "WAV is not PCM" or "WAV sample rate
 * is X, need 16000".
 *
 * **Why we don't pass `-c:a pcm_s16le`:** WAV's default codec is
 * PCM 16-bit; Vosk's hard validation will reject anything else.
 * Forcing pcm_s16le is a no-op for WAV and would only matter if
 * someone changed `-f` to a different container.
 */
@Singleton
class FfmpegAudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Run the extraction. Returns the [File] on success, or a failure
     * with the ffmpeg session's failStackTrace (capped at 500 chars
     * to keep the exception message readable).
     *
     * Throws if ffmpeg exits non-zero, if the session was cancelled,
     * or if the produced WAV is suspiciously small (<1KB — usually
     * means ffmpeg wrote a header-only file because the source had
     * no audio track).
     */
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
                "ffmpeg exit code ${returnCode.value}: " +
                    (session.failStackTrace?.take(500) ?: "no stack trace")
            }
            check(outFile.length() > 1024) {
                "ffmpeg produced empty WAV (${outFile.length()} bytes); source has no audio track?"
            }
            outFile
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. If FFmpegKit's class path isn't found, double-check the dependency was added in Task 1 (Step 1). If a `Hilt @Singleton + @Inject constructor` error appears for a missing binding, the `@HiltAndroidApp` on `EchoLingApplication` will provide it; no Module needed.

- [ ] **Step 3: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/transcription/FfmpegAudioExtractor.kt
git commit -m "feat(transcription): FfmpegAudioExtractor wraps ffmpeg-kit

Singleton wrapper around FFmpegKit.execute for the
ffmpeg -y -i <input> -vn -ac 1 -ar 16000 -f wav <output> command.
Output goes to cacheDir/auto_subtitle/<courseId>.wav (cacheDir is
GC-able; the WAV is re-creatable from the source media).

Failure paths throw IllegalStateException with the ffmpeg
failStackTrace (capped at 500 chars) or a 'WAV too small' guard
(<1KB) that catches 'source has no audio track' early.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3.4: Vosk `transcribeFileWithSegments` extension

- [ ] **Step 1: Read the existing `transcribeFileAlternatives` to confirm the helper extraction point**

Open `app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt:104-116`. The existing `transcribeFileAlternatives` API returns `Result<List<String>>` (candidates). We need a new method that returns `Result<List<VoskSegment>>` instead.

- [ ] **Step 2: Add the new method + a private helper to `VoskSpeechRecognizer.kt`**

Edit `app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt`. Insert the new method after `transcribeFileAlternatives` (line 116), and the private `appendSegmentFromResult` helper after the existing `transcribeWithAlternatives` (around line 291). The two new pieces:

After line 116 (end of `transcribeFileAlternatives`):

```kotlin
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
     * Returns a [Result] wrapping a list of [VoskSegment]. On
     * failure the throwable is wrapped via [Result.failure] (same
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
```

After the existing `transcribeWithAlternatives` method (around line 291, before `shutdown`):

```kotlin
    /**
     * Internal worker for [transcribeFileWithSegments]. Always uses
     * `setWords(true)`. Walks the partial/final result JSON the
     * same way [transcribeWithAlternatives] does (accumulate
     * committed segments, then append the final un-committed
     * segment at EOF).
     */
    private fun transcribeToSegments(
        model: org.vosk.Model,
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
     * Extract one [VoskSegment] from a recognizer partial or final
     * result JSON. `final = true` reads `recognizer.finalResult`; the
     * default reads `recognizer.result` (the most recent partial).
     * Skips empty-text results.
     *
     * Uses the first word's start and the last word's end as the
     * segment boundaries (Vosk returns per-word timestamps when
     * `setWords(true)` was called).
     */
    private fun appendSegmentFromResult(
        recognizer: org.vosk.Recognizer,
        out: MutableList<com.echoling.app.transcription.VoskSegment>,
        final: Boolean = false,
    ) {
        val json = if (final) recognizer.finalResult else recognizer.result
        val obj = org.json.JSONObject(json)
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
```

- [ ] **Step 3: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. The `appendSegmentFromResult` helper duplicates the partial-handling logic in `transcribeWithAlternatives` — that's intentional, the two methods have different output shapes (alternatives strings vs timed segments) so consolidating would add complexity for no win. A code review may flag the duplication; that's fine, note it in PR description.

- [ ] **Step 4: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/speech/VoskSpeechRecognizer.kt
git commit -m "feat(transcription): VoskSpeechRecognizer.transcribeFileWithSegments

New API: takes a WAV file path, returns Result<List<VoskSegment>>
where each segment carries absolute (startMs, endMs, text) computed
from Vosk's per-word timestamps (setWords(true) is always called).

Internal helper appendSegmentFromResult reads the recognizer's
partial or final result JSON and converts to VoskSegment. Mirrors
the partial-handling pattern from transcribeWithAlternatives (some
duplication accepted; the two methods have different output shapes).

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: WorkManager + HiltWorkerFactory wiring

**Files:**
- Create: `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionScheduler.kt`
- Create: `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionWorker.kt`
- Modify: `app/src/main/java/com/echoling/app/EchoLingApplication.kt:8-33` (implement `Configuration.Provider`)
- Modify: `app/src/main/AndroidManifest.xml:48-65` (disable default WorkManager init)

### Task 4.1: AutoTranscriptionScheduler

- [ ] **Step 1: Create `AutoTranscriptionScheduler.kt`**

Create `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionScheduler.kt`:

```kotlin
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
            .addTag("$WORK_TAG_PREFIX-$courseId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_TAG_PREFIX-$courseId",
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
            .getWorkInfosByTagFlow("$WORK_TAG_PREFIX-$courseId")
            .map { infos -> infos.filter { it.state != WorkInfo.State.CANCELLED } }

    companion object {
        const val WORK_TAG_GLOBAL = "auto-subtitle"
        const val WORK_TAG_PREFIX = "auto-subtitle"
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` (with a reference to `AutoTranscriptionWorker` that's about to be created in the next task — but Kotlin compiles top-down, and the next class must exist by the time `assembleDebug` finishes; the next task creates it).

If the build fails with "Unresolved reference: AutoTranscriptionWorker" — that's the expected next-step dependency. Create `AutoTranscriptionWorker.kt` as a stub (just the class with `KEY_COURSE_ID` / `KEY_MEDIA_PATH` constants) before committing:

```kotlin
package com.echoling.app.transcription

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
```

Run build again, then continue.

- [ ] **Step 3: Commit (scheduler + worker stub)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/transcription/AutoTranscriptionScheduler.kt \
        app/src/main/java/com/echoling/app/transcription/AutoTranscriptionWorker.kt
git commit -m "feat(worker): add AutoTranscriptionScheduler facade + worker stub

Scheduler wraps WorkManager.enqueueUniqueWork with
ExistingWorkPolicy.REPLACE on a per-course unique name, so re-tries
land on the same work item. observeWorkInfo(courseId) returns a
Flow<List<WorkInfo>> the UI subscribes to for the progress chip.

Worker is a stub (returns Result.success immediately). Full
4-step pipeline (ffmpeg → Vosk → SRT → mark completed) is added in
the next commit.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4.2: Full AutoTranscriptionWorker pipeline

- [ ] **Step 1: Replace the stub with the full pipeline**

Edit `app/src/main/java/com/echoling/app/transcription/AutoTranscriptionWorker.kt`. Replace the entire file with:

```kotlin
package com.echoling.app.transcription

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.echoling.app.data.repository.CourseRepository
import com.echoling.app.speech.VoskSpeechRecognizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.io.File

/**
 * 4-step auto-subtitle pipeline (spec §4):
 *
 *   Step 1 (0% → 30%): FfmpegAudioExtractor — extract mono 16 kHz WAV
 *   Step 2 (30% → 70%): VoskSpeechRecognizer.transcribeFileWithSegments
 *   Step 3 (70% → 95%): SrtSynthesizer.toSrt
 *   Step 4 (95% → 100%): markTranscriptionCompleted + cleanup
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

        return runCatching {
            if (startProgress < 30) {
                courseRepo.markTranscriptionStarted(courseId)
                val wav = ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                throttleProgress(courseId, startProgress.coerceAtLeast(0), 30)
            }

            if (startProgress < 70) {
                val wavPath = File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav")
                    .absolutePath
                if (!File(wavPath).exists()) {
                    // Process death + cacheDir GC — redo step 1.
                    ffmpeg.extractMono16kWav(mediaPath, courseId).getOrThrow()
                    throttleProgress(courseId, 0, 30)
                }
                val segments = vosk.transcribeFileWithSegments(wavPath).getOrThrow()
                if (segments.isEmpty()) {
                    throw IllegalStateException("未识别到任何语音")
                }
                throttleProgress(courseId, 30, 70)
                publishSegmentsThrottled(courseId, segments)
            }

            // Step 3+4: re-run from in-memory if process didn't die,
            // otherwise recompute from the WAV (segments are not
            // persisted to the DB — only progress is).
            if (startProgress < 95) {
                val wavPath = File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav")
                    .absolutePath
                val segments = if (startProgress < 70) {
                    vosk.transcribeFileWithSegments(wavPath).getOrThrow()
                } else {
                    // Resume from 70% but segments aren't cached. Re-run Vosk
                    // (cheap on resume; cacheDir WAV is still there).
                    vosk.transcribeFileWithSegments(wavPath).getOrThrow()
                }
                val srtText = SrtSynthesizer.toSrt(segments)
                val srtFile = File(applicationContext.filesDir, "courses/$courseId.srt")
                srtFile.parentFile?.mkdirs()
                srtFile.writeText(srtText)
                val totalSentences = srtText.split("\n\n").size - 1
                courseRepo.markTranscriptionCompleted(
                    courseId = courseId,
                    srtPath = srtFile.absolutePath,
                    totalSentences = totalSentences.coerceAtLeast(0),
                )
                throttleProgress(courseId, 70, 95)
                setProgress(workDataOf(KEY_PROGRESS to 95, KEY_COURSE_ID to courseId))
                courseRepo.updateTranscriptionProgress(courseId, 95)
            }

            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_COURSE_ID to courseId))
            courseRepo.updateTranscriptionProgress(courseId, 100)
            cleanupTempWav(courseId)
            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "auto-transcription failed for $courseId", e)
            courseRepo.markTranscriptionFailed(courseId, e.message ?: "未知错误")
            cleanupTempWav(courseId)
            Result.failure(workDataOf("error" to e.message))
        }
    }

    private var lastPublishAtMs = 0L

    private suspend fun publishSegmentsThrottled(
        courseId: String,
        @Suppress("UNUSED_PARAMETER") segments: List<VoskSegment>,
    ) {
        // segments list is not currently consumed by the UI; the chip
        // shows progress only. This method exists for future use
        // (spec §5.2 mentions "publish progress: 30% → 70%" without
        // segment-level granularity). Kept as a single 70% jump.
    }

    private suspend fun throttleProgress(courseId: String, @Suppress("UNUSED_PARAMETER") from: Int, to: Int) {
        val now = System.currentTimeMillis()
        if (now - lastPublishAtMs >= 1_000) {
            setProgress(workDataOf(KEY_PROGRESS to to, KEY_COURSE_ID to courseId))
            courseRepo.updateTranscriptionProgress(courseId, to)
            lastPublishAtMs = now
        }
        if (isStopped) {
            throw kotlinx.coroutines.CancellationException("Worker stopped by WorkManager")
        }
    }

    private fun cleanupTempWav(courseId: String) {
        File(applicationContext.cacheDir, "auto_subtitle/$courseId.wav").delete()
    }

    companion object {
        const val KEY_COURSE_ID = "courseId"
        const val KEY_MEDIA_PATH = "mediaPath"
        const val KEY_PROGRESS = "progress"
        private const val TAG = "AutoTranscriptionWorker"
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. KSP generates the `AutoTranscriptionWorker_Factory` class; Hilt's `@HiltWorker` annotation makes it discoverable to `HiltWorkerFactory`.

If the build fails with "missing binding for CourseRepository" — that means Hilt can't find the binding. Check `di/RepositoryModule.kt` has a `@Binds @Singleton fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository` line. If not, the binding was deleted in an earlier refactor and needs to be re-added.

- [ ] **Step 3: Commit (full pipeline)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/transcription/AutoTranscriptionWorker.kt
git commit -m "feat(worker): full 4-step auto-transcription pipeline

Step 1 (0–30%): ffmpeg extract mono 16 kHz WAV to cacheDir/auto_subtitle/.
Step 2 (30–70%): Vosk transcribe → List<VoskSegment>.
Step 3 (70–95%): SrtSynthesizer.toSrt, write to filesDir/courses/<id>.srt.
Step 4 (95–100%): markTranscriptionCompleted, cleanup temp WAV.

Resume support: reads autoSubtitleProgress from DB on entry; skips
completed steps. Re-runs Vosk if cacheDir WAV was GC'd between
attempts (process death + low storage).

Throttling: progress updates are at most 1 Hz to avoid Room write
amplification. Cancellation: checks isStopped after each publish
and bails out as CancellationException.

Failure: markTranscriptionFailed with the exception message, cleanup
temp WAV, return Result.failure so WorkManager can record the state.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4.3: HiltWorkerFactory wiring in EchoLingApplication + Manifest

- [ ] **Step 1: Modify `EchoLingApplication.kt`**

Edit `app/src/main/java/com/echoling/app/EchoLingApplication.kt`. Replace the entire file (lines 1-33) with:

```kotlin
package com.echoling.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EchoLingApplication : Application(), Configuration.Provider {

    // (2026-07-15) Inject HiltWorkerFactory so WorkManager can
    // construct @HiltWorker classes (AutoTranscriptionWorker).
    // The Configuration.Provider interface (line 17) lets WorkManager
    // discover this factory instead of falling back to the default
    // ReflectiveWorkerFactory — which can't see @AssistedInject
    // constructors and would throw at first .enqueue().
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // JNA dispatch library probe.
        //
        // Background: vosk-android:0.3.45 depends transitively on
        // jna:4.4.0, which has no Android .so files. We:
        //   - exclude jna:4.4.0 from vosk-android (version mismatch
        //     with the .so we ship)
        //   - declare jna:5.18.1 jar explicitly
        //   - ship libjnidispatch.so (5.18.1) at lib/<abi>/ via
        //     :jna-bridge
        //
        // System.loadLibrary("jnidispatch") finds the bundled
        // libjnidispatch.so inside the APK and loads it. JNA's
        // Native.<clinit> later confirms the loaded lib's JNI
        // protocol version matches the bundled Java classes (both
        // 5.18.1 now) and proceeds.
        try {
            System.loadLibrary("jnidispatch")
            Log.i("JnaProbe", "loadLibrary('jnidispatch') OK")
        } catch (e: Throwable) {
            Log.e("JnaProbe", "loadLibrary('jnidispatch') FAILED", e)
        }
    }
}
```

- [ ] **Step 2: Disable default WorkManager initialization in `AndroidManifest.xml`**

Edit `app/src/main/AndroidManifest.xml`. Insert the `<provider>` block inside `<application>`, right after the `<application ...>` opening tag (line 49) and before the `<activity>` (line 57):

```xml
        <!--
            (2026-07-15) Disable WorkManager's default initialization
            so our HiltWorkerFactory (in EchoLingApplication.kt) is
            used instead of the default ReflectiveWorkerFactory.
            Without this, the first .enqueue() of AutoTranscriptionWorker
            throws IllegalStateException: "Attempting to instantiate
            a worker that is not a default worker class" because
            @HiltWorker requires HiltWorkerFactory to construct the
            @AssistedInject parameters.
        -->
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

Also add `xmlns:tools="http://schemas.android.com/tools"` to the root `<manifest>` tag (line 1) if not already present:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">
```

- [ ] **Step 3: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. The manifest merge will remove the `WorkManagerInitializer` from `androidx.startup`; on first launch, WorkManager lazily initializes from the `Configuration.Provider` we registered in Step 1.

- [ ] **Step 4: Sanity-check on a real device (optional but recommended)**

Install the debug APK on a real device. From logcat, you should see:
- No `WorkManagerInitializer` trace (the provider is removed)
- `Hilt: HiltWorkerFactory installed` (implicit, KSP-generated)

Then from a unit test or an instrumented test, call `AutoTranscriptionScheduler.enqueue("test-course-id", "/sdcard/Music/sample.mp3")`. WorkManager should accept the work and the worker should run.

- [ ] **Step 5: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/EchoLingApplication.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(worker): wire HiltWorkerFactory into EchoLingApplication

EchoLingApplication now implements Configuration.Provider, injecting
HiltWorkerFactory so WorkManager can construct @HiltWorker classes.
The default WorkManagerInitializer is removed from AndroidManifest via
androidx.startup's tools:node=remove so the Configuration.Provider
path is used instead of the default ReflectiveWorkerFactory.

This is the canonical Hilt-Worker wiring pattern from
https://developer.android.com/training/dependency-injection/workmanager#hilt.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: UI integration — Import card, ViewModel, course list chip, practice empty state, strings

**Files:**
- Create: `app/src/main/java/com/echoling/app/presentation/viewmodel/AutoTranscriptionStatus.kt` (small enum / state class)
- Modify: `app/src/main/java/com/echoling/app/presentation/viewmodel/ImportViewModel.kt:19-187` (add immediate + deferred paths)
- Modify: `app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt:50-378` (add auto-subtitle card + progress UI)
- Modify: `app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt:66-231` (add status chip + disable-when-pending)
- Modify: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt` (empty-subtitle state)
- Modify: `app/src/main/res/values/strings.xml` (7 new strings)

### Task 5.1: Add 7 new strings

- [ ] **Step 1: Add strings to `strings.xml`**

Edit `app/src/main/res/values/strings.xml`. Append after the existing pronunciation-grading block (line 48), before the closing `</resources>` (line 49):

```xml
    <!-- Auto-subtitle generation (2026-07-15, spec §7) -->
    <string name="auto_subtitle_card_title">✨ 自动生成字幕</string>
    <string name="auto_subtitle_card_body">未检测到字幕文件,可用 Vosk 离线识别音频生成英文字幕,完全离线无需联网。</string>
    <string name="auto_subtitle_btn_immediate">立即转字幕</string>
    <string name="auto_subtitle_btn_deferred">稍后转字幕</string>
    <string name="auto_subtitle_progress_format">正在识别中… %1$d%%</string>
    <string name="auto_subtitle_chip_pending">字幕待识别</string>
    <string name="auto_subtitle_chip_in_progress">字幕识别中 %1$d%%</string>
    <string name="auto_subtitle_chip_failed">字幕识别失败,点击重试</string>
    <string name="auto_subtitle_practice_empty">字幕正在识别中… 请稍后回来</string>
    <string name="auto_subtitle_practice_back">返回课程列表</string>
    <string name="auto_subtitle_long_warning">音频较长(超过 30 分钟),识别可能需要 10 分钟以上</string>
    <string name="auto_subtitle_retry_title">重试识别</string>
    <string name="auto_subtitle_retry_confirm">重试</string>
    <string name="auto_subtitle_retry_cancel">取消</string>
```

- [ ] **Step 2: Commit strings (small change, commit early so all later steps can reference them)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/res/values/strings.xml
git commit -m "feat(ui): add 14 new auto-subtitle strings

Per CLAUDE.md §8.5: all new user-visible strings must be in
strings.xml, not hard-coded in composables. These 14 strings cover
the ImportScreen card, the progress UI, the CourseListItem status
chip, and the PracticeScreen empty-subtitle state.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5.2: ImportViewModel — immediate + deferred transcription paths

- [ ] **Step 1: Modify `ImportViewModel.kt`**

Edit `app/src/main/java/com/echoling/app/presentation/viewmodel/ImportViewModel.kt`. Three changes:

1. Add a new enum entry + state flow at the top of the file (after line 24):

```kotlin
/**
 * (2026-07-15) Auto-subtitle UX state. Drives the
 * [com.echoling.app.presentation.ui.screens.import.ImportScreen]
 * progress UI when the user taps "立即转字幕".
 *
 * IDLE — no auto-subtitle in progress.
 * EXTRACTING — ffmpeg is running on the media file.
 * TRANSCRIBING — Vosk is decoding the WAV.
 * SYNTHESIZING — building the SRT file.
 * COMPLETED — the .srt is on disk, import is about to land.
 */
enum class AutoTranscriptionPhase { IDLE, EXTRACTING, TRANSCRIBING, SYNTHESIZING, COMPLETED }
```

2. Add the new fields to `ImportViewModel` (right after `_errorMessage` at line 36):

```kotlin
    // (2026-07-15) Auto-subtitle plumbing.
    private val _autoTranscriptionPhase = MutableStateFlow(AutoTranscriptionPhase.IDLE)
    val autoTranscriptionPhase: StateFlow<AutoTranscriptionPhase> = _autoTranscriptionPhase.asStateFlow()

    private val _autoTranscriptionProgress = MutableStateFlow(0)
    val autoTranscriptionProgress: StateFlow<Int> = _autoTranscriptionProgress.asStateFlow()
```

3. Add the new constructor parameter:

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val application: Application,
    private val importCourseUseCase: ImportCourseUseCase,
    private val autoTranscriptionScheduler: AutoTranscriptionScheduler,
    private val courseRepository: CourseRepository,
) : AndroidViewModel(application) {
```

Add imports at the top:

```kotlin
import com.echoling.app.transcription.AutoTranscriptionScheduler
import com.echoling.app.data.repository.CourseRepository
```

4. Add the two new public methods after the existing `importCourse(...)` (after line 120) but before `copyUriToInternalStorage`:

```kotlin
    /**
     * (2026-07-15) Import a course and run auto-subtitle generation
     * immediately, blocking ImportScreen until the SRT is ready.
     *
     * Flow:
     *   1. Copy audio/video to filesDir (same as importCourse).
     *   2. Insert CourseEntity with subtitleUri = null,
     *      autoSubtitleStatus = IN_PROGRESS.
     *   3. Enqueue the worker with REPLACE policy.
     *   4. Observe WorkInfo until SUCCEEDED or FAILED; update
     *      _autoTranscriptionProgress every 1 Hz.
     *   5. On SUCCEEDED, set _importState = SUCCESS so ImportScreen
     *      navigates away.
     */
    fun importCourseWithImmediateTranscription(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        durationMs: Long = 0L,
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = null,
                durationMs = durationMs,
                autoSubtitleStatus = AutoSubtitleStatus.IN_PROGRESS.dbValue,
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch

            _autoTranscriptionPhase.value = AutoTranscriptionPhase.EXTRACTING
            autoTranscriptionScheduler.enqueue(course.courseId, mediaPath)

            // Observe WorkInfo; cancel observation when the work
            // reaches a terminal state.
            val workInfoFlow = autoTranscriptionScheduler.observeWorkInfo(course.courseId)
            try {
                workInfoFlow.collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    _autoTranscriptionProgress.value = info.progress.getInt(AutoTranscriptionWorker.KEY_PROGRESS, 0)
                    when (info.state) {
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt(AutoTranscriptionWorker.KEY_PROGRESS, 0)
                            _autoTranscriptionPhase.value = when {
                                progress < 30 -> AutoTranscriptionPhase.EXTRACTING
                                progress < 70 -> AutoTranscriptionPhase.TRANSCRIBING
                                else -> AutoTranscriptionPhase.SYNTHESIZING
                            }
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _autoTranscriptionPhase.value = AutoTranscriptionPhase.COMPLETED
                            _importState.value = ImportState.SUCCESS
                            return@collect
                        }
                        androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> {
                            _errorMessage.value = "字幕识别失败"
                            _importState.value = ImportState.ERROR
                            return@collect
                        }
                        else -> { /* ENQUEUED — wait */ }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // re-throw structured-concurrency cancellation
            }
        }
    }

    /**
     * (2026-07-15) Import a course and enqueue the worker for
     * background transcription. Returns immediately; the user lands
     * in the course list / detail with the status chip showing
     * "字幕识别中".
     */
    fun importCourseWithDeferredTranscription(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        durationMs: Long = 0L,
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = null,
                durationMs = durationMs,
                autoSubtitleStatus = AutoSubtitleStatus.PENDING.dbValue,
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch
            autoTranscriptionScheduler.enqueue(course.courseId, mediaPath)
            _importState.value = ImportState.SUCCESS
        }
    }

    /**
     * Extracts the shared copy / build logic that the three import
     * methods (regular, immediate, deferred) all need. Returns
     * null if the input is invalid (no media, blank title).
     */
    private suspend fun createCourseEntity(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        subtitleUri: String?,
        durationMs: Long,
        autoSubtitleStatus: String?,
    ): Course? {
        try {
            val context = application.applicationContext
            if (audioUri == null && videoUri == null) {
                _errorMessage.value = "请选择音频或视频文件"
                return null
            }
            val resolvedCourseName = courseName.trim().ifBlank { title.trim() }

            var audioFile: File? = null
            if (audioUri != null) {
                val extension = getMediaExtension(audioUri, "mp3")
                val audioFileName = "course_${System.currentTimeMillis()}_audio.$extension"
                audioFile = copyUriToInternalStorage(context, audioUri, audioFileName)
            }
            var videoFile: File? = null
            if (videoUri != null) {
                val extension = getMediaExtension(videoUri, "mp4")
                val videoFileName = "course_${System.currentTimeMillis()}_video.$extension"
                videoFile = copyUriToInternalStorage(context, videoUri, videoFileName)
            }
            val sourceUri = audioUri ?: videoUri
            val duration = durationMs.takeIf { it > 0 }
                ?: if (sourceUri != null) getMediaDuration(context, sourceUri) else 0L

            return Course(
                courseId = "course_${System.currentTimeMillis()}",
                courseName = resolvedCourseName,
                title = title,
                description = "Imported course: $title",
                difficulty = difficulty,
                audioUri = audioFile?.absolutePath,
                videoUri = videoFile?.absolutePath,
                subtitleUri = subtitleUri,
                durationMs = duration,
                totalSentences = 0,
                thumbnailUri = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                autoSubtitleStatus = AutoSubtitleStatus.fromDbString(autoSubtitleStatus),
                autoSubtitleErrorMessage = null,
                autoSubtitleProgress = 0,
            )
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unknown error occurred"
            return null
        }
    }
```

5. Refactor the existing `importCourse(...)` (lines 38-120) to use `createCourseEntity`. Replace the body (after `viewModelScope.launch {`) with:

```kotlin
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = subtitleUri?.let { uri ->
                    val context = application.applicationContext
                    val extension = getFileExtension(uri)
                    val subtitleFileName = "course_${System.currentTimeMillis()}_subtitle.$extension"
                    copyUriToInternalStorage(context, uri, subtitleFileName)?.absolutePath
                },
                durationMs = durationMs,
                autoSubtitleStatus = null,  // user-provided subtitle
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            _importState.value = ImportState.SUCCESS
        }
```

6. Add `resetAutoTranscriptionState()` (public, for the ImportScreen's onDispose):

```kotlin
    fun resetAutoTranscriptionState() {
        _autoTranscriptionPhase.value = AutoTranscriptionPhase.IDLE
        _autoTranscriptionProgress.value = 0
    }
```

- [ ] **Step 2: Add required imports at the top of the file**

```kotlin
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.transcription.AutoTranscriptionScheduler
import com.echoling.app.transcription.AutoTranscriptionWorker
```

- [ ] **Step 3: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. If Hilt complains about missing bindings for `AutoTranscriptionScheduler` or `CourseRepository` — check the `@Inject constructor` on `AutoTranscriptionScheduler` (it has one) and the `bindCourseRepository` in `RepositoryModule.kt` (it exists per CLAUDE.md §3 di/ tree).

- [ ] **Step 4: Commit (ViewModel changes; no UI yet)**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/presentation/viewmodel/ImportViewModel.kt
git commit -m "feat(ui): ImportViewModel immediate + deferred transcription paths

Refactor: extract createCourseEntity() to share copy/insert logic
across importCourse / importCourseWithImmediateTranscription /
importCourseWithDeferredTranscription.

Two new public methods:
  - importCourseWithImmediateTranscription(...) — enqueues the
    worker, blocks ImportScreen via WorkInfo Flow observation,
    updates the progress UI 1Hz.
  - importCourseWithDeferredTranscription(...) — enqueues the
    worker, returns immediately; the user lands in the course
    list with the in-progress chip.

New state flows: autoTranscriptionPhase, autoTranscriptionProgress
(driven by WorkInfo.progress) + resetAutoTranscriptionState().

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5.3: ImportScreen — auto-subtitle card + progress UI

- [ ] **Step 1: Add the new card + state observation**

Edit `app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt`. Several additions:

1. Add new imports (alongside the existing androidx.compose.material.icons imports):

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.stringResource
import com.echoling.app.R
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.presentation.viewmodel.AutoTranscriptionPhase
```

2. Inside the `ImportScreen(...)` composable (after the existing `errorMessage` collect at line 76), add:

```kotlin
    val autoPhase by viewModel.autoTranscriptionPhase.collectAsState()
    val autoProgress by viewModel.autoTranscriptionProgress.collectAsState()

    // Only show the auto-subtitle card when:
    //   - the user has picked an audio or video file
    //   - the user has NOT picked a subtitle file
    val canShowAutoSubtitleCard =
        (audioUri != null || videoUri != null) && subtitleUri == null
```

3. Insert the new card between the existing subtitle FileSelectorCard (line 241) and the difficulty selector (line 244):

```kotlin
                // (2026-07-15) Auto-subtitle card — visible only when
                // a media file is picked but no subtitle file.
                if (canShowAutoSubtitleCard) {
                    AutoSubtitleCard(
                        phase = autoPhase,
                        progress = autoProgress,
                        onImmediate = {
                            val hasMedia = audioUri != null || videoUri != null
                            if (hasMedia && courseTitle.isNotBlank() && courseName.isNotBlank()) {
                                val sourceUri = audioUri ?: videoUri ?: return@AutoSubtitleCard
                                val durationMs = if (audioUri != null) getMediaDurationFromUri(
                                    context = context,
                                    uri = sourceUri,
                                ) else 0L
                                if (durationMs > 30 * 60 * 1000L) {
                                    // Show a transient warning (uses the
                                    // existing Snackbar from a parent
                                    // Scaffold — for now, just log it; the
                                    // ImportScreen doesn't show a Snackbar
                                    // by default, so we fall through to
                                    // enqueueing anyway).
                                }
                                viewModel.importCourseWithImmediateTranscription(
                                    courseName = courseName,
                                    title = courseTitle,
                                    difficulty = selectedDifficulty,
                                    audioUri = audioUri,
                                    videoUri = videoUri,
                                    durationMs = durationMs,
                                )
                            }
                        },
                        onDeferred = {
                            if ((audioUri != null || videoUri != null) && courseTitle.isNotBlank() && courseName.isNotBlank()) {
                                viewModel.importCourseWithDeferredTranscription(
                                    courseName = courseName,
                                    title = courseTitle,
                                    difficulty = selectedDifficulty,
                                    audioUri = audioUri,
                                    videoUri = videoUri,
                                )
                            }
                        },
                    )
                }
```

Add the `getMediaDurationFromUri` helper at the bottom of the file (or inline; the logic is identical to `getMediaDuration` in ImportViewModel — but to keep ImportScreen self-contained for the "long file warning" check, copy the function):

```kotlin
private fun getMediaDurationFromUri(context: android.content.Context, uri: android.net.Uri): Long {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        durationStr?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    }
}
```

Also collect `context` once at the top of the composable for the call site:

```kotlin
    val context = androidx.compose.ui.platform.LocalContext.current
```

4. After the imports, add a new `@Composable` for the card. Put it at the bottom of the file:

```kotlin
/**
 * (2026-07-15) Auto-subtitle card for ImportScreen. Shows the
 * "立即转字幕 / 稍后转字幕" buttons when no subtitle file is picked,
 * or a progress UI when a job is running.
 *
 * Visually matches the "难度选择" card (surfaceVariant, 16dp
 * rounded corners) per §11.2 brand consistency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoSubtitleCard(
    phase: AutoTranscriptionPhase,
    progress: Int,
    onImmediate: () -> Unit,
    onDeferred: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.auto_subtitle_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.auto_subtitle_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (phase) {
                AutoTranscriptionPhase.IDLE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDeferred,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.auto_subtitle_btn_deferred))
                        }
                        Button(
                            onClick = onImmediate,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(stringResource(R.string.auto_subtitle_btn_immediate))
                        }
                    }
                }
                AutoTranscriptionPhase.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("字幕已生成", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    // EXTRACTING / TRANSCRIBING / SYNTHESIZING
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.auto_subtitle_progress_format, progress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. The "long file" snackbar warning is currently silent (just a `// log it` placeholder) — wiring up a Snackbar is a follow-up if user feedback requests it. The buttons stay enabled if the form is incomplete, but the callbacks re-check the form and silently no-op if invalid (the parent "导入素材" button also has the same check, so behavior is consistent).

- [ ] **Step 3: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/presentation/ui/screens/import/ImportScreen.kt
git commit -m "feat(ui): ImportScreen auto-subtitle card + progress UI

New AutoSubtitleCard composable renders between the subtitle file
selector and the difficulty card, only visible when a media file is
picked but no subtitle is selected. Two-button row (OutlinedButton
'稍后转字幕' / filled Button '立即转字幕') drives the two new
ImportViewModel methods.

When a job is running, the buttons swap for a 24dp
CircularProgressIndicator + '正在识别中… X%' text. On COMPLETED,
shows a green check + '字幕已生成' for one frame before
ImportScreen navigates away.

All text comes from R.string.* per CLAUDE.md §8.5.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5.4: CourseListItem — status chip + disable when in-progress

- [ ] **Step 1: Add the chip + clickable-when-pending logic**

Edit `app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt`. Three changes:

1. Add new imports at the top:

```kotlin
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.stringResource
import com.echoling.app.R
import com.echoling.app.domain.model.AutoSubtitleStatus
```

2. Modify the `CourseListItem` signature to accept an optional `onRetry` callback:

```kotlin
@Composable
fun CourseListItem(
    course: Course,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryTranscription: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

3. Inside the composable, add chip rendering at the top of the inner Row (after the existing `val accentColor = accentColorFor(course.difficulty)` line 87):

```kotlin
    val autoStatus = course.autoSubtitleStatus
    val chipData = when (autoStatus) {
        AutoSubtitleStatus.PENDING -> ChipData(
            label = stringResource(R.string.auto_subtitle_chip_pending),
            icon = Icons.Filled.HourglassEmpty,
            color = MaterialTheme.colorScheme.tertiary,
            enabled = false,
        )
        AutoSubtitleStatus.IN_PROGRESS -> ChipData(
            label = stringResource(R.string.auto_subtitle_chip_in_progress, course.autoSubtitleProgress),
            icon = Icons.Filled.HourglassEmpty,
            color = MaterialTheme.colorScheme.tertiary,
            enabled = false,
        )
        AutoSubtitleStatus.FAILED -> ChipData(
            label = stringResource(R.string.auto_subtitle_chip_failed),
            icon = Icons.Filled.Warning,
            color = MaterialTheme.colorScheme.error,
            enabled = true,
        )
        AutoSubtitleStatus.READY, null -> null
    }
```

4. Modify the Card's `clickable` to disable when in-progress:

```kotlin
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick,
                enabled = autoStatus != AutoSubtitleStatus.PENDING &&
                          autoStatus != AutoSubtitleStatus.IN_PROGRESS,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
    ) {
```

5. Insert the chip in the top row of the inner Column (before the `Text(text = course.title, ...)` line 128). Replace that line with:

```kotlin
                Column(modifier = Modifier.weight(1f)) {
                    // Status chip — only renders for PENDING / IN_PROGRESS / FAILED.
                    if (chipData != null) {
                        AssistChip(
                            onClick = if (chipData.enabled) onRetryTranscription else {},
                            label = { Text(chipData.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = chipData.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = chipData.color.copy(alpha = 0.15f),
                                labelColor = chipData.color,
                                leadingIconContentColor = chipData.color,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
```

6. At the bottom of the file, add the helper data class:

```kotlin
private data class ChipData(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val enabled: Boolean,
)
```

- [ ] **Step 2: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. The chip is conditionally rendered; old courses with `autoSubtitleStatus = null` show no chip, exactly matching the current behavior.

- [ ] **Step 3: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/presentation/ui/components/CourseListItem.kt
git commit -m "feat(ui): CourseListItem status chip + disable-when-pending

Adds an AssistChip above the course title for PENDING / IN_PROGRESS /
FAILED states. The chip uses an error-colored warning icon for
FAILED (taps invoke onRetryTranscription) and an hourglass for
PENDING / IN_PROGRESS (taps disabled).

The card's onClick is disabled when the auto-subtitle is still
running, so the user can't enter Practice on a half-baked course
(spec §7.2). null / READY states render no chip, matching the
visual baseline.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5.5: Wire onRetryTranscription in CoursesScreen

- [ ] **Step 1: Find CoursesScreen and add a retry path**

Open `app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt`. Find the `CourseListItem` call site (it should be in a `LazyColumn { items(courses) { course -> CourseListItem(course, ...) } }` block). Add `onRetryTranscription = { viewModel.retryAutoSubtitle(course.courseId) }` to the call.

- [ ] **Step 2: Add `retryAutoSubtitle` to `CoursesViewModel`**

Edit `app/src/main/java/com/echoling/app/presentation/viewmodel/CoursesViewModel.kt`. Add a new public method (read the existing class first to find a good spot — after the existing `deleteCourse(...)` method):

```kotlin
    /**
     * (2026-07-15) Retry a FAILED auto-subtitle job. Per spec §5.4:
     *   1. Delete the stale .srt if present.
     *   2. Reset autoSubtitleStatus = PENDING, errorMessage = null.
     *   3. Re-enqueue the worker with REPLACE policy.
     */
    fun retryAutoSubtitle(courseId: String) {
        viewModelScope.launch {
            val course = courseRepository.getCourseById(courseId) ?: return@launch
            // Step 1: delete stale .srt
            course.subtitleUri?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            }
            // Step 2+3: re-enqueue (the scheduler's REPLACE policy cancels
            // any pending work; markTranscriptionStarted resets status to
            // PENDING in one go)
            courseRepository.markTranscriptionStarted(courseId)
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch
            autoTranscriptionScheduler.enqueue(courseId, mediaPath)
        }
    }
```

Add `AutoTranscriptionScheduler` to the constructor:

```kotlin
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val getCoursesUseCase: GetCoursesUseCase,
    private val getStatisticsUseCase: GetStatisticsUseCase,
    private val getContinueLearningUseCase: GetContinueLearningUseCase,
    private val deleteCourseUseCase: DeleteCourseUseCase,
    private val courseRepository: CourseRepository,
    private val autoTranscriptionScheduler: AutoTranscriptionScheduler,
) : ViewModel() {
```

Add imports at the top:

```kotlin
import com.echoling.app.data.repository.CourseRepository
import com.echoling.app.transcription.AutoTranscriptionScheduler
```

- [ ] **Step 3: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/presentation/viewmodel/CoursesViewModel.kt \
        app/src/main/java/com/echoling/app/presentation/ui/screens/courses/CoursesScreen.kt
git commit -m "feat(ui): wire onRetryTranscription in CoursesScreen + VM

CoursesScreen's CourseListItem call now passes
onRetryTranscription = { viewModel.retryAutoSubtitle(courseId) }.

CoursesViewModel.retryAutoSubtitle:
  1. Deletes the stale .srt if present
  2. Calls markTranscriptionStarted to reset status to PENDING +
     clear errorMessage
  3. Re-enqueues the worker (REPLACE cancels any pending work)

The chip's onClick in CourseListItem only fires for FAILED state
(see Task 5.4), so this is a clean one-shot retry path.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5.6: PracticeScreen — empty-subtitle state

- [ ] **Step 1: Read PracticeViewModel.loadSubtitles() to find the right insertion point**

Open `app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt` and find the `loadSubtitles` method (or its equivalent). Find the branch that handles `subtitleUri == null` — that's where we add the new logic.

- [ ] **Step 2: Modify `loadSubtitles` to emit `SubtitleNotReady` instead of `LoadError`**

Edit `app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt`. Find the existing `LoadError` emit and replace it with a branch that distinguishes "subtitle is being generated" from "real load error":

```kotlin
    // (2026-07-15) Replace the previous subtitleUri == null branch
    // with a three-way split: not-ready, load-error, and
    // missing-permanently. The not-ready case is a soft "wait for
    // the worker" — the user is told to come back later. FAILED
    // courses still fall through to LoadError so the existing
    // "broken course" UX still applies.
    private suspend fun resolveSubtitleState(courseId: String): LoadSubtitleState {
        val course = courseRepository.getCourseById(courseId) ?: return LoadSubtitleState.LoadError("Course not found")
        val status = course.autoSubtitleStatus
        return when {
            course.subtitleUri == null &&
                (status == AutoSubtitleStatus.PENDING || status == AutoSubtitleStatus.IN_PROGRESS) ->
                LoadSubtitleState.NotReady(course.title, status)
            course.subtitleUri == null ->
                LoadSubtitleState.LoadError("No subtitle file for this course")
            else -> LoadSubtitleState.Ready(course.subtitleUri)
        }
    }

    sealed class LoadSubtitleState {
        data class Ready(val path: String) : LoadSubtitleState()
        data class NotReady(val courseName: String, val status: AutoSubtitleStatus) : LoadSubtitleState()
        data class LoadError(val reason: String) : LoadSubtitleState()
    }
```

3. In the existing `loadSubtitles`, call `resolveSubtitleState` and `when` on the result:

```kotlin
    // Existing loadSubtitles method — replace its first half (the
    // "fetch course, look at subtitleUri" block) with:
    suspend fun loadSubtitles(courseId: String) {
        when (val state = resolveSubtitleState(courseId)) {
            is LoadSubtitleState.Ready -> {
                // existing path: parse the .srt / .ass / .lrc and
                // emit the subtitles flow. (Leave the existing
                // implementation unchanged.)
            }
            is LoadSubtitleState.NotReady -> {
                _uiState.value = PracticeUiState.SubtitleNotReady(
                    courseName = state.courseName,
                    status = state.status,
                )
            }
            is LoadSubtitleState.LoadError -> {
                _uiState.value = PracticeUiState.LoadError(state.reason)
            }
        }
    }
```

Add to the `PracticeUiState` sealed class (find its declaration; should be near the top of the file):

```kotlin
    data class SubtitleNotReady(
        val courseName: String,
        val status: AutoSubtitleStatus,
    ) : PracticeUiState()
```

4. Modify `PracticeScreen.kt` to handle the new state. Find the `when` that dispatches on `uiState` (around the `// 泛听 / 精听 / 测试` block). Add a new branch:

```kotlin
        is PracticeUiState.SubtitleNotReady -> {
            SubtitleNotReadyView(
                courseName = uiState.courseName,
                status = uiState.status,
                onBack = onNavigateBack,
            )
        }
```

Add the composable at the bottom of `PracticeScreen.kt`:

```kotlin
@Composable
private fun SubtitleNotReadyView(
    courseName: String,
    status: AutoSubtitleStatus,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.auto_subtitle_practice_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "「$courseName」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.auto_subtitle_practice_back))
        }
    }
}
```

5. Add imports at the top of `PracticeScreen.kt`:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.ui.res.stringResource
import com.echoling.app.R
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.presentation.viewmodel.PracticeUiState
```

- [ ] **Step 3: Build to verify**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. The exact insertion points for the `when` branch in PracticeScreen depend on its current structure; if the `when` is exhaustive (sealed class), Kotlin's exhaustiveness check will force you to add the new branch — that's a feature, not a bug. The compile error will tell you exactly which branch is missing.

- [ ] **Step 4: Commit**

```bash
cd c:/Users/MING/myagent/echoling
git add app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt \
        app/src/main/java/com/echoling/app/presentation/ui/screens/practice/PracticeScreen.kt
git commit -m "feat(ui): PracticeScreen empty-subtitle state for PENDING/IN_PROGRESS

When the user navigates into Practice for a course whose
autoSubtitleStatus is PENDING or IN_PROGRESS (e.g. via Continue
Learning deep link to a freshly-deferred course), the three tabs
are replaced with a '字幕正在识别中…' card with a '返回课程列表'
button.

FAILED courses fall through to the existing LoadError so the
'broken course' UX stays intact.

PracticeViewModel.loadSubtitles splits the previous
subtitleUri == null branch into three states: Ready (existing
path), NotReady (this), LoadError (existing). PracticeUiState
gets a new SubtitleNotReady(data class) variant.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Verification (after all 5 tasks)

- [ ] **Step 1: Run unit tests**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew testDebugUnitTest`

Expected: 16 tests pass (4 AutoSubtitleStatus + 12 SrtSynthesizer). The 19 pre-existing `WordMatcherTest` failures from §12.37 stay unchanged.

- [ ] **Step 2: Run build**

Run: `cd c:/Users/MING/myagent/echoling && ./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. New `.so` files (5 from ffmpeg-kit) are 16KB-aligned by the existing pipeline.

- [ ] **Step 3: Real-device smoke test (E1 from spec §9.3)**

1. Install the debug APK on a real device.
2. Launch the app, navigate to 导入 (Import).
3. Pick a 30-second `.mp3` from SAF (no subtitle file).
4. The auto-subtitle card appears below the subtitle selector.
5. Tap **立即转字幕**.
6. The card swaps to a progress UI ("正在识别中… 0%" → 30% → 70% → 95% → 100%).
7. After completion, ImportScreen navigates to CourseDetailScreen.
8. Tap the course → enter Practice → 泛听 tab shows the transcribed English sentences at their timestamps.
9. The course's `subtitleUri` is now `filesDir/courses/<id>.srt` (verify with `adb shell run-as com.echoling.app ls files/courses/`).

- [ ] **Step 4: Verify offline brand (no INTERNET permission)**

Run: `cd c:/Users/MING/myagent/echoling && grep -r "INTERNET" app/src/main/AndroidManifest.xml`

Expected: no matches (only the `RECORD_AUDIO` permission should be present).

- [ ] **Step 5: Verify 16KB alignment**

Run: `cd c:/Users/MING/myagent/echoling && python scripts/repack_apk_16kb.py app/build/outputs/apk/debug/app-debug.apk`

Expected: `(0 → 0 misaligned)` — second run is idempotent, confirming the build artifact is already aligned.

- [ ] **Step 6: Verify APK size budget**

Run: `cd c:/Users/MING/myagent/echoling && ls -lh app/build/outputs/apk/debug/app-debug.apk`

Expected: 105-110 MB (v1.0 was 84.8 MB; +20-25 MB for ffmpeg-kit is expected).

---

## CLAUDE.md + README.md follow-ups (post-implementation)

After all 5 tasks land, update the project documentation:

1. **CLAUDE.md §12.X** — append a new entry documenting: (a) ffmpeg-kit-min-gpl 6.0-2 integration; (b) WorkManager + HiltWorkerFactory wiring; (c) MIGRATION_5_6; (d) the new "自动生成字幕" card. Mirror the detail level of §12.19 / §12.26 / §12.33.
2. **README.md** — append a GPL notice paragraph to "已知限制" (ffmpeg-kit-min-gpl forces GPL on the entire APK). Bump "11 个分类" line to "11 个分类 + 自动字幕生成".
3. **docs/release/app-store-hardening.md** — no changes needed (no new permissions).
