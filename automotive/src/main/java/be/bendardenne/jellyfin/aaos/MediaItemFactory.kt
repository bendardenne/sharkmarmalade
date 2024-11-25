package be.bendardenne.jellyfin.aaos

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class MediaItemFactory(private val jellyfinApi: ApiClient) {

    companion object {
        const val ROOT_ID = "ROOT_ID"
        const val LATEST_ALBUMS = "LATEST_ALBUMS_ID"
        const val RANDOM_ALBUMS = "RANDOM_ALBUMS_ID"
        const val FAVOURITES = "FAVOURITES_ID"

        const val CONTEXT_KEY = "CONTEXT_KEY"
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
        return albumCategory(LATEST_ALBUMS, "Latest")
    }

    fun randomAlbums(): MediaItem {
        return albumCategory(RANDOM_ALBUMS, "Random")
    }


    @OptIn(UnstableApi::class)
    private fun albumCategory(id: String, label: String): MediaItem {
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
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    @OptIn(UnstableApi::class)
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
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()

        return MediaItem.Builder()
            .setMediaId(FAVOURITES)
            .setMediaMetadata(metadata)
            .build()
    }

    fun forAlbum(item: BaseItemDto): MediaItem {
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(item.id, ImageType.PRIMARY)
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setAlbumArtist(item.albumArtist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(localUrl)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .build()

        return MediaItem.Builder()
            .setMediaId(UUIDConverter.dehyphenate(item.id))
            .setMediaMetadata(metadata)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun forTrack(item: BaseItemDto): MediaItem {
        val artUrl = ImageApi(jellyfinApi).getItemImageUrl(item.id, ImageType.PRIMARY)
        val localUrl = AlbumArtContentProvider.mapUri(Uri.parse(artUrl))

        var audioStream =
            jellyfinApi.universalAudioApi.getUniversalAudioStreamUrl(
                item.id,
                container = listOf("mp3"),      // TODO flac ?
                audioCodec = "mp3",
            )

        // FIXME hack, due to Jellyfin API using UUID object which
        audioStream = audioStream.replace("-", "")

        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setAlbumArtist(item.albumArtist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(localUrl)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setDurationMs(item.runTimeTicks?.div(10_000))
            .build()

        return MediaItem.Builder()
            .setMediaId(UUIDConverter.dehyphenate(item.id))
            .setMediaMetadata(metadata)
            .setUri(audioStream)
            .build()
    }
}