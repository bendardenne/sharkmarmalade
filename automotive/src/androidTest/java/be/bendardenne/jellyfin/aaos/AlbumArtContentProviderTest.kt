package be.bendardenne.jellyfin.aaos

import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import be.bendardenne.jellyfin.aaos.api.subsonicArtworkCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AlbumArtContentProviderTest {
    @Test
    fun changingSubsonicAuthenticationKeepsTheSameContentUri() {
        val key = subsonicArtworkCacheKey("https://music.example", "alice", "album-1", 512)
        val first = Uri.parse("https://music.example/rest/getCoverArt?id=album-1&s=first&t=token1")
        val second = Uri.parse("https://music.example/rest/getCoverArt?id=album-1&s=second&t=token2")

        assertEquals(
            AlbumArtContentProvider.mapUri(first, key),
            AlbumArtContentProvider.mapUri(second, key)
        )
    }

    @Test
    fun defaultKeyStillUsesTheCompleteUrl() {
        val first = Uri.parse("https://music.example/Items/1/Images/Primary?tag=first")
        val second = Uri.parse("https://music.example/Items/1/Images/Primary?tag=second")

        assertEquals(AlbumArtContentProvider.mapUri(first), AlbumArtContentProvider.mapUri(first))
        assertNotEquals(AlbumArtContentProvider.mapUri(first), AlbumArtContentProvider.mapUri(second))
    }

    @Test
    fun emptyUriRemainsEmpty() {
        assertEquals(Uri.EMPTY, AlbumArtContentProvider.mapUri(Uri.EMPTY, "unused"))
    }

    @Test
    fun refreshedUrlIsUsedAndConcurrentRequestsShareTheCachedDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AlbumArtContentProvider().apply {
            attachInfo(context, ProviderInfo())
        }
        val key = subsonicArtworkCacheKey("http://localhost", "alice", UUID.randomUUID().toString(), 512)
        val contentUri = AlbumArtContentProvider.mapUri(Uri.parse("http://127.0.0.1:1/old"), key)
        val cachedFile = File(context.cacheDir, contentUri.path!!)
        val executor = Executors.newFixedThreadPool(3)
        val requestReceived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        fun readArtwork(): String {
            val descriptor = requireNotNull(provider.openFile(contentUri, "r"))
            return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
                it.readText()
            }
        }

        try {
            ServerSocket(0).use { server ->
                server.soTimeout = 5000
                val response = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        requestReceived.countDown()
                        check(releaseResponse.await(5, TimeUnit.SECONDS))
                        socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 7\r\nConnection: close\r\n\r\nartwork"
                                .toByteArray(Charsets.UTF_8)
                        )
                        requestLine
                    }
                }
                val latestUrl = Uri.parse("http://127.0.0.1:${server.localPort}/art?s=new&t=new")
                assertEquals(contentUri, AlbumArtContentProvider.mapUri(latestUrl, key))
                val first = executor.submit<String> { readArtwork() }
                assertTrue(requestReceived.await(5, TimeUnit.SECONDS))
                AlbumArtContentProvider.mapUri(latestUrl.buildUpon().appendQueryParameter("s", "next").build(), key)
                val second = executor.submit<String> { readArtwork() }
                releaseResponse.countDown()

                assertEquals("artwork", first.get(5, TimeUnit.SECONDS))
                assertEquals("artwork", second.get(5, TimeUnit.SECONDS))
                assertEquals("GET /art?s=new&t=new HTTP/1.1", response.get(5, TimeUnit.SECONDS))
            }
            // The server is closed; another authenticated URL must still reuse the cached file.
            AlbumArtContentProvider.mapUri(Uri.parse("http://127.0.0.1:1/art?s=again&t=again"), key)
            assertEquals("artwork", readArtwork())
        } finally {
            releaseResponse.countDown()
            executor.shutdownNow()
            cachedFile.delete()
        }
    }
}
