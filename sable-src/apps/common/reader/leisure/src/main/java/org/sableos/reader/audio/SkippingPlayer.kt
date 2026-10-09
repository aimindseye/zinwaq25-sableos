package org.sableos.reader.audio

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Wraps the ExoPlayer so the media session, the notification and Bluetooth/headset buttons use the audiobook rules:
 * skip back/forward by the title's chosen intervals across file boundaries, and next/previous go to chapters (even
 * inside one long M4B, where the playlist has a single item). Everything else is the wrapped player's.
 */
@OptIn(UnstableApi::class)
internal class SkippingPlayer(
    player: Player,
    private val playback: () -> AudiobookPlayback?,
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun getSeekBackIncrement(): Long = playback()?.skipBackMs ?: super.getSeekBackIncrement()

    override fun getSeekForwardIncrement(): Long = playback()?.skipForwardMs ?: super.getSeekForwardIncrement()

    override fun seekBack() {
        val active = playback()
        if (active != null) active.skipBack() else super.seekBack()
    }

    override fun seekForward() {
        val active = playback()
        if (active != null) active.skipForward() else super.seekForward()
    }

    override fun seekToNext() {
        val active = playback()
        if (active != null) active.nextChapter() else super.seekToNext()
    }

    override fun seekToPrevious() {
        val active = playback()
        if (active != null) active.previousChapter() else super.seekToPrevious()
    }
}
