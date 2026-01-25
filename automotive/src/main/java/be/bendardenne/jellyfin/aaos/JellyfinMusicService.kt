package be.bendardenne.jellyfin.aaos

import android.accounts.AccountManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.preference.PreferenceManager
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.PLAYLIST_INDEX_PREF
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.PLAYLIST_TRACK_POSITON_MS_PREF
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import dagger.hilt.android.AndroidEntryPoint
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.serializer.toUUID
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class JellyfinMusicService : MediaLibraryService() {

    @Inject
    lateinit var jellyfin: Jellyfin

    private lateinit var accountManager: JellyfinAccountManager
    private lateinit var jellyfinApi: ApiClient
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var callback: JellyfinMediaLibrarySessionCallback

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentPlaybackTime: Long = 0;
    private var currentTrack: MediaItem? = null;

    private lateinit var playbackPoll: Runnable;
    private lateinit var playerListener: Player.Listener;


    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_MARKER, "onCreate")

        accountManager = JellyfinAccountManager(AccountManager.get(applicationContext))
        jellyfinApi = jellyfin.createApi()
        mediaSourceFactory = DefaultMediaSourceFactory(this)

        playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    // Persist the current index of the queue in the preferences.
                    // This is restored in onPlaybackResumption
                    PreferenceManager.getDefaultSharedPreferences(this@JellyfinMusicService).edit {
                        putInt(PLAYLIST_INDEX_PREF, player.currentMediaItemIndex)
                    }

                    SuspendToFutureAdapter.launchFuture { reportPlayback(player) }
                }
            }
        }


        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.addListener(playerListener)

        // Start in Repeat all & no shuffle by default
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = false

        // Repeatedly poll the player for current elapsed playback time
        // We need this to report elapsed time on playback stop, which is needed for scrobbling.
        playbackPoll = Runnable {
            if (player.isPlaying) {
                Log.i(LOG_MARKER, "Playback ${player.currentPosition}")
                currentPlaybackTime = player.currentPosition
                currentTrack = player.currentMediaItem

                PreferenceManager.getDefaultSharedPreferences(this@JellyfinMusicService).edit {
                    putLong(PLAYLIST_TRACK_POSITON_MS_PREF, currentPlaybackTime)
                }
            }

            handler.postDelayed(playbackPoll, 1000)
        }
        handler.postDelayed(playbackPoll, 1000)

        callback = JellyfinMediaLibrarySessionCallback(this, accountManager, jellyfinApi)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setMediaButtonPreferences(CommandButtons.createButtons(player))
            .build()

        if (accountManager.isAuthenticated) {
            onLogin()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        Log.i(LOG_MARKER, "onDestroy")

        mediaLibrarySession.release()
        mediaLibrarySession.player.removeListener(playerListener)
        mediaLibrarySession.player.release()
        handler.removeCallbacks(playbackPoll)
        super.onDestroy()
    }

    fun onLogin() {
        jellyfinApi.update(
            baseUrl = accountManager.server,
            accessToken = accountManager.token
        )

        val headers = mapOf(
            "Authorization" to "MediaBrowser Client=\"${jellyfinApi.clientInfo.name}\", " +
                    "Device=\"${jellyfinApi.deviceInfo.name}\", " +
                    "DeviceId=\"${jellyfinApi.deviceInfo.id}\", " +
                    "Version=\"${jellyfinApi.clientInfo.version}\", " +
                    "Token=\"${jellyfinApi.accessToken}\""
        )

        val authedFactory =
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        mediaSourceFactory.setDataSourceFactory(authedFactory)

        // Trigger a refresh upon login.
        mediaLibrarySession.notifyChildrenChanged(ROOT_ID, 4, null)
    }

    private suspend fun reportPlayback(player: Player) {
        val exoPlayer = player as ExoPlayer
        // MediaItem has changed; if there was a previous item playing, mark it stopped.
        if (currentTrack != null) {
            Log.i(LOG_MARKER, "Reporting playback stopped: ${currentPlaybackTime}")
            jellyfinApi.playStateApi.onPlaybackStopped(
                currentTrack!!.mediaId.toUUID(),
                positionTicks = 10000 * currentPlaybackTime
            )
        }

        if (player.currentMediaItem != null) {
            val format = exoPlayer.audioFormat
            val formatString = "${format?.containerMimeType} at ${format?.averageBitrate} bps"

            Log.i(
                LOG_MARKER,
                "Playing $formatString: ${exoPlayer.currentMediaItem?.localConfiguration?.uri}"
            )
            jellyfinApi.playStateApi.onPlaybackStart(
                player.currentMediaItem!!.mediaId.toUUID(),
                canSeek = true
            )
        }
    }

}