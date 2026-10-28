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
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.PLAYLIST_INDEX_PREF
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.PLAYLIST_TRACK_POSITION_MS_PREF
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.api.JellyfinTree
import be.bendardenne.jellyfin.aaos.api.MediaTreeFactory
import dagger.hilt.android.AndroidEntryPoint
import org.jellyfin.sdk.Jellyfin
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class JellyfinMusicService : MediaLibraryService() {

    @Inject
    lateinit var jellyfin: Jellyfin

    @Inject
    lateinit var mediaTreeFactory: MediaTreeFactory

    private lateinit var accountManager: JellyfinAccountManager
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var callback: JellyfinMediaLibrarySessionCallback

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentPlaybackTime: Long = 0;
    private var currentTrack: MediaItem? = null;

    private lateinit var playbackPoll: Runnable;


    private val playerListener = object : Player.Listener {
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

    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_MARKER, "onCreate")

        accountManager = JellyfinAccountManager(AccountManager.get(applicationContext))
        mediaSourceFactory = DefaultMediaSourceFactory(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(playerListener)

        // Start in no repeat & no shuffle by default
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        // TODO  double check if we can't get events for this
        // https://proandroiddev.com/mastering-playback-state-with-exo-player-977016aa5003
        pollForPlaybackStatus(player)

        callback = JellyfinMediaLibrarySessionCallback(this, accountManager, mediaTreeFactory)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setMediaButtonPreferences(CommandButtons.createButtons(player))
            .build()

        if (accountManager.isAuthenticated) {
            onLogin()
        }
    }

    private fun pollForPlaybackStatus(player: ExoPlayer) {
        // Repeatedly poll the player for current elapsed playback time
        // We need this to report elapsed time on playback stop, which is needed for scrobbling.
        playbackPoll = Runnable {
            if (player.isPlaying) {
                currentPlaybackTime = player.currentPosition
                currentTrack = player.currentMediaItem

                PreferenceManager.getDefaultSharedPreferences(this@JellyfinMusicService).edit {
                    putLong(PLAYLIST_TRACK_POSITION_MS_PREF, currentPlaybackTime)
                }
            }

            handler.postDelayed(playbackPoll, 1000)
        }
        handler.postDelayed(playbackPoll, 1000)
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

    fun onLogout() {
        // Remove the account first so browsers refetching afterwards see the signed-out state.
        accountManager.deleteAccount()

        val player = mediaLibrarySession.player
        player.stop()
        player.clearMediaItems()
        currentTrack = null
        currentPlaybackTime = 0

        // Drop the persisted queue so it is not restored for the next user.
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            remove(JellyfinMediaLibrarySessionCallback.PLAYLIST_IDS_PREF)
            remove(PLAYLIST_INDEX_PREF)
            remove(PLAYLIST_TRACK_POSITION_MS_PREF)
        }
    }

    fun onLogin() {
        val mediaTree = mediaTreeFactory.getTree()
        mediaTree.onLogin()

        if (accountManager.serverType == ServerType.JELLYFIN && mediaTree is JellyfinTree) {
            val headers = mediaTree.jellyfinApi.auth(accountManager)
            val authedFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            mediaSourceFactory.setDataSourceFactory(authedFactory)
        } else {
            mediaSourceFactory.setDataSourceFactory(DefaultHttpDataSource.Factory())
        }

        // Trigger a refresh upon login.
        mediaLibrarySession.notifyChildrenChanged(ROOT_ID, 4, null)
    }

    private suspend fun reportPlayback(player: Player) {
        val exoPlayer = player as ExoPlayer
        val mediaTree = mediaTreeFactory.getTree()

        if (currentTrack != null) {
            Log.i(LOG_MARKER, "Reporting playback stopped: ${currentPlaybackTime}")
            mediaTree.reportPlaybackStopped(currentTrack!!, currentPlaybackTime)
        }

        if (player.currentMediaItem != null) {
            val format = exoPlayer.audioFormat
            val formatString = "${format?.containerMimeType} at ${format?.averageBitrate} bps"

            Log.i(
                LOG_MARKER,
                "Playing $formatString: ${exoPlayer.currentMediaItem?.localConfiguration?.uri}"
            )
            mediaTree.reportPlaybackStart(player.currentMediaItem!!)
        }
    }
}
