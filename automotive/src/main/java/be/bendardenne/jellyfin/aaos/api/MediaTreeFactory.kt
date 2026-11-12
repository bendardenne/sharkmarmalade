package be.bendardenne.jellyfin.aaos.api

import android.content.Context
import android.util.Log
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.ServerType
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.Jellyfin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaTreeFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountManager: JellyfinAccountManager,
    private val jellyfin: Jellyfin
) {
    private var mediaTree: MediaTree? = null

    @Synchronized
    fun getTree(artSize: Int = 512): MediaTree {
        var current = mediaTree
        val type = accountManager.serverType
        if (current == null ||
            (type == ServerType.JELLYFIN && current !is JellyfinTree) ||
            (type == ServerType.SUBSONIC && current !is SubsonicTree)
        ) {
            Log.i(LOG_MARKER, "Creating tree of $type")
            if (current is SubsonicTree) {
                current.close()
            }

            current = when (type) {
                ServerType.JELLYFIN -> JellyfinTree(context, accountManager, jellyfin, artSize)
                ServerType.SUBSONIC -> SubsonicTree(context, accountManager, artSize)
            }
            mediaTree = current
        }
        return current
    }

    @Synchronized
    fun invalidate() {
        val current = mediaTree
        if (current is SubsonicTree) {
            current.close()
        }
        mediaTree = null
    }
}
