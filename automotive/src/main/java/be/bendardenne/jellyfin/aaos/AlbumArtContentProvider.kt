package be.bendardenne.jellyfin.aaos

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

const val DOWNLOAD_TIMEOUT_SECONDS = 30L

/**
 * ContentProvider for album arts. Adapted from:
 *
 * https://github.com/android/uamp/blob/99e44c1c5106218c62eff552b64bbc12f1883a22/common/src/main/java/com/example/android/uamp/media/library/AlbumArtContentProvider.kt
 */
class AlbumArtContentProvider : ContentProvider() {

    companion object {
        private val uriMap = mutableMapOf<Uri, Uri>()

        fun mapUri(uri: Uri): Uri {
            val path = uri.encodedPath?.substring(1)?.replace('/', ':') ?: return Uri.EMPTY
            val contentUri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("be.bendardenne.jellyfin.aaos")
                .path(path)
                .build()
            uriMap[contentUri] = uri
            return contentUri
        }
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = this.context ?: return null
        val remoteUri = uriMap[uri] ?: throw FileNotFoundException(uri.path)
        val file = File(context.cacheDir, uri.path)

        if (!file.exists()) {
            // Use Glide to download the album art.
            val cacheFile = Glide.with(context)
                .downloadOnly()
                .load(remoteUri)
                .thumbnail()
                .submit()
                .get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // Rename the file Glide created to match our own scheme.
            cacheFile.renameTo(file)
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    override fun getType(uri: Uri): String? = null
}