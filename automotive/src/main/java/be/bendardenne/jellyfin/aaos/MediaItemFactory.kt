package be.bendardenne.jellyfin.aaos

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@OptIn(UnstableApi::class)
class MediaItemFactory(private val jellyfinApi: ApiClient) {

    companion object {
        const val ROOT_ID = "ROOT_ID"
        const val LATEST_ALBUMS = "LATEST_ALBUMS_ID"
        const val RANDOM_ALBUMS = "RANDOM_ALBUMS_ID"
        const val FAVOURITES = "FAVOURITES_ID"
        const val PLAYLISTS = "PLAYLISTS_ID"
        const val PARENT_KEY = "PARENT_KEY"
    }

    fun rootNode(): MediaItem {
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

    fun latestAlbums(): MediaItem {
        return albumCategory(LATEST_ALBUMS, "Latest", "schedule")
    }

    fun randomAlbums(): MediaItem {
        return albumCategory(RANDOM_ALBUMS, "Random", "casino")
    }


    private fun albumCategory(id: String, label: String, icon: String): MediaItem {
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
            .setArtworkUri(Uri.parse("android.resource://be.bendardenne.jellyfin.aaos/drawable/$icon"))
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun favourites(): MediaItem {
        val extras = Bundle()
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )


        val metadata = MediaMetadata.Builder()
            .setTitle("Favourites")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(Uri.parse("android.resource://be.bendardenne.jellyfin.aaos/drawable/star_filled"))
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()

        return MediaItem.Builder()
            .setMediaId(FAVOURITES)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playlists(): MediaItem {
        val extras = Bundle()
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )

        val metadata = MediaMetadata.Builder()
            .setTitle("Playlists")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(Uri.parse("android.resource://be.bendardenne.jellyfin.aaos/drawable/playlists"))
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            .build()

        return MediaItem.Builder()
            .setMediaId(PLAYLISTS)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun forArtist(item: BaseItemDto, group: String? = null): MediaItem {
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(
            item.id,
            ImageType.PRIMARY,
            quality = 90,
            maxWidth = 1024
        )
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

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

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setAlbumArtist(item.albumArtist)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(localUrl)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id.dehyphenate())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun forAlbum(item: BaseItemDto, group: String? = null): MediaItem {
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(
            item.id,
            ImageType.PRIMARY,
            quality = 90,
            maxWidth = 1024
        )
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setAlbumArtist(item.albumArtist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(localUrl)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id.dehyphenate())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun forPlaylist(item: BaseItemDto, group: String? = null): MediaItem {
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(
            item.id,
            ImageType.PRIMARY,
            quality = 90,
            maxWidth = 1024
        )
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(localUrl)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id.dehyphenate())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun forTrack(
        item: BaseItemDto,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        // Use the album ID for album art, if present.
        // This way, all tracks in an album have the same URI, which saves some downloads.
        // It probably makes sense most of the time, unless someone uses different images for
        // tracks within the same album, which seems weird.
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(
            item.albumId ?: item.id,
            ImageType.PRIMARY,
            quality = 90,
            maxWidth = 1024
        )
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

        var audioStream =
            jellyfinApi.universalAudioApi.getUniversalAudioStreamUrl(
                item.id,
                container = listOf("flac,mp3"),
                audioCodec = "flac,mp3",
            )

        // FIXME hack, due to Jellyfin API using UUID object
        audioStream = audioStream.replace("-", "")

        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }

        extras.putString(PARENT_KEY, parent)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setAlbumArtist(item.albumArtist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(localUrl)
            .setUserRating(HeartRating(item.userData?.isFavorite ?: false))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setDurationMs(item.runTimeTicks?.div(10_000))
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id.dehyphenate())
            .setMediaMetadata(metadata)
            .setUri(audioStream)
            .build()
    }


    fun create(
        baseItemDto: BaseItemDto,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        return when (baseItemDto.type) {
            BaseItemKind.MUSIC_ARTIST -> forArtist(baseItemDto, group)
            BaseItemKind.MUSIC_ALBUM -> forAlbum(baseItemDto, group)
            BaseItemKind.PLAYLIST -> forPlaylist(baseItemDto, group)
            BaseItemKind.AUDIO -> forTrack(baseItemDto, group, parent)
            else -> throw UnsupportedOperationException("Can't create mediaItem for ${baseItemDto.type}")
        }
    }
}