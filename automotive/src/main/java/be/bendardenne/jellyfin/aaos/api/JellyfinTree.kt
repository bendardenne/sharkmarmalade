package be.bendardenne.jellyfin.aaos.api

import android.content.Context
import android.util.Log
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.common.Rating
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.MediaItemFactory
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.FAVOURITES
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.LATEST_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.PLAYLISTS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.RANDOM_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import be.bendardenne.jellyfin.aaos.R
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.auth
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID

class JellyfinTree(
    private val context: Context,
    private val accountManager: JellyfinAccountManager,
    jellyfin: Jellyfin,
    artSize: Int = 512,
    private val maxItemsPerPage: Int = 120
) : MediaTree {

    val jellyfinApi: ApiClient = jellyfin.createApi()
    private val itemFactory = MediaItemFactory(context)
    private val mapper = JellyfinItemMapper(itemFactory, jellyfinApi, artSize)

    private val mediaItems: Cache<String, MediaItem> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()

    override fun onLogin() {
        jellyfinApi.auth(accountManager)
    }

    override suspend fun getItem(id: String): MediaItem {
        if (mediaItems.getIfPresent(id) == null) {
            // Cache miss
            val newItem = when (id) {
                ROOT_ID -> itemFactory.rootNode()
                LATEST_ALBUMS -> itemFactory.latestAlbums()
                RANDOM_ALBUMS -> itemFactory.randomAlbums()
                FAVOURITES -> itemFactory.favourites()
                PLAYLISTS -> itemFactory.playlists()
                else -> {
                    val response = jellyfinApi.userLibraryApi.getItem(id.toUUID())
                    mapper.create(response.content)
                }
            }

            mediaItems.put(id, newItem)
        }

        return mediaItems.getIfPresent(id)!!
    }

    override suspend fun getChildren(id: String): List<MediaItem> {
        return when (id) {
            ROOT_ID -> listOf(
                getItem(LATEST_ALBUMS),
                getItem(RANDOM_ALBUMS),
                getItem(FAVOURITES),
                getItem(PLAYLISTS)
            )

            LATEST_ALBUMS -> getLatestAlbums()
            RANDOM_ALBUMS -> getRandomAlbums()
            FAVOURITES -> getFavourite()
            PLAYLISTS -> getPlaylists()
            else -> getItemChildren(id)
        }
    }

    private fun cache(
        dto: BaseItemDto,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        val item = mapper.create(dto, group, parent)
        mediaItems.put(item.mediaId, item)
        return item
    }

    private suspend fun getLatestAlbums(): List<MediaItem> {
        val response = jellyfinApi.userLibraryApi.getLatestMedia(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = maxItemsPerPage
        )

        return response.content.map { cache(it) }
    }

    private suspend fun getRandomAlbums(): List<MediaItem> {
        val response = jellyfinApi.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            limit = maxItemsPerPage
        )

        return response.content.items.map { cache(it) }
    }

    private suspend fun getPlaylists(): List<MediaItem> {
        val response = jellyfinApi.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            sortOrder = listOf(SortOrder.DESCENDING),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            limit = maxItemsPerPage
        )

        return response.content.items.map { cache(it) }
    }

    private suspend fun getItemChildren(id: String): List<MediaItem> {
        if (getItem(id).mediaMetadata.mediaType == MEDIA_TYPE_ARTIST) {
            return getArtistAlbums(id)
        }

        var sortBy = listOf(
            ItemSortBy.PARENT_INDEX_NUMBER,
            ItemSortBy.INDEX_NUMBER,
            ItemSortBy.SORT_NAME
        )

        // For playlists, we should respect the default order (user's track order)
        if (getItem(id).mediaMetadata.mediaType == MEDIA_TYPE_PLAYLIST) {
            sortBy = listOf(ItemSortBy.DEFAULT)
        }

        val response = jellyfinApi.itemsApi.getItems(
            sortBy = sortBy,
            parentId = id.toUUID()
        )

        return response.content.items.map { cache(it, parent = id) }
    }

    private suspend fun getArtistAlbums(id: String): List<MediaItem> {
        val response = jellyfinApi.itemsApi.getItems(
            sortBy = listOf(
                ItemSortBy.PARENT_INDEX_NUMBER,
                ItemSortBy.INDEX_NUMBER,
                ItemSortBy.SORT_NAME
            ),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            albumArtistIds = listOf(id.toUUID()),
        )

        return response.content.items.map { cache(it) }
    }

    private suspend fun getFavourite(): List<MediaItem> {
        val response = jellyfinApi.itemsApi.getItems(
            recursive = true,
            filters = listOf(ItemFilter.IS_FAVORITE),
            includeItemTypes = listOf(
                BaseItemKind.AUDIO,
                BaseItemKind.MUSIC_ALBUM,
                BaseItemKind.MUSIC_ARTIST
            )
        )

        return response.content.items.map { cache(it, groupForItem(it), parent = FAVOURITES) }
    }

    private fun groupForItem(dto: BaseItemDto): String = (
            if (dto.type == BaseItemKind.MUSIC_ALBUM)
                context.getString(R.string.albums)
            else if (dto.type == BaseItemKind.MUSIC_ARTIST)
                context.getString(R.string.artists)
            else
                context.getString(R.string.tracks)
            )

    override suspend fun search(query: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        var response = jellyfinApi.artistsApi.getAlbumArtists(
            searchTerm = query,
            limit = 10,
        )

        items.addAll(response.content.items.map { cache(it, context.getString(R.string.artists)) })

        response = jellyfinApi.itemsApi.getItems(
            recursive = true,
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = 10
        )

        items.addAll(response.content.items.map { cache(it, context.getString(R.string.albums)) })

        response = jellyfinApi.itemsApi.getItems(
            recursive = true,
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            limit = 10
        )

        items.addAll(response.content.items.map { cache(it, context.getString(R.string.playlists)) })

        response = jellyfinApi.itemsApi.getItems(
            recursive = true,
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            limit = 20
        )

        items.addAll(response.content.items.map { cache(it, context.getString(R.string.tracks)) })

        return items
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

    /**
     * Clears the cached MediaItems.
     */
    override fun evictCache() {
        mediaItems.invalidateAll()
    }
}
