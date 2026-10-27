package be.bendardenne.jellyfin.aaos

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


/**
 * ContentProvider for album arts.
 */
class AlbumArtContentProvider : ContentProvider() {

    private val client = OkHttpClient()

    companion object {
        private val uriMap = ConcurrentHashMap<Uri, Uri>()
        private val inProgress = HashMap<Uri, CountDownLatch>()

        private fun md5(input: String): String {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Generates a local FS URI for an album art. The original online URI is stored for later
         * retrieval.
         *
         * [cacheKey] identifies the artwork independently of changing authentication parameters.
         * The original [uri] is retained, and refreshed on each call, for authenticated downloads.
         */
        fun mapUri(uri: Uri, cacheKey: String = uri.toString()): Uri {
            if (uri == Uri.EMPTY) return Uri.EMPTY
            val path = md5(cacheKey)
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

        if (file.exists() && file.length() > 0) {
            Log.d(LOG_MARKER, "Returning existing file for $remoteUri: $file")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // Several threads may request the same image (typical when listing an album).
        // To avoid firing multiple downloads, the first thread makes the request, others will wait.
        val newDownload = CountDownLatch(1)
        val download = synchronized(inProgress) {
            if (file.exists() && file.length() > 0) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            inProgress.getOrPut(uri) { newDownload }
        }

        if (download !== newDownload) {
            // Never wait while holding the lock needed by the downloading thread's finally block.
            if (!download.await(15, TimeUnit.SECONDS)) {
                Log.w(LOG_MARKER, "Timed out waiting for artwork $uri")
                return null
            }
            if (file.exists() && file.length() > 0) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            Log.w(LOG_MARKER, "Artwork download did not produce a cached file for $uri")
            return null
        }

        var tmpFile: File? = null
        Log.d(LOG_MARKER, "Downloading $remoteUri ...")
        try {
            tmpFile = File.createTempFile("sharkmarmalade-albumart", ".png", context.cacheDir)
            val request = Request.Builder()
                .url(remoteUri.toString())
                .build()
            client.newCall(request).execute().use {
                if (it.body != null && it.code == 200) {
                    Log.d(LOG_MARKER, "Downloaded $remoteUri")
                    val source = it.body!!.source()
                    source.request(Long.MAX_VALUE)

                    tmpFile.sink().buffer().use { sink ->
                        sink.writeAll(source)
                    }

                    if (!tmpFile.renameTo(file)) {
                        throw IOException("Failed to cache artwork at $file")
                    }
                } else {
                    Log.w(LOG_MARKER, "Failed to download $remoteUri: \n ${it.code} - ${it.body.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Failed to download $remoteUri", e)
        } finally {
            synchronized(inProgress) {
                download.countDown()
                inProgress.remove(uri)
            }
            tmpFile?.let {
                if (it.exists() && !it.delete()) {
                    Log.w(LOG_MARKER, "Failed to remove temporary artwork file $it")
                }
            }
        }

        if (file.exists() && file.length() > 0) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return null
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
