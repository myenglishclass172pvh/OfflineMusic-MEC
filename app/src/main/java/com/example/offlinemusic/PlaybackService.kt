package com.example.offlinemusic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.offlinemusic.audio.EqEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(applicationContext)

        val factory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(EqEngine.processor))
                .build()
        }

        player = ExoPlayer.Builder(this, factory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) persist() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { persist() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // File không hỗ trợ / hỏng: bỏ qua bài và phát bài kế tiếp
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
            }
        })

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()

        // Khôi phục EQ đã lưu (kể cả khi UI chưa mở)
        scope.launch(Dispatchers.IO) { EqEngine.apply(store.eq.first()) }
        // Lưu vị trí phát định kỳ
        scope.launch { while (true) { delay(3000); if (player.isPlaying) persist() } }
    }

    private fun persist() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).joinToString(",") { player.getMediaItemAt(it).mediaId }
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition
        scope.launch(Dispatchers.IO) { store.saveQueue(ids, idx, pos) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        if (player.mediaItemCount > 0) {
            val ids = (0 until player.mediaItemCount).joinToString(",") { player.getMediaItemAt(it).mediaId }
            val idx = player.currentMediaItemIndex
            val pos = player.currentPosition
            runBlocking(Dispatchers.IO) { store.saveQueue(ids, idx, pos) }
        }
        scope.cancel()
        session?.run { release() }
        session = null
        player.release()
        super.onDestroy()
    }
}
