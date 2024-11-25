package be.bendardenne.jellyfin.aaos

import java.util.UUID

object UUIDConverter {

    fun hyphenate(jellyfinUUID: String): UUID {
        return UUID.fromString(
            jellyfinUUID
                .replaceRange(20, 20, "-")
                .replaceRange(16, 16, "-")
                .replaceRange(12, 12, "-")
                .replaceRange(8, 8, "-")
        )
    }

    fun dehyphenate(javaUUID: UUID): String {
        return javaUUID.toString().replace("-", "")
    }

}