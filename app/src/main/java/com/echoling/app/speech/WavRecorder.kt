package com.echoling.app.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 16 kHz mono 16-bit PCM WAV recorder. Built on [AudioRecord] (not
 * [android.media.MediaRecorder]) because Vosk needs raw PCM at a
 * fixed sample rate, and Android's MediaRecorder cannot output raw
 * PCM in a portable way across devices.
 *
 * The two audio backends in this app have distinct roles:
 * - [VoiceRecorder] (m4a/AAC, 44.1 kHz) — used by SpeakingPage
 *   (跟读). Quality > size.
 * - [WavRecorder] (PCM 16 kHz) — used by TestingPage (测试). The
 *   file must be 16 kHz mono 16-bit PCM so Vosk can transcribe it
 *   directly without resampling. File is also used for playback.
 *
 * Recording state is exposed via [recordingState] / [amplitude] for
 * UI binding (similar shape to VoiceRecorder). The actual capture
 * happens on a dedicated background thread; [start] / [stop] are
 * non-blocking.
 *
 * WAV format: 44-byte RIFF header + 16-bit PCM data, little-endian.
 * Standard for Vosk / most STT engines.
 */
@Singleton
class WavRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val isCapturing = AtomicBoolean(false)
    private var currentFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var totalSamplesWritten: Long = 0L

    /**
     * Begin recording into a fresh .wav file under
     * `cacheDir/recordings/recording_<ts>.wav`. Returns the file path.
     */
    fun start(): String {
        if (isCapturing.get()) return currentFile?.absolutePath.orEmpty()

        val outputDir = File(context.cacheDir, "recordings")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Failed to create recordings dir")
        }
        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.wav")
        currentFile = outputFile
        totalSamplesWritten = 0L
        recordingStartTimeMs = System.currentTimeMillis()

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioRecord.getMinBufferSize returned $minBuffer")
        }
        // 2x min to avoid underruns on slow devices.
        val bufferSize = minBuffer * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord init failed")
        }
        audioRecord?.startRecording()
        isCapturing.set(true)
        _recordingState.value = RecordingState.RECORDING

        captureThread = Thread({ captureLoop(bufferSize) }, "WavRecorder-capture").also {
            it.start()
        }
        return outputFile.absolutePath
    }

    private fun captureLoop(bufferSize: Int) {
        val out = try {
            FileOutputStream(currentFile)
        } catch (e: Exception) {
            Log.e(TAG, "open output failed", e)
            isCapturing.set(false)
            _recordingState.value = RecordingState.IDLE
            return
        }
        // Reserve 44-byte header; backfill size fields on stop.
        try {
            out.write(ByteArray(HEADER_SIZE))
        } catch (e: Exception) {
            Log.e(TAG, "write header reserve failed", e)
            closeQuietly(out)
            isCapturing.set(false)
            _recordingState.value = RecordingState.IDLE
            return
        }

        val shortBuf = ShortArray(bufferSize / 2)
        val record = audioRecord ?: run {
            closeQuietly(out)
            isCapturing.set(false)
            _recordingState.value = RecordingState.IDLE
            return
        }

        while (isCapturing.get()) {
            val read = try {
                record.read(shortBuf, 0, shortBuf.size)
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord.read failed", e)
                break
            }
            if (read <= 0) continue
            // Track peak amplitude for the in-overlay meter (0..32767).
            var peak = 0
            for (i in 0 until read) {
                val s = shortBuf[i].toInt()
                val abs = if (s < 0) -s else s
                if (abs > peak) peak = abs
            }
            _amplitude.value = peak

            try {
                val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until read) byteBuf.putShort(shortBuf[i])
                out.write(byteBuf.array(), 0, read * 2)
                totalSamplesWritten += read
            } catch (e: Exception) {
                Log.e(TAG, "write PCM failed", e)
                break
            }
        }

        closeQuietly(out)
        // Backfill header now that we know the data size.
        currentFile?.let { f -> writeWavHeader(f, totalSamplesWritten) }
    }

    /** Stop recording. Returns (filePath, durationMs) or null if not recording. */
    fun stop(): RecordingResult? {
        if (!isCapturing.get()) return null
        isCapturing.set(false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord.stop failed", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord.release failed", e)
        }
        audioRecord = null
        captureThread?.join(500)
        captureThread = null
        val path = currentFile?.absolutePath
        val duration = System.currentTimeMillis() - recordingStartTimeMs
        _recordingState.value = RecordingState.STOPPED
        _amplitude.value = 0
        if (path == null) return null
        return RecordingResult(path, duration)
    }

    /** Discard the current recording. */
    fun cancel() {
        if (!isCapturing.get() && _recordingState.value != RecordingState.RECORDING) {
            // still clean up any leftover file
            currentFile?.delete()
            currentFile = null
            return
        }
        isCapturing.set(false)
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        captureThread?.join(500)
        captureThread = null
        currentFile?.delete()
        currentFile = null
        _recordingState.value = RecordingState.IDLE
        _amplitude.value = 0
    }

    fun release() {
        cancel()
    }

    private fun closeQuietly(fos: FileOutputStream) {
        try { fos.close() } catch (_: Exception) {}
    }

    /**
     * Write a standard 16-bit PCM mono WAV header at the head of [file].
     * [numSamples] is the count of int16 samples (per-channel) in the
     * data section. Writes the header in-place by reopening the file in
     * rw mode — that's why the capture loop reserves the 44 bytes up
     * front instead of writing the header at start.
     */
    private fun writeWavHeader(file: File, numSamples: Long) {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val dataSize = numSamples * CHANNELS * BITS_PER_SAMPLE / 8
        val totalSize = (dataSize + HEADER_SIZE - 8).toInt()

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)               // subchunk1 size (PCM)
        header.putShort(1)              // audio format (1 = PCM)
        header.putShort(CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())  // block align
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize.toInt())

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header.array(), 0, HEADER_SIZE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeWavHeader failed", e)
        }
    }

    private companion object {
        const val TAG = "WavRecorder"
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val HEADER_SIZE = 44
    }
}
