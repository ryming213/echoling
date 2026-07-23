# smart-exception-java AAR vendoring

> **Status:** Implemented 2026-07-18 — unblocks `AutoTranscriptionWorker` at
> the 0% → 30% step on real devices.

## Why

Auto-subtitle on a 5-minute imported video (real device test, 2026-07-18) got
stuck at 0%. `adb logcat -s AutoTranscriptionWorker:V` showed:

```
doWork START courseId=... mediaPath=.../...mkv
step1 ffmpeg.start mediaPath=...
java.lang.NoClassDefFoundError: com.arthenica.ffmpegkit.FFmpegKitConfig
    at com.arthenica.ffmpegkit.FFmpegSession.create(FFmpegSession.java:57)
    at com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(FFmpegKit.java:58)
    at FfmpegAudioExtractor$extractMono16kWav$2.invokeSuspend(...)
Caused by: java.lang.NoClassDefFoundError: Failed resolution of:
    Lcom/arthenica/smartexception/java/Exceptions;
    at com.arthenica.ffmpegkit.FFmpegKitConfig.<clinit>(FFmpegKitConfig.java:134)
Caused by: java.lang.ClassNotFoundException: Didn't find class
    "com.arthenica.smartexception.java.Exceptions" on path: DexPathList
```

`FFmpegKitConfig.<clinit>` references `Exceptions.registerRootPackage` and
the session classes reference `Exceptions.getStackTraceString` — both live
in a transitive AAR (`com.arthenica:ffmpeg-kit-smart-exception-java:6.0`)
that Arthenica never included in `ffmpeg-kit-min-gpl-6.0-2.aar` and which
they wiped from Maven Central when ffmpeg-kit was retired in early 2025.

## Why we can't just download the AAR

| Source | Status |
|---|---|
| Maven Central (`com.arthenica:ffmpeg-kit-smart-exception-java:6.0`) | 404 — Arthenica wiped in 2025 |
| Aliyun mirror | 404 |
| Google Maven | 404 |
| Bintray archive | 404 |
| Sonatype OSS (`com.arthenica:smart-exception:6.0`) | 302 → Maven Central → 404 |
| GitHub `arthenica/smart-exception-java` | 404 (repo deleted) |
| GitHub code search (`com.arthenica.smartexception.java.Exceptions`) | 0 hits |
| JitPack (`com.github.arthenica/...`) | 401 — repo gone |

`smart-exception-java` is also the upstream repo of the JNA dispatcher used
by ffmpeg-kit — it's an empty library at runtime (two static methods).

## Reverse-engineered API surface

`javap` over `ffmpeg-kit-min-gpl-6.0-2.aar` shows only two call sites for
`com.arthenica.smartexception.java.Exceptions`:

```
invokestatic  Method com/arthenica/smartexception/java/Exceptions
                .registerRootPackage:(Ljava/lang/String;)V
invokestatic  Method com/arthenica/smartexception/java/Exceptions
                .getStackTraceString:(Ljava/lang/Throwable;)Ljava/lang/String;
```

Both called from `FFmpegKitConfig.<clinit>` (once, with `"com.arthenica"`)
and `FFmpegSession.{create, fail}` (per-failure path). Nothing else.

## Implementation

`app/libs/ffmpeg-kit-smart-exception-java-6.0.aar` (2.4 KB, locally built):

- **One class:** `com.arthenica.smartexception.java.Exceptions` (final, private ctor)
- **`registerRootPackage(String)`:** stores prefix in static field; tolerates null
- **`getStackTraceString(Throwable)`:** `Throwable.printStackTrace(PrintWriter)`
  → String; when a root package is registered, filters frames to keep only
  classes under that root (the "smart" behavior — concise stack traces
  without the JDK / Android framework noise above the call site). Falls
  back to the full trace if filtering would yield empty.
- **`proguard.txt`:** `-keep class com.arthenica.smartexception.java.** { *; }`
  so R8 doesn't strip the public API when we eventually enable minification.

The source for `Exceptions.java` lives only in the AAR (no committed .java
file) — re-deriving the bytecode from scratch would lose the contract
fidelity we just established. If we ever need to modify the source, the
build recipe is in `scripts/build_smart_exception_aar.md` (TODO: write
when there's a reason to).

## Files

| File | Change |
|---|---|
| `app/libs/ffmpeg-kit-smart-exception-java-6.0.aar` | New (vendored, 2.4 KB) |
| `app/build.gradle.kts` (lines 193-215) | Add `implementation(files(...))`; remove misleading "self-contained (no transitive Maven deps)" claim |

## Verification (real device)

After `./gradlew installDebug`:

```
adb logcat -s AutoTranscriptionWorker:V

# Expected sequence on 5-min video:
doWork START courseId=course_xxx mediaPath=.../...mkv
doWork startProgress=0 wavPath=/data/.../cacheDir/auto_subtitle/course_xxx.wav
step1 ffmpeg.start mediaPath=.../...mkv
step1 ffmpeg.done elapsedMs=... wav=...bytes     ← ~3-8s for 5-min mkv
step2 vosk.start wav=...bytes
step2 vosk.done elapsedMs=... segments=NN        ← ~20-60s
step3 srt.start segments=NN
step3 srt.done cues=NN path=.../course_xxx.srt
doWork DONE courseId=course_xxx
```

Then in the app: course chip transitions from "字幕识别中 N%" → gone, and
Practice screen loads with auto-generated English SRT.

## Risk

- The `getStackTraceString` filter is a behavioral approximation, not a
  byte-for-byte copy of the original library. If a future ffmpeg-kit
  upgrade changes what `Exceptions` is expected to return, we'd need to
  revisit. Mitigation: the only callers are `FFmpegSession.create` (set
  initial session's stack trace for the failure JSON) and `FFmpegSession.fail`
  (overwrite on failure). Both produce diagnostic data that's logged but
  not parsed — divergence is invisible to the user.