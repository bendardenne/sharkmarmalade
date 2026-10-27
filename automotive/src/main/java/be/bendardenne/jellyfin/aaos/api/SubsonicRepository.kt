package be.bendardenne.jellyfin.aaos.api

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.preference.PreferenceManager
import be.bendardenne.jellyfin.aaos.AlbumArtContentProvider
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.FAVOURITES
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.LATEST_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.PARENT_KEY
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.PLAYLISTS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.RANDOM_ALBUMS
import be.bendardenne.jellyfin.aaos.MediaItemFactory.Companion.ROOT_ID
import be.bendardenne.jellyfin.aaos.R
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.DIRECT_STREAM
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.EXPAND
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PLAY
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PREF_ALBUM_BEHAVIOUR
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PREF_BITRATE
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import dev.zt64.subsonic.api.model.Album
import dev.zt64.subsonic.api.model.AlbumListType
import dev.zt64.subsonic.api.model.Artist
import dev.zt64.subsonic.api.model.Playlist
import dev.zt64.subsonic.api.model.Song
import dev.zt64.subsonic.client.SubsonicClient
import kotlin.time.ExperimentalTime

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTime::class)
class SubsonicRepository(
    private val context: Context,
    private val accountManager: JellyfinAccountManager,
    private val artSize: Int = 512
) : MediaRepository {

    private val mediaItems: Cache<String, MediaItem> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()

    private val serverUrl: String
        get() = accountManager.server ?: ""

    private val username: String
        get() = accountManager.username ?: ""

    private val tokenOrPassword: String
        get() = accountManager.token ?: ""

    private val apiDelegate = lazy {
        createSubsonicApi(serverUrl, username, tokenOrPassword)
    }
    private val api: SubsonicClient by apiDelegate

    fun close() {
        if (apiDelegate.isInitialized()) {
            api.close()
        }
    }

    override fun onLogin() {
        evictCache()
    }

    override fun evictCache() {
        mediaItems.invalidateAll()
    }

    override suspend fun getItem(id: String): MediaItem {
        val cached = mediaItems.getIfPresent(id)
        if (cached != null) return cached

        val item = when (id) {
            ROOT_ID -> createRootNode()
            LATEST_ALBUMS -> createCategoryNode(LATEST_ALBUMS, "Latest", "schedule", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            RANDOM_ALBUMS -> createCategoryNode(RANDOM_ALBUMS, "Random", "casino", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            FAVOURITES -> createCategoryNode(FAVOURITES, "Favourites", "star_filled", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            PLAYLISTS -> createCategoryNode(PLAYLISTS, "Playlists", "playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            else -> fetchItemFromSubsonic(id)
        }

        if (item != null) {
            mediaItems.put(id, item)
            return item
        }

        return createRootNode()
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
            FAVOURITES -> getFavourites()
            PLAYLISTS -> getPlaylists()
            else -> getItemChildren(id)
        }
    }

    private suspend fun getLatestAlbums(): List<MediaItem> {
        return try {
            val albums = api.getAlbumsID3(AlbumListType.Newest, size = 120)
            albums.map {
                val item = createAlbumItem(it)
                mediaItems.put(item.mediaId, item)
                item
            }
        } catch (e: Exception) {
            try {
                val albums = api.getAlbums(AlbumListType.Newest, size = 120)
                albums.map {
                    val item = createAlbumItem(it)
                    mediaItems.put(item.mediaId, item)
                    item
                }
            } catch (e2: Exception) {
                Log.e(LOG_MARKER, "Failed to get latest albums", e2)
                emptyList()
            }
        }
    }

    private suspend fun getRandomAlbums(): List<MediaItem> {
        return try {
            val albums = api.getAlbumsID3(AlbumListType.Random, size = 120)
            albums.map {
                val item = createAlbumItem(it)
                mediaItems.put(item.mediaId, item)
                item
            }
        } catch (e: Exception) {
            try {
                val albums = api.getAlbums(AlbumListType.Random, size = 120)
                albums.map {
                    val item = createAlbumItem(it)
                    mediaItems.put(item.mediaId, item)
                    item
                }
            } catch (e2: Exception) {
                Log.e(LOG_MARKER, "Failed to get random albums", e2)
                emptyList()
            }
        }
    }

    private suspend fun getFavourites(): List<MediaItem> {
        return try {
            val starred = api.getStarredID3()
            val items = mutableListOf<MediaItem>()

            starred.artists.forEach {
                val item = createArtistItem(it, context.getString(R.string.artists))
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            starred.albums.forEach {
                val item = createAlbumItem(it, context.getString(R.string.albums))
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            starred.songs.forEach {
                val item = createSongItem(it, context.getString(R.string.tracks), parent = FAVOURITES)
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            items
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Failed to get favourites", e)
            emptyList()
        }
    }

    private suspend fun getPlaylists(): List<MediaItem> {
        return try {
            val playlists = api.getPlaylists()
            playlists.map {
                val item = createPlaylistItem(it)
                mediaItems.put(item.mediaId, item)
                item
            }
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Failed to get playlists", e)
            emptyList()
        }
    }

    private suspend fun getItemChildren(id: String): List<MediaItem> {
        val currentItem = getItem(id)

        if (currentItem.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ARTIST) {
            return try {
                val artist = api.getArtist(id)
                val albums = artist.album
                albums.map {
                    val item = createAlbumItem(it)
                    mediaItems.put(item.mediaId, item)
                    item
                }
            } catch (e: Exception) {
                Log.e(LOG_MARKER, "Failed to get children of $id", e)
                emptyList()
            }
        }

        if (currentItem.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_ALBUM) {
            return try {
                val album = api.getAlbum(id)
                val songs = album.songs
                songs.map {
                    val item = createSongItem(it, parent = id)
                    mediaItems.put(item.mediaId, item)
                    item
                }
            } catch (e: Exception) {
                Log.e(LOG_MARKER, "Failed to get children of $id", e)
                emptyList()
            }
        }

        if (currentItem.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_PLAYLIST) {
            return try {
                val playlist = api.getPlaylist(id)
                val entries = playlist.songs
                entries.map {
                    val item = createPlaylistEntryItem(it, parent = id)
                    mediaItems.put(item.mediaId, item)
                    item
                }
            } catch (e: Exception) {
                Log.e(LOG_MARKER, "Failed to get children of $id", e)
                emptyList()
            }
        }

        return emptyList()
    }

    private suspend fun fetchItemFromSubsonic(id: String): MediaItem? {
        try {
            val song = api.getSong(id)
            return createSongItem(song)
        } catch (e: Exception) {
            Log.d(LOG_MARKER, "Lookup of $id failed", e)
        }

        try {
            val album = api.getAlbum(id)
            return createAlbumItem(album)
        } catch (e: Exception) {
            Log.d(LOG_MARKER, "Lookup of $id failed", e)
        }

        try {
            val artist = api.getArtist(id)
            return createArtistItem(artist)
        } catch (e: Exception) {
            Log.d(LOG_MARKER, "Lookup of $id failed", e)
        }

        try {
            val playlist = api.getPlaylist(id)
            return createPlaylistItem(playlist)
        } catch (e: Exception) {
            Log.d(LOG_MARKER, "Lookup of $id failed", e)
        }

        return null
    }

    override suspend fun search(query: String): List<MediaItem> {
        return try {
            val result = api.searchID3(
                query = query,
                artistCount = 10,
                albumCount = 10,
                songCount = 20
            )
            val items = mutableListOf<MediaItem>()

            result.artists.forEach {
                val item = createArtistItem(it, context.getString(R.string.artists))
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            result.albums.forEach {
                val item = createAlbumItem(it, context.getString(R.string.albums))
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            result.songs.forEach {
                val item = createSongItem(it, context.getString(R.string.tracks))
                mediaItems.put(item.mediaId, item)
                items.add(item)
            }

            items
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Search failed", e)
            emptyList()
        }
    }

    override suspend fun setRating(mediaId: String, rating: Rating) {
        val isFavorite = rating == HeartRating(true)
        Log.i(LOG_MARKER, "Subsonic: Setting rating $rating for $mediaId")
        try {
            if (isFavorite) {
                api.star(mediaId)
            } else {
                api.unstar(mediaId)
            }
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Rating failed for $mediaId", e)
        }
    }

    override suspend fun reportPlaybackStart(mediaItem: MediaItem) {
        try {
            Log.i(LOG_MARKER, "Subsonic: Playback start for ${mediaItem.mediaId}")
            api.scrobble(mediaItem.mediaId, submission = false)
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Playback start scrobble failed", e)
        }
    }

    override suspend fun reportPlaybackStopped(mediaItem: MediaItem, positionMs: Long) {
        try {
            Log.i(LOG_MARKER, "Subsonic: Playback stopped for ${mediaItem.mediaId}")
            api.scrobble(mediaItem.mediaId, submission = true)
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Playback stop scrobble failed", e)
        }
    }

    private fun createRootNode(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Root")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()

        return MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createCategoryNode(id: String, label: String, iconName: String, mediaType: @MediaMetadata.MediaType Int): MediaItem {
        val extras = Bundle()
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )

        val metadata = MediaMetadata.Builder()
            .setTitle(label)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri("android.resource://be.bendardenne.jellyfin.aaos/drawable/$iconName".toUri())
            .setExtras(extras)
            .setMediaType(mediaType)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createArtistItem(artist: Artist, group: String? = null): MediaItem {
        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )

        val artCover = artist.coverArtId ?: artist.artistImageUrl
        val artUri = artCover?.let { getArtUri(it) }

        val metadata = MediaMetadata.Builder()
            .setTitle(artist.name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(artUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(artist.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createAlbumItem(album: Album, group: String? = null): MediaItem {
        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }

        val preferenceIsExpand = PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString(PREF_ALBUM_BEHAVIOUR, PLAY) == EXPAND

        val artUri = album.coverArtId?.let { getArtUri(it) }

        val metadata = MediaMetadata.Builder()
            .setTitle(album.name)
            .setAlbumArtist(album.artistName)
            .setIsBrowsable(preferenceIsExpand)
            .setIsPlayable(!preferenceIsExpand)
            .setArtworkUri(artUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(album.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createPlaylistItem(playlist: Playlist, group: String? = null): MediaItem {
        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }

        val artUri = playlist.coverArtId?.let { getArtUri(it) }

        val metadata = MediaMetadata.Builder()
            .setTitle(playlist.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(playlist.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createSongItem(
        song: Song,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        val coverArtId = song.coverArtId ?: song.albumId ?: song.id
        val artUri = getArtUri(coverArtId)

        val preferenceBitrate = PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString(PREF_BITRATE, DIRECT_STREAM)!!

        val bitrate = preferenceBitrate.toIntOrNull()?.takeIf { it > 0 } ?: 0
        val streamUrl = api.getStreamUrl(
            song.id,
            maxBitRate = if (preferenceBitrate == DIRECT_STREAM) 0 else bitrate
        )

        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        if (parent != null) {
            extras.putString(PARENT_KEY, parent)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setAlbumTitle(song.albumTitle)
            .setAlbumArtist(song.artistName)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artUri)
            .setUserRating(HeartRating(song.starredAt != null))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                val duration = song.duration
                if (duration != null && duration.inWholeMilliseconds > 0) {
                    setDurationMs(duration.inWholeMilliseconds)
                }
            }
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .setUri(streamUrl.toUri())
            .build()
    }

    private fun createPlaylistEntryItem(
        entry: Song,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        val coverArtId = entry.coverArtId ?: entry.albumId ?: entry.id
        val artUri = getArtUri(coverArtId)

        val preferenceBitrate = PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString(PREF_BITRATE, DIRECT_STREAM)!!

        val bitrate = preferenceBitrate.toIntOrNull()?.takeIf { it > 0 } ?: 0
        val streamUrl = api.getStreamUrl(
            entry.id,
            maxBitRate = if (preferenceBitrate == DIRECT_STREAM) 0 else bitrate
        )

        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        if (parent != null) {
            extras.putString(PARENT_KEY, parent)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setAlbumTitle(entry.albumTitle)
            .setAlbumArtist(entry.artistName)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artUri)
            .setUserRating(HeartRating(entry.starredAt != null))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                val duration = entry.duration
                if (duration != null && duration.inWholeMilliseconds > 0) {
                    setDurationMs(duration.inWholeMilliseconds)
                }
            }
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(entry.id)
            .setMediaMetadata(metadata)
            .setUri(streamUrl.toUri())
            .build()
    }

    private fun getArtUri(coverArtId: String): android.net.Uri {
        val remoteUrl = api.getCoverArtUrl(coverArtId, size = artSize.toString(), auth = true)
        val cacheKey = subsonicArtworkCacheKey(serverUrl, username, coverArtId, artSize)
        return AlbumArtContentProvider.mapUri(remoteUrl.toUri(), cacheKey)
    }
}

internal fun subsonicArtworkCacheKey(
    serverUrl: String,
    username: String,
    coverArtId: String,
    size: Int
): String {
    // Length prefixes keep identities unambiguous even when values contain delimiters.
    return listOf("subsonic-cover-art", serverUrl, username, coverArtId, size.toString())
        .joinToString("") { "${it.length}:$it" }
}
