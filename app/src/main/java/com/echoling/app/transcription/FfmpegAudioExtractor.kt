package com.echoling.app.transcription

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Log as FfLog
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
 *
 * **DTS caveat:** `ffmpeg-kit-min-gpl` does NOT bundle the libdca
 * DTS decoder (only the `full-gpl` flavor does). A source MKV with
 * a DTS audio track silently produces a header-only WAV because
 * ffmpeg cannot decode the stream — ffmpeg exits 0 anyway because
 * `-f wav` + a missing stream just writes the header. Detect this
 * by scanning session logs for "Stream #... Audio: dts"; if found,
 * surface a clearer error so the user knows to remux the file first
 * (echo `ffmpeg -c:v copy -c:a aac -b:a 192k -ac 2 -c:s copy out.mkv`).
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
     * if the produced WAV is suspiciously small (<1KB — usually
     * means ffmpeg wrote a header-only file because the source had
     * no audio track), or if the source uses DTS (see KDoc above).
     */
    suspend fun extractMono16kWav(
        inputPath: String,
        courseId: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(context.cacheDir, "auto_subtitle/$courseId.wav")
            outFile.parentFile?.mkdirs()

            // Use executeWithArguments (the documented tokenized form)
            // instead of execute(String): the String form re-tokenizes the
            // command via shell-style quoting and breaks on paths containing
            // spaces or quote characters. Passing an explicit argument array
            // bypasses tokenization entirely, so any path content is safe.
            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y",
                    "-i", inputPath,
                    "-vn",
                    "-ac", "1",
                    "-ar", "16000",
                    "-f", "wav",
                    outFile.absolutePath,
                )
            )
            val returnCode = session.returnCode
            val logs = session.allLogsAsString.orEmpty()

            // Diagnostic: log a compact summary of ffmpeg's stderr (codec + duration)
// rather than dumping the full build/config banner — that's hundreds of
// lines per run, all noise unless we're actively debugging a decode
// failure. The DTS detection below still scans the full log.
            val audioLine = logs.lineSequence()
                .firstOrNull { it.trimStart().startsWith("Stream #") && "Audio:" in it }
            val durationLine = logs.lineSequence()
                .firstOrNull { it.trimStart().startsWith("Duration:") }
            if (audioLine != null) {
                Log.i(TAG, "ffmpeg audio stream: ${audioLine.trim()}")
            }
            if (durationLine != null) {
                Log.i(TAG, "ffmpeg ${durationLine.trim()}")
            }

            // Detect DTS source — min-gpl can't decode, so the output WAV
            // would be header-only silence, which Vosk then returns 0
            // segments for. Catch this BEFORE the "is the WAV too small?"
            // check because a DTS source actually produces a 40-byte
            // header-only file (smaller than the 1024-byte threshold,
            // but the cause isn't "no audio track" — it's "wrong audio
            // codec"). Giving the user the right hint saves a remux.
            val isDts = logs.contains("Audio: dts", ignoreCase = true)
            if (isDts) {
                throw IllegalStateException(
                    "源文件使用 DTS 音频，ffmpeg-kit-min-gpl 不含 DTS 解码器。" +
                        "请先用 ffmpeg 重封装为 AAC 立体声后重新导入。"
                )
            }

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

    private companion object {
        const val TAG = "FfmpegAudioExtractor"
    }
}