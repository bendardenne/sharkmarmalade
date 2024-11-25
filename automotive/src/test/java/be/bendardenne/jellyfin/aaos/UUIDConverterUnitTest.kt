package be.bendardenne.jellyfin.aaos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UUIDConverterUnitTest {
    @Test
    fun test_JellyfinUUIDToJava() {
        assertEquals(
            "7e64e319-657a-9516-ec78-490da03edccb",
            be.bendardenne.jellyfin.aaos.UUIDConverter.hyphenate("7e64e319657a9516ec78490da03edccb")
                .toString()
        )
    }
}