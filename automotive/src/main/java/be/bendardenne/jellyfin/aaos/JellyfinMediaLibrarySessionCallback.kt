package be.bendardenne.jellyfin.aaos

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT_COMPAT
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import be.bendardenne.jellyfin.aaos.signin.SignInActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.sdk.api.client.ApiClient

@OptIn(UnstableApi::class)
class JellyfinMediaLibrarySessionCallback(
    private val service: JellyfinMusicService,
    private val accountManager: JellyfinAccountManager,
    jellyfinApi: ApiClient
) : MediaLibraryService.MediaLibrarySession.Callback {

    companion object {
        const val LOGIN = "be.bendardenne.jellyfin.aaos.COMMAND.LOGIN"
    }

    private val tree = JellyfinMediaTree(jellyfinApi)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val sessionCommands =
            connectionResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(LOGIN, Bundle()))
                .build()
        return MediaSession.ConnectionResult.accept(
            sessionCommands, connectionResult.availablePlayerCommands
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Log.i(LOG_MARKER, "onGetRoot")
        return Futures.immediateFuture(LibraryResult.ofItem(tree.getItem(ROOT_ID), params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Log.i(LOG_MARKER, "onGetChildren $parentId")
        if (!accountManager.isAuthenticated) {
            return Futures.immediateFuture(needsAuth())
        }

        return SuspendToFutureAdapter.launchFuture {
            LibraryResult.ofItemList(tree.getChildren(parentId), params)
        }
    }

    private fun <T> needsAuth(): LibraryResult<T & Any> =
        LibraryResult.ofError(
            SessionError(
                SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                "Sign in to your Jellyfin server"
            ),
            MediaLibraryService.LibraryParams.Builder()
                .setExtras(authenticationExtras()).build()
        )

    private fun authenticationExtras(): Bundle {
        return Bundle().also {
            // TODO i18n
            it.putString(
                EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT,
                "Sign in to your Jellyfin server"
            )

            val signInIntent = Intent(service, SignInActivity::class.java)

            val flags = if (Util.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            it.putParcelable(
                EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT,
                PendingIntent.getActivity(service, 0, signInIntent, flags)
            )

            it.putParcelable(
                EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT_COMPAT,
                PendingIntent.getActivity(service, 0, signInIntent, flags)
            )
        }
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Log.i(LOG_MARKER, "onGetItem $mediaId")
        return Futures.immediateFuture(LibraryResult.ofItem(tree.getItem(mediaId), null))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        Log.i(LOG_MARKER, "onAddMediaItems $mediaItems")
        return SuspendToFutureAdapter.launchFuture { resolveMediaItems(mediaItems) }
    }

    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        Log.i(LOG_MARKER, "onSetMediaItems $mediaItems")
        return SuspendToFutureAdapter.launchFuture {
            MediaSession.MediaItemsWithStartPosition(
                resolveMediaItems(mediaItems),
                startIndex,
                startPositionMs
            )
        }
    }

    private suspend fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val playlist = mutableListOf<MediaItem>()

        mediaItems.forEach {
            val item = tree.getItem(it.mediaId)
            // If the item is an album, get its children and add them to the playlist.
            if (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
                resolveMediaItems(tree.getChildren(item.mediaId)).forEach(playlist::add)
            } else if (item.mediaMetadata.isPlayable == true) {
                playlist.add(item)
            }
        }

        return playlist
    }

    // TODO
    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return super.onSearch(session, browser, query, params)
    }

    // TODO add favourite?
    // TODO shuffle ??
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Log.i(LOG_MARKER, "CustomCommand: ${customCommand.customAction}")
        when (customCommand.customAction) {
            LOGIN -> {
                service.onLogin()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }
}