package be.bendardenne.jellyfin.aaos

import android.accounts.AccountManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
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

    override fun onCreate() {
        super.onCreate()

        accountManager = JellyfinAccountManager(AccountManager.get(applicationContext))
        jellyfinApi = jellyfin.createApi()
        mediaSourceFactory = DefaultMediaSourceFactory(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION
                    )
                ) {
                    SuspendToFutureAdapter.launchFuture { reportPlayback(player) }
                }
            }
        })

        val callback = JellyfinMediaLibrarySessionCallback(this, accountManager, jellyfinApi)

        // Start in Repeat all & no shuffle by default
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = false

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setMediaButtonPreferences(CommandButtons.createButtons(player))
            .build()

        if (accountManager.isAuthenticated) {
            onLogin()
        }
    }

    private suspend fun reportPlayback(player: Player) {
        if (player.isPlaying) {
            val exoPlayer = player as ExoPlayer
            val format = exoPlayer.audioFormat
            val formatString = "${format?.containerMimeType} at ${format?.averageBitrate} bps"

            Log.i(LOG_MARKER, "Playing: $formatString")
            Log.i(LOG_MARKER, "Playing: ${exoPlayer.currentMediaItem?.localConfiguration?.uri}")
            jellyfinApi.playStateApi.onPlaybackStart(
                player.currentMediaItem!!.mediaId.toUUID()
            )
        } else {
            jellyfinApi.playStateApi.onPlaybackStopped(
                player.currentMediaItem!!.mediaId.toUUID()
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession.release()
        mediaLibrarySession.player.release()
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
}