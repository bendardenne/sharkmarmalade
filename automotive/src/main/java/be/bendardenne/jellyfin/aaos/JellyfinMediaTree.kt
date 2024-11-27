package be.bendardenne.jellyfin.aaos

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.FAVOURITES
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.LATEST_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.RANDOM_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy

class JellyfinMediaTree(private val context: Context, private val api: ApiClient) {

    private val itemFactory = MediaItemFactory(api)

    // TODO make this a Cache ?
    private val mediaItems: MutableMap<String, MediaItem> = mutableMapOf()

    init {
        mediaItems[ROOT_ID] = itemFactory.rootNode()
        mediaItems[LATEST_ALBUMS] = itemFactory.latestAlbums()
        mediaItems[RANDOM_ALBUMS] = itemFactory.randomAlbums()
        mediaItems[FAVOURITES] = itemFactory.favourites()
    }

    fun getItem(id: String): MediaItem {
        // It shouldn't be possible to request an item, unless it was previously obtained via
        // getChildren or one of the other methods for toplevel entries.
        // Thus, it should never be possible to request an ID which is not in the map.
        // If needed, we could query the api for the ID when missing
        return mediaItems[id]!!
    }

    suspend fun getChildren(id: String): List<MediaItem> {
        return when (id) {
            ROOT_ID -> listOf(
                mediaItems[LATEST_ALBUMS]!!,
                mediaItems[RANDOM_ALBUMS]!!,
                mediaItems[FAVOURITES]!!
            )

            LATEST_ALBUMS -> getLatestAlbums()
            RANDOM_ALBUMS -> getRandomAlbums()
            FAVOURITES -> getFavouriteTracks()
            else -> getItemChildren(id)
        }
    }

    private suspend fun getLatestAlbums(): List<MediaItem> {
        val response = api.userLibraryApi.getLatestMedia(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM)
        )

        return response.content.map {
            val item = itemFactory.create(it)
            mediaItems[item.mediaId] = item
            item
        }
    }

    private suspend fun getRandomAlbums(): List<MediaItem> {
        val response = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(
                ItemSortBy.RANDOM
            ),
            limit = 24
        )

        return response.content.items.map {
            val item = itemFactory.create(it)
            mediaItems[item.mediaId] = item
            item
        }
    }

    private suspend fun getItemChildren(id: String): List<MediaItem> {
        if (mediaItems[id]?.mediaMetadata?.mediaType == MEDIA_TYPE_ARTIST) {
            return getArtistAlbums(id)
        }

        val response = api.itemsApi.getItems(
            sortBy = listOf(
                ItemSortBy.PARENT_INDEX_NUMBER,
                ItemSortBy.INDEX_NUMBER,
                ItemSortBy.SORT_NAME
            ),
            parentId = UUIDConverter.hyphenate(id)
        )

        return response.content.items.map {
            val item = itemFactory.create(it, parent = id)
            mediaItems[item.mediaId] = item
            item
        }
    }

    private suspend fun getArtistAlbums(id: String): List<MediaItem> {
        val response = api.itemsApi.getItems(
            sortBy = listOf(
                ItemSortBy.PARENT_INDEX_NUMBER,
                ItemSortBy.INDEX_NUMBER,
                ItemSortBy.SORT_NAME
            ),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            albumArtistIds = listOf(UUIDConverter.hyphenate(id)),
        )

        return response.content.items.map {
            val item = itemFactory.create(it)
            mediaItems[item.mediaId] = item
            item
        }
    }

    private suspend fun getFavouriteTracks(): List<MediaItem> {
        val response = api.itemsApi.getItems(
            recursive = true,
            filters = listOf(ItemFilter.IS_FAVORITE),
            includeItemTypes = listOf(BaseItemKind.AUDIO)
        )

        return response.content.items.map {
            val item = itemFactory.create(it, parent = FAVOURITES)
            mediaItems[item.mediaId] = item
            item
        }
    }

    suspend fun search(query: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        var response = api.artistsApi.getAlbumArtists(
            searchTerm = query,
            limit = 10,
        )

        items.addAll(response.content.items.map {
            val item = itemFactory.create(it, context.getString(R.string.artists))
            mediaItems[item.mediaId] = item
            item
        })

        response = api.itemsApi.getItems(
            recursive = true,
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = 10
        )

        items.addAll(response.content.items.map {
            val item = itemFactory.create(it, context.getString(R.string.albums))
            mediaItems[item.mediaId] = item
            item
        })

        response = api.itemsApi.getItems(
            recursive = true,
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            limit = 20
        )

        items.addAll(response.content.items.map {
            val item = itemFactory.create(it, context.getString(R.string.tracks))
            mediaItems[item.mediaId] = item
            item
        })

        return items
    }
}