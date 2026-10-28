package be.bendardenne.jellyfin.aaos.api

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import be.bendardenne.jellyfin.aaos.AlbumArtContentProvider
import be.bendardenne.jellyfin.aaos.MediaItemFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/** Maps Jellyfin [BaseItemDto]s onto [MediaItem]s using the shared [MediaItemFactory]. */
class JellyfinItemMapper(
    private val factory: MediaItemFactory,
    private val api: ApiClient,
    private val artSize: Int
) {

    fun create(dto: BaseItemDto, group: String? = null, parent: String? = null): MediaItem {
        val id = dto.id.toString()
        return when (dto.type) {
            BaseItemKind.MUSIC_ARTIST ->
                factory.artist(id, dto.name, artUri(dto.id), dto.albumArtist, group)

            BaseItemKind.MUSIC_ALBUM ->
                factory.album(id, dto.name, dto.albumArtist, artUri(dto.id), group)

            BaseItemKind.PLAYLIST -> factory.playlist(id, dto.name, artUri(dto.id), group)
            BaseItemKind.AUDIO -> track(dto, group, parent)
            else -> throw UnsupportedOperationException("Can't create mediaItem for ${dto.type}")
        }
    }

    private fun track(dto: BaseItemDto, group: String?, parent: String?): MediaItem {
        // Use the album ID for album art, if present.
        // This way, all tracks in an album have the same URI, which saves some downloads.
        // It probably makes sense most of the time, unless someone uses different images for
        // tracks within the same album, which seems weird.
        val artUrl = artUri(dto.albumId ?: dto.id)
        val bitrate = factory.preferredBitrate()

        // Nice-to-have: it would be nice to force transcoding when the codec is not supported
        //  (eg ALAC).
        //  This would require that we know the codec upfront. we currently don't have access to
        //  it because our BaseItemDtos are mostly fetched via getItemChildren, which queries the
        //  /Items endpoint, which does not include the codec (mediaSources/mediaStreams) in its
        //  response.

        // When a file is not in this list of containers, it will always be transcoded.
        // This list are containers that I tested.
        val allowedContainers = listOf("flac", "mp3", "m4a", "aac", "ogg")
        val audioStream = api.universalAudioApi.getUniversalAudioStreamUrl(
            dto.id,
            container = allowedContainers,
            audioBitRate = bitrate,
            maxStreamingBitrate = bitrate,
            transcodingContainer = "mp3",
            audioCodec = "mp3",
        )

        return factory.track(
            id = dto.id.toString(),
            title = dto.name,
            albumArtist = dto.albumArtist,
            artworkUri = artUrl,
            streamUri = audioStream.toUri(),
            isFavourite = dto.userData?.isFavorite == true,
            durationMs = dto.runTimeTicks?.div(10_000),
            group = group,
            parent = parent
        )
    }

    private fun artUri(id: UUID): Uri {
        val artUrl = ImageApi(api).getItemImageUrl(
            id,
            ImageType.PRIMARY,
            quality = 90,
            maxWidth = artSize,
            maxHeight = artSize,
        )
        return AlbumArtContentProvider.mapUri(artUrl.toUri())
    }
}
