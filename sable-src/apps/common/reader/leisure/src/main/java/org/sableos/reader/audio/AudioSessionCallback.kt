package org.sableos.reader.audio

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.sableos.reader.engine.audio.PlaybackSourcePolicy

/**
 * Policy for who may talk to the playback session, and what they may do. The service is exported, so this callback is
 * the access gate: it rejects every controller that is neither this app nor system-trusted.
 *
 * - Only this app and system-trusted controllers (notification, lock screen, Bluetooth) may connect; anyone else is
 *   rejected.
 * - Only this app may use the private commands that open a book, set the sleep timer and set skip intervals.
 * - Nobody can put items into the playlist from outside: the service builds it from the library. A controller asking
 *   for items, or for a non-local source, is refused. Browsing exposes no content (empty library tree).
 */
@OptIn(UnstableApi::class)
internal class AudioSessionCallback(
    private val ownPackage: String,
    private val onOpenBook: (Bundle) -> ListenableFuture<SessionResult>,
    private val onSetSleep: (Bundle) -> ListenableFuture<SessionResult>,
    private val onSetSkip: (Bundle) -> ListenableFuture<SessionResult>,
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val isOwn = controller.packageName == ownPackage
        return if (isOwn || controller.isTrusted) {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().apply {
                if (isOwn) {
                    add(AudioSessionContract.openBook)
                    add(AudioSessionContract.setSleep)
                    add(AudioSessionContract.setSkip)
                }
            }.build()
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
        } else {
            MediaSession.ConnectionResult.reject()
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val handler = when {
            controller.packageName != ownPackage -> null
            customCommand.customAction == AudioSessionContract.ACTION_OPEN_BOOK -> onOpenBook
            customCommand.customAction == AudioSessionContract.ACTION_SET_SLEEP -> onSetSleep
            customCommand.customAction == AudioSessionContract.ACTION_SET_SKIP -> onSetSkip
            else -> null
        }
        return handler?.invoke(args) ?: ImmediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFailedFuture(
            UnsupportedOperationException("The playlist is built from the library; controllers cannot add items"),
        )

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle("Sable Reader").setIsBrowsable(false).setIsPlayable(false).build(),
            )
            .build()
        return ImmediateFuture(LibraryResult.ofItem(root, params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        ImmediateFuture(LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params))

    companion object {
        const val ROOT_ID = "sable_reader_root"

        /** True when [uri] may be given to the player: a local `content:` or `file:` source, never the network. */
        fun isPlayable(uri: String): Boolean = PlaybackSourcePolicy.isLocal(uri)
    }
}
