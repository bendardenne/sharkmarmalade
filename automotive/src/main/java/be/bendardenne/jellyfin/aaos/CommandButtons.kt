package be.bendardenne.jellyfin.aaos

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.REPEAT_COMMAND
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.SHUFFLE_COMMAND
import com.google.common.collect.ImmutableList

/**
 * Helper for creating the custom command buttons we need for a player.
 */
object CommandButtons {

    @OptIn(UnstableApi::class)
    fun createButtons(player: Player): List<CommandButton> {
        // The UI should show the icon for the active mode
        val repeatIcon = when (player.repeatMode) {
            REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            REPEAT_MODE_OFF -> CommandButton.ICON_REPEAT_OFF
            REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            else -> throw IllegalStateException("Unexpected change to Repeat mode")
        }

        val repeat =
            CommandButton.Builder(repeatIcon)
                .setDisplayName("Toggle repeat")
                .setSessionCommand(SessionCommand(REPEAT_COMMAND, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()

        val shuffleIcon =
            if (player.shuffleModeEnabled)
                CommandButton.ICON_SHUFFLE_ON
            else
                CommandButton.ICON_SHUFFLE_OFF

        val shuffle = CommandButton.Builder(shuffleIcon)
            .setDisplayName("Toggle Shuffle")
            .setSessionCommand(SessionCommand(SHUFFLE_COMMAND, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        return ImmutableList.of(shuffle, repeat)
    }

}