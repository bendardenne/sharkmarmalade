package be.bendardenne.jellyfin.aaos

import java.util.UUID

/**
 * Returns a Jellyfin-compatible de-hyphenated version of this UUID.
 */
fun UUID.dehyphenate(): String = toString().replace("-", "")
