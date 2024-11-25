package be.bendardenne.jellyfin.aaos

import android.accounts.AccountManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import dagger.hilt.android.AndroidEntryPoint
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
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

        val callback = JellyfinMediaLibrarySessionCallback(this, accountManager, jellyfinApi)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .build()

        if (accountManager.isAuthenticated) {
            onLogin()
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

        val authedFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        mediaSourceFactory.setDataSourceFactory(authedFactory)

        // Trigger a refresh upon login.
        mediaLibrarySession.notifyChildrenChanged(ROOT_ID, 4, null)
    }
}