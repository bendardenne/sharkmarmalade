package be.bendardenne.jellyfin.aaos

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT
import androidx.media3.session.MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT_COMPAT
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.PARENT_KEY
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import be.bendardenne.jellyfin.aaos.signin.SignInActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.serializer.toUUID

@OptIn(UnstableApi::class)
class JellyfinMediaLibrarySessionCallback(
    private val service: JellyfinMusicService,
    private val accountManager: JellyfinAccountManager,
    private val jellyfinApi: ApiClient
) : MediaLibraryService.MediaLibrarySession.Callback {

    companion object {
        const val LOGIN_COMMAND = "be.bendardenne.jellyfin.aaos.COMMAND.LOGIN"
        const val REPEAT_COMMAND = "be.bendardenne.jellyfin.aaos.COMMAND.REPEAT"
        const val SHUFFLE_COMMAND = "be.bendardenne.jellyfin.aaos.COMMAND.SHUFFLE"
    }

    private val tree = JellyfinMediaTree(service, jellyfinApi)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ConnectionResult {
        val connectionResult = super.onConnect(session, controller)

        val sessionCommands = connectionResult.availableSessionCommands
            .buildUpon()
            .add(SessionCommand(LOGIN_COMMAND, Bundle()))
            .add(SessionCommand(REPEAT_COMMAND, Bundle()))
            .add(SessionCommand(SHUFFLE_COMMAND, Bundle()))
            .build()

        return ConnectionResult.accept(
            sessionCommands,
            connectionResult.availablePlayerCommands
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        Log.i(LOG_MARKER, "onGetRoot")
        return SuspendToFutureAdapter.launchFuture {
            LibraryResult.ofItem(
                tree.getItem(ROOT_ID),
                params
            )
        }
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
            return Futures.immediateFuture(
                LibraryResult.ofError(
                    SessionError(
                        SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                        service.getString(R.string.sign_in_to_your_jellyfin_server)
                    ),
                    MediaLibraryService.LibraryParams.Builder()
                        .setExtras(authenticationExtras()).build()
                )
            )
        }

        return SuspendToFutureAdapter.launchFuture {
            LibraryResult.ofItemList(tree.getChildren(parentId), params)
        }
    }

    private fun authenticationExtras(): Bundle {
        return Bundle().also {
            it.putString(
                EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT,
                service.getString(R.string.sign_in_to_your_jellyfin_server)
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
        return SuspendToFutureAdapter.launchFuture {
            LibraryResult.ofItem(
                tree.getItem(mediaId),
                null
            )
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        Log.i(LOG_MARKER, "onAddMediaItems $mediaItems")
        return SuspendToFutureAdapter.launchFuture { resolveMediaItems(mediaItems) }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        Log.i(LOG_MARKER, "onSetMediaItems $mediaItems")
        return SuspendToFutureAdapter.launchFuture {
            if (isSingleItemWithParent(mediaItems)) {
                val singleItem = mediaItems[0]
                val resolvedItems = expandSingleItem(singleItem)
                return@launchFuture MediaSession.MediaItemsWithStartPosition(
                    resolvedItems,
                    resolvedItems.indexOfFirst { it.mediaId == singleItem.mediaId },
                    startPositionMs
                )
            }

            MediaSession.MediaItemsWithStartPosition(
                resolveMediaItems(mediaItems),
                startIndex,
                startPositionMs
            )
        }
    }

    private suspend fun isSingleItemWithParent(mediaItems: List<MediaItem>): Boolean {
        return mediaItems.size == 1 &&
                tree.getItem(mediaItems[0].mediaId).mediaMetadata.extras?.containsKey(PARENT_KEY) == true
    }

    private suspend fun expandSingleItem(item: MediaItem): List<MediaItem> {
        // This could load a lot of tracks if the parent has many children.
        val parentId = tree.getItem(item.mediaId).mediaMetadata.extras?.getString(PARENT_KEY)!!
        return resolveMediaItems(tree.getChildren(parentId))
    }

    private suspend fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val playlist = mutableListOf<MediaItem>()

        mediaItems.forEach {
            Log.i(LOG_MARKER, "Resolving ${it.mediaId}")
            // We need to call getItem to resolve the content: the provided object only has an ID
            val item = tree.getItem(it.mediaId)
            // If the item is an album or playlist, get its children and add them to the playlist.
            // Albums are playlists are "immediately playable" items, that actually load their
            // children (tracks).
            if (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM ||
                item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_PLAYLIST
            ) {
                resolveMediaItems(tree.getChildren(item.mediaId)).forEach(playlist::add)
            } else if (item.mediaMetadata.isPlayable == true) {
                playlist.add(item)
            } else {
                Log.e(LOG_MARKER, "Cannot add media ${item.mediaMetadata.title}")
            }
        }

        return playlist
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return SuspendToFutureAdapter.launchFuture {
            val results = tree.search(query).size
            session.notifySearchResultChanged(browser, query, results, params)
            LibraryResult.ofVoid(params)
        }
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return SuspendToFutureAdapter.launchFuture {
            val results = tree.search(query)
            LibraryResult.ofItemList(results, params)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Log.i(LOG_MARKER, "CustomCommand: ${customCommand.customAction}")
        when (customCommand.customAction) {
            LOGIN_COMMAND -> {
                service.onLogin()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            REPEAT_COMMAND -> {
                val currentMode = session.player.repeatMode
                session.player.repeatMode = (currentMode + 1) % 3 // There are 3 repeat modes
                session.setMediaButtonPreferences(CommandButtons.createButtons(session.player))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            SHUFFLE_COMMAND -> {
                session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                session.setMediaButtonPreferences(CommandButtons.createButtons(session.player))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        Log.i(LOG_MARKER, "onSetRating ${(rating as HeartRating).isHeart}")

        val item = session.player.currentMediaItem
        item?.let {
            val metadata = it.mediaMetadata.buildUpon().setUserRating(rating).build()
            val mediaItem = it.buildUpon().setMediaMetadata(metadata).build()
            session.player.replaceMediaItem(session.player.currentMediaItemIndex, mediaItem)
        }

        return SuspendToFutureAdapter.launchFuture {
            applyRating(mediaId, rating)
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    private suspend fun applyRating(currentMediaItem: String, newRating: Rating) {
        val id = currentMediaItem.toUUID()

        if (newRating == HeartRating(true)) {
            Log.i(LOG_MARKER, "Marking as favorite")
            jellyfinApi.userLibraryApi.markFavoriteItem(id).content.isFavorite.toString()
        } else {
            Log.i(LOG_MARKER, "Unmarking as favorite")
            jellyfinApi.userLibraryApi.unmarkFavoriteItem(id).content.isFavorite.toString()
        }
    }
}