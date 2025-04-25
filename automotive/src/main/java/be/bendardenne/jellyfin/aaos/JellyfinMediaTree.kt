package be.bendardenne.jellyfin.aaos

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_ARTIST
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.FAVOURITES
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.LATEST_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.PLAYLISTS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.RANDOM_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID

class JellyfinMediaTree(private val context: Context, private val api: ApiClient) {

    private val itemFactory = MediaItemFactory(api)

    // TODO make this a Cache ?
    private val mediaItems: MutableMap<String, MediaItem> = mutableMapOf()

    init {
        mediaItems[ROOT_ID] = itemFactory.rootNode()
        mediaItems[LATEST_ALBUMS] = itemFactory.latestAlbums()
        mediaItems[RANDOM_ALBUMS] = itemFactory.randomAlbums()
        mediaItems[FAVOURITES] = itemFactory.favourites()
        mediaItems[PLAYLISTS] = itemFactory.playlists()
    }

    suspend fun getItem(id: String): MediaItem {
        // Ideally, this never happens because it shouldn't be possible to request an item by ID
        // without it having been fetched and cached by one of the getChildren delegates.
        if (mediaItems[id] == null) {
            val response = api.itemsApi.getItems(ids = listOf(id.toUUID()))
            val baseItemDto = response.content.items[0]
            mediaItems[id] = itemFactory.create(baseItemDto)
        }

        return mediaItems[id]!!
    }

    suspend fun getChildren(id: String): List<MediaItem> {
        return when (id) {
            ROOT_ID -> listOf(
                mediaItems[LATEST_ALBUMS]!!,
                mediaItems[RANDOM_ALBUMS]!!,
                mediaItems[FAVOURITES]!!,
                mediaItems[PLAYLISTS]!!
            )

            LATEST_ALBUMS -> getLatestAlbums()
            RANDOM_ALBUMS -> getRandomAlbums()
            FAVOURITES -> getFavouriteTracks()
            PLAYLISTS -> getPlaylists()
            else -> getItemChildren(id)
        }
    }

    private suspend fun getLatestAlbums(): List<MediaItem> {
        val response = api.userLibraryApi.getLatestMedia(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            limit = 24
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
            sortBy = listOf(ItemSortBy.RANDOM),
            limit = 24
        )

        return response.content.items.map {
            val item = itemFactory.create(it)
            mediaItems[item.mediaId] = item
            item
        }
    }

    private suspend fun getPlaylists(): List<MediaItem> {
        val response = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            sortOrder = listOf(SortOrder.DESCENDING),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
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
            parentId = id.toUUID()
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
            albumArtistIds = listOf(id.toUUID()),
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
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            limit = 10
        )

        items.addAll(response.content.items.map {
            val item = itemFactory.create(it, context.getString(R.string.playlists))
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