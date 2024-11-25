package be.bendardenne.jellyfin.aaos

import androidx.media3.common.MediaItem
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.FAVOURITES
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.LATEST_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.RANDOM_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy

class JellyfinMediaTree(private val api: ApiClient) {

    private val itemFactory = MediaItemFactory(api)

    // TODO make this a Cache ?
    private val mediaItems: MutableMap<String, MediaItem> = mutableMapOf()
    private val children: MutableMap<String, List<String>> = mutableMapOf()

    init {
        mediaItems[ROOT_ID] = itemFactory.rootNode()
        mediaItems[LATEST_ALBUMS] = itemFactory.latestAlbums()
        mediaItems[RANDOM_ALBUMS] = itemFactory.randomAlbums()
        mediaItems[FAVOURITES] = itemFactory.favourites()
    }

    fun getItem(id: String): MediaItem {
        // TODO Fix !!
        return mediaItems[id]!!
    }

    suspend fun getChildren(id: String): List<MediaItem> {
        // With random albums, don't store the children and recompute them each time.
        if (id == RANDOM_ALBUMS) {
            return resolveChildren(id).map(this::getItem)
        }

        if (!children.containsKey(id)) {
            children[id] = resolveChildren(id)
        }

        return children[id]!!.map(this::getItem)
    }

    private suspend inline fun resolveChildren(id: String): List<String> {
        return when (id) {
            ROOT_ID -> listOf(LATEST_ALBUMS, RANDOM_ALBUMS, FAVOURITES)
            LATEST_ALBUMS -> getLatestAlbums()
            RANDOM_ALBUMS -> getRandomAlbums()
            FAVOURITES -> getFavouriteTracks()
            else -> getItemChildren(id)
        }
    }

    private suspend fun getLatestAlbums(): List<String> {
        val response = api.userLibraryApi.getLatestMedia(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM)
        )

        response.content.map(itemFactory::forAlbum).forEach {
            mediaItems[it.mediaId] = it
        }

        return response.content.map {
            UUIDConverter.dehyphenate(it.id)
        }
    }

    private suspend fun getRandomAlbums(): List<String> {
        val response = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = listOf(
                ItemSortBy.RANDOM
            ),
            limit = 24
        )

        response.content.items.map(itemFactory::forAlbum).forEach {
            mediaItems[it.mediaId] = it
        }

        return response.content.items.map {
            UUIDConverter.dehyphenate(it.id)
        }
    }

    private suspend fun getItemChildren(id: String): List<String> {
        val response = api.itemsApi.getItems(
            sortBy = listOf(
                ItemSortBy.PARENT_INDEX_NUMBER,
                ItemSortBy.INDEX_NUMBER,
                ItemSortBy.SORT_NAME
            ),
            parentId = UUIDConverter.hyphenate(id)
        )

        // Assuming id points to an album. We could check the MediaItem to validate this
        response.content.items.forEach {
            mediaItems[UUIDConverter.dehyphenate(it.id)] = itemFactory.forTrack(it)
        }

        return response.content.items.map { UUIDConverter.dehyphenate(it.id) }
    }

    private suspend fun getFavouriteTracks(): List<String> {
        // TODO when clicking a favourite track, we should load all of them in the playlist
        val response = api.itemsApi.getItems(
            recursive = true,
            filters = listOf(ItemFilter.IS_FAVORITE),
            includeItemTypes = listOf(BaseItemKind.AUDIO)
        )

        response.content.items.map(itemFactory::forTrack).forEach {
            mediaItems[it.mediaId] = it
        }

        return response.content.items.map { UUIDConverter.dehyphenate(it.id) }
    }
}