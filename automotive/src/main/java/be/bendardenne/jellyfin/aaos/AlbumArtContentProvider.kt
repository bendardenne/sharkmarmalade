package be.bendardenne.jellyfin.aaos

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileNotFoundException


/**
 * ContentProvider for album arts.
 */
class AlbumArtContentProvider : ContentProvider() {

    private val client = OkHttpClient()

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
            val tmpFile = File.createTempFile("sharkmarmalade-albumart", ".png", context.cacheDir)

            val request: Request = Request.Builder()
                .url(remoteUri.toString())
                .build()


            // TODO  Often, the same URL will be requested multiple times, in several threads.
            // We could perform only one request and return the same file in each thread.
            client.newCall(request).execute().use {
                if (it.body != null && it.code == 200) {
                    Log.i(LOG_MARKER, "Downloaded $remoteUri")
                    val source = it.body!!.source()
                    source.request(Long.MAX_VALUE)

                    val sink = tmpFile.sink().buffer()
                    sink.writeAll(source)
                    sink.flush()
                    sink.close()
                }
            }

            tmpFile.renameTo(file)
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