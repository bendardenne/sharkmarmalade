package be.bendardenne.jellyfin.aaos

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.preference.PreferenceManager
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.DIRECT_STREAM
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.EXPAND
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PLAY
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PREF_ALBUM_BEHAVIOUR
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.PREF_BITRATE

/**
 * Server-agnostic factory of [MediaItem]s. Server-specific trees map their own models onto
 * the parameters of these builders.
 */
@OptIn(UnstableApi::class)
class MediaItemFactory(private val context: Context) {

    companion object {
        const val ROOT_ID = "ROOT_ID"
        const val LATEST_ALBUMS = "LATEST_ALBUMS_ID"
        const val RANDOM_ALBUMS = "RANDOM_ALBUMS_ID"
        const val FAVOURITES = "FAVOURITES_ID"
        const val PLAYLISTS = "PLAYLISTS_ID"
        const val PARENT_KEY = "PARENT_KEY"
    }

    /** The bitrate chosen in the preferences, or null for direct streaming. */
    fun preferredBitrate(): Int? {
        val preference = PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString(PREF_BITRATE, DIRECT_STREAM)!!

        return if (preference == DIRECT_STREAM) null else preference.toInt()
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
            .setArtworkUri("android.resource://be.bendardenne.jellyfin.aaos/drawable/$icon".toUri())
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
            .setArtworkUri("android.resource://be.bendardenne.jellyfin.aaos/drawable/star_filled".toUri())
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
            .setArtworkUri("android.resource://be.bendardenne.jellyfin.aaos/drawable/playlists".toUri())
            .setExtras(extras)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            .build()

        return MediaItem.Builder()
            .setMediaId(PLAYLISTS)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun groupExtras(group: String?): Bundle {
        val extras = Bundle()
        if (group != null) {
            extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        return extras
    }

    fun artist(
        id: String,
        name: String?,
        artworkUri: Uri?,
        albumArtist: String? = null,
        group: String? = null
    ): MediaItem {
        val extras = groupExtras(group)
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        extras.putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )

        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setAlbumArtist(albumArtist)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun album(
        id: String,
        title: String?,
        albumArtist: String?,
        artworkUri: Uri?,
        group: String? = null
    ): MediaItem {
        val preferenceIsExpand = PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString(PREF_ALBUM_BEHAVIOUR, PLAY) == EXPAND

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setAlbumArtist(albumArtist)
            .setIsBrowsable(preferenceIsExpand)
            .setIsPlayable(!preferenceIsExpand)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .setExtras(groupExtras(group))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playlist(
        id: String,
        title: String?,
        artworkUri: Uri?,
        group: String? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setExtras(groupExtras(group))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun track(
        id: String,
        title: String?,
        albumArtist: String?,
        artworkUri: Uri?,
        streamUri: Uri,
        isFavourite: Boolean,
        durationMs: Long?,
        albumTitle: String? = null,
        group: String? = null,
        parent: String? = null
    ): MediaItem {
        val extras = groupExtras(group)
        if (parent != null) {
            extras.putString(PARENT_KEY, parent)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setAlbumTitle(albumTitle)
            .setAlbumArtist(albumArtist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artworkUri)
            .setUserRating(HeartRating(isFavourite))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setDurationMs(durationMs)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .setUri(streamUri)
            .build()
    }
}
