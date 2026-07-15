package com.echoling.app.speech

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the on-disk Vosk acoustic model.
 *
 * **Source priority** — [ensureModelReady] walks these in order:
 *
 *  1. **Local filesDir.** If [filesDir]/models/vosk-model-small-en-us-0.15/
 *     already contains a valid model (validated by checking the key
 *     files), use it. This is the fast path for every launch after the
 *     first.
 *  2. **Bundled assets.** If the model is missing locally, copy the
 *     bundled copy from `assets/models/vosk-model-small-en-us-0.15/`
 *     into `filesDir/`. The bundled copy ships with the APK so users
 *     don't need internet access at any point.
 *
 * **Why copy from assets to filesDir?** Vosk's `Model` constructor
 * takes a real filesystem directory — Android `assets/` is not a real
 * filesystem path, the files are accessed through AssetManager. So we
 * unpack once into internal storage and feed Vosk that path on
 * subsequent runs.
 *
 * **Where it lives:** `filesDir/models/vosk-model-small-en-us-0.15/`.
 *
 * **Validation:** [isModelPresent] checks for the key files that any
 * Vosk model needs — `am/final.mdl` (the acoustic model binary) and
 * the `conf/` directory (mfcc config). If either is missing we treat
 * the install as corrupt and re-copy from assets.
 *
 * (2026-07-04) Removed the previous alphacephei.com network fallback
 * (was ModelManager.downloadAndExtract). The Vosk model is bundled in
 * `assets/models/vosk-model-small-en-us-0.15/` (~68 MB) and shipped
 * with every release APK, so the network path is dead code in
 * production. Keeping it would have required `android.permission.INTERNET`
 * to stay declared in the manifest — which fails the
 * 隐私政策 / "用不到的网络权限" review that the Chinese
 * 应用商店 (华为 / 小米 / OPPO / vivo / 应用宝) enforce for an
 * otherwise fully-offline app. If you ever switch to a lean APK
 * strategy that ships the model separately, the download path
 * needs to come back along with the INTERNET permission.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class DownloadState {
        data object NotStarted : DownloadState()
        data class Deploying(val progress: Float) : DownloadState()  // 0..1
        data object Ready : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotStarted)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val mutex = Any()

    /** Returns the absolute path to the model dir, deploying it if needed. */
    suspend fun ensureModelReady(): Result<String> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            val modelDir = modelDir()
            if (isModelPresent(modelDir)) {
                _downloadState.value = DownloadState.Ready
                return@withContext Result.success(modelDir.absolutePath)
            }
            // Bundled assets are the only source we use. If they're
            // missing the APK was built wrong — fail loudly so the
            // problem is reported rather than silently retrying a
            // network path that no longer exists.
            if (!hasBundledModel()) {
                val msg = "Vosk model missing from APK assets/models/. " +
                    "Release builds must package the model."
                Log.e(TAG, msg)
                _downloadState.value = DownloadState.Failed(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }
            try {
                _downloadState.value = DownloadState.Deploying(0f)
                copyFromAssets(modelDir)
                _downloadState.value = DownloadState.Ready
                Result.success(modelDir.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "copy from assets failed", e)
                _downloadState.value = DownloadState.Failed(e.message ?: e.javaClass.simpleName)
                Result.failure(e)
            }
        }
    }

    fun isModelReady(): Boolean = isModelPresent(modelDir())

    fun modelDir(): File = File(context.filesDir, "models/vosk-model-small-en-us-0.15")

    private fun isModelPresent(dir: File): Boolean {
        if (!dir.isDirectory) return false
        // Vosk model dir must contain the acoustic model binary and
        // the mfcc config directory. A partial copy fails this check
        // and forces a re-copy from assets.
        val am = File(dir, "am/final.mdl")
        val conf = File(dir, "conf")
        return am.exists() && conf.isDirectory
    }

    private fun hasBundledModel(): Boolean {
        // We only need to know whether assets/models/vosk-model-small-en-us-0.15/
        // was packaged into the APK. Listing assets is cheap enough
        // to do synchronously; copying only happens if needed.
        return try {
            context.assets.list("models/vosk-model-small-en-us-0.15")?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.w(TAG, "assets.list failed", e)
            false
        }
    }

    private fun copyFromAssets(targetDir: File) {
        targetDir.parentFile?.mkdirs()
        // Walk the asset tree depth-first. AssetManager.list() gives
        // us the entries at one level — files vs subdirs are
        // distinguished by re-listing a subdir.
        copyAssetTree("models/vosk-model-small-en-us-0.15", targetDir)
    }

    private fun copyAssetTree(assetPath: String, outDir: File) {
        val entries = context.assets.list(assetPath)
        if (entries.isNullOrEmpty()) {
            // No children — this could be either an empty dir or a file.
            // Probe by trying to open it; if it works, it's a file.
            val outFile = File(outDir, File(assetPath).name)
            outFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            return
        }
        outDir.mkdirs()
        for (name in entries) {
            val childAssetPath = "$assetPath/$name"
            val childEntries = context.assets.list(childAssetPath)
            if (childEntries.isNullOrEmpty()) {
                // File — leaf
                val outFile = File(outDir, name)
                outFile.parentFile?.mkdirs()
                context.assets.open(childAssetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            } else {
                // Subdirectory — recurse
                copyAssetTree(childAssetPath, File(outDir, name))
            }
        }
    }

    /** Reset state — used in tests / for "redeploy model" UI. */
    fun resetForRedownload() {
        synchronized(mutex) {
            _downloadState.value = DownloadState.NotStarted
            // Don't delete the model dir here — the UI calls
            // ensureModelReady() next, which short-circuits if present.
        }
    }

    private companion object {
        const val TAG = "ModelManager"
    }
}