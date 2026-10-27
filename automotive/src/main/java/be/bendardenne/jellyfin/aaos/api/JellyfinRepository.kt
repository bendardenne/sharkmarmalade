package be.bendardenne.jellyfin.aaos.api

import android.content.Context
import android.util.Log
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Rating
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.JellyfinMediaTree
import be.bendardenne.jellyfin.aaos.MediaItemFactory
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.auth
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.serializer.toUUID

class JellyfinRepository(
    context: Context,
    private val accountManager: JellyfinAccountManager,
    jellyfin: Jellyfin,
    artSize: Int = 512
) : MediaRepository {

    val jellyfinApi: ApiClient = jellyfin.createApi()
    private val itemFactory = MediaItemFactory(context, jellyfinApi, artSize)
    private val tree = JellyfinMediaTree(context, jellyfinApi, itemFactory)

    override fun onLogin() {
        jellyfinApi.auth(accountManager)
    }

    override suspend fun getItem(id: String): MediaItem {
        return tree.getItem(id)
    }

    override suspend fun getChildren(id: String): List<MediaItem> {
        return tree.getChildren(id)
    }

    override suspend fun search(query: String): List<MediaItem> {
        return tree.search(query)
    }

    override suspend fun setRating(mediaId: String, rating: Rating) {
        try {
            val id = mediaId.toUUID()
            if (rating == HeartRating(true)) {
                Log.i(LOG_MARKER, "Jellyfin: Marking as favorite")
                jellyfinApi.userLibraryApi.markFavoriteItem(id)
            } else {
                Log.i(LOG_MARKER, "Jellyfin: Unmarking as favorite")
                jellyfinApi.userLibraryApi.unmarkFavoriteItem(id)
            }
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Error setting rating in Jellyfin", e)
        }
    }

    override suspend fun reportPlaybackStart(mediaItem: MediaItem) {
        try {
            jellyfinApi.playStateApi.onPlaybackStart(
                mediaItem.mediaId.toUUID(),
                canSeek = true
            )
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Error reporting playback start to Jellyfin", e)
        }
    }

    override suspend fun reportPlaybackStopped(mediaItem: MediaItem, positionMs: Long) {
        try {
            jellyfinApi.playStateApi.onPlaybackStopped(
                mediaItem.mediaId.toUUID(),
                positionTicks = 10000 * positionMs
            )
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Error reporting playback stopped to Jellyfin", e)
        }
    }

    override fun evictCache() {
        tree.evictCache()
    }
}
