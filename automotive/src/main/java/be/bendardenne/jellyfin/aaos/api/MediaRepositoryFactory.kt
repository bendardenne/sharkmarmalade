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
class MediaRepositoryFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountManager: JellyfinAccountManager,
    private val jellyfin: Jellyfin
) {
    private var repository: MediaRepository? = null

    @Synchronized
    fun getRepository(artSize: Int = 512): MediaRepository {
        var current = repository
        val type = accountManager.serverType
        if (current == null ||
            (type == ServerType.JELLYFIN && current !is JellyfinRepository) ||
            (type == ServerType.SUBSONIC && current !is SubsonicRepository)
        ) {
            Log.i(LOG_MARKER, "Creating repository $type")
            if (current is SubsonicRepository) {
                current.close()
            }

            current = when (type) {
                ServerType.JELLYFIN -> JellyfinRepository(context, accountManager, jellyfin, artSize)
                ServerType.SUBSONIC -> SubsonicRepository(context, accountManager, artSize)
            }
            repository = current
        }
        return current
    }

    @Synchronized
    fun invalidate() {
        val current = repository
        if (current is SubsonicRepository) {
            current.close()
        }
        repository = null
    }
}
