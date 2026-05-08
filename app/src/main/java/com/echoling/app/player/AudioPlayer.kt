package com.echoling.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentSubtitle = MutableStateFlow<String?>(null)
    val currentSubtitle: StateFlow<String?> = _currentSubtitle.asStateFlow()

    private var subtitleProvider: ((Long) -> String?)? = null

    fun initialize() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        updateState {
                            it.copy(
                                isBuffering = state == Player.STATE_BUFFERING,
                                isPlaying = exoPlayer?.isPlaying == true
                            )
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        updateState { it.copy(currentPositionMs = newPosition.positionMs) }
                    }
                })
            }
        }
    }

    fun setMediaUri(uri: String) {
        exoPlayer?.apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(uri))
            setMediaItem(mediaItem)
            prepare()
        }
    }

    fun setSubtitleProvider(provider: (Long) -> String?) {
        subtitleProvider = provider
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updateState { it.copy(currentPositionMs = positionMs) }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        updateState { it.copy(playbackSpeed = speed) }
    }

    fun setLooping(looping: Boolean) {
        exoPlayer?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        updateState { it.copy(isLooping = looping) }
    }

    fun updatePosition() {
        exoPlayer?.let { player ->
            val position = player.currentPosition
            val duration = player.duration.coerceAtLeast(0)
            updateState {
                it.copy(
                    currentPositionMs = position,
                    durationMs = duration
                )
            }
            subtitleProvider?.invoke(position)?.let { subtitle ->
                _currentSubtitle.value = subtitle
            }
        }
    }

    fun updatePositionFromExternal(position: Long, duration: Long, isPlaying: Boolean = false) {
        updateState {
            it.copy(
                currentPositionMs = position,
                durationMs = duration,
                isPlaying = isPlaying
            )
        }
        subtitleProvider?.invoke(position)?.let { subtitle ->
            _currentSubtitle.value = subtitle
        }
    }

    private var videoPlayer: ExoPlayer? = null

    fun setVideoPlayer(player: ExoPlayer?) {
        videoPlayer = player
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    fun getDuration(): Long = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L

    private fun updateState(update: (PlaybackState) -> PlaybackState) {
        _playbackState.value = update(_playbackState.value)
    }
}
