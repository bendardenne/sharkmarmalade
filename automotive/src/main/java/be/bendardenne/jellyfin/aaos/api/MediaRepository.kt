package be.bendardenne.jellyfin.aaos.api

import androidx.media3.common.MediaItem
import androidx.media3.common.Rating

interface MediaRepository {
    suspend fun getItem(id: String): MediaItem
    suspend fun getChildren(id: String): List<MediaItem>
    suspend fun search(query: String): List<MediaItem>
    suspend fun setRating(mediaId: String, rating: Rating)
    suspend fun reportPlaybackStart(mediaItem: MediaItem)
    suspend fun reportPlaybackStopped(mediaItem: MediaItem, positionMs: Long)
    fun evictCache()
    fun onLogin()
}
