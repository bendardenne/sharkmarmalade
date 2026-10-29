package be.bendardenne.jellyfin.aaos.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubsonicArtworkCacheKeyTest {
    @Test
    fun sameArtworkHasStableIdentity() {
        assertEquals(
            subsonicArtworkCacheKey("https://music.example", "alice", "album-1", 512),
            subsonicArtworkCacheKey("https://music.example", "alice", "album-1", 512)
        )
    }

    @Test
    fun eachIdentityComponentSeparatesCachedArtwork() {
        val key = subsonicArtworkCacheKey("https://music.example", "alice", "album-1", 512)
        assertNotEquals(key, subsonicArtworkCacheKey("https://other.example", "alice", "album-1", 512))
        assertNotEquals(key, subsonicArtworkCacheKey("https://music.example", "bob", "album-1", 512))
        assertNotEquals(key, subsonicArtworkCacheKey("https://music.example", "alice", "album-2", 512))
        assertNotEquals(key, subsonicArtworkCacheKey("https://music.example", "alice", "album-1", 256))
    }

    @Test
    fun delimitersCannotCauseIdentityCollisions() {
        assertNotEquals(
            subsonicArtworkCacheKey("https://music.example", "alice:album", "1", 512),
            subsonicArtworkCacheKey("https://music.example", "alice", "album:1", 512)
        )
    }
}
