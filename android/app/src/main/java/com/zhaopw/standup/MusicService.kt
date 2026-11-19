package com.zhaopw.standup

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var library: MusicLibrary
    private val handler = Handler(Looper.getMainLooper())
    private val persistProgress = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, 5000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        library = MusicLibrary(this)
        player = ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        val tracks = library.tracks()
        if (tracks.isNotEmpty()) player.setMediaItems(tracks.map { it.mediaItem() }, tracks.indexOfFirst { it.uri == library.selected() }.coerceAtLeast(0), library.position())
        player.repeatMode = library.mode
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePosition() else library.error(player.currentMediaItem?.mediaId, null) }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { savePosition() }
            override fun onPlayerError(error: PlaybackException) {
                library.error(player.currentMediaItem?.mediaId, "无法播放「${player.currentMediaItem?.mediaMetadata?.title ?: "音频"}」。文件可能已移动、权限失效或格式不支持，请移除后重新导入。")
                player.pause()
                savePosition()
            }
        })
        session = MediaSession.Builder(this, player).build()
        player.prepare() // Prepare without starting: restoring the app never starts music.
        handler.postDelayed(persistProgress, 5000)
    }
    private fun savePosition() { player.currentMediaItem?.mediaId?.let { library.savePosition(it, player.currentPosition) } }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.isPlaying) stopSelf() }
    override fun onDestroy() {
        savePosition()
        handler.removeCallbacks(persistProgress)
        session.release()
        player.release()
        instance = null
        super.onDestroy()
    }
    companion object {
        private var instance: MusicService? = null
        fun pauseForReminder(context: Context) {
            // Receiver and service share the main process: pause completes before the chime notification.
            instance?.let { it.player.pause(); it.savePosition() }

        }
    }
}
