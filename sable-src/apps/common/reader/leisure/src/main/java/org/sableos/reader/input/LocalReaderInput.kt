package org.sableos.reader.input

import androidx.compose.runtime.compositionLocalOf

/** Provided once by the activity; reading surfaces register with it while they are on screen. */
val LocalReaderKeyDispatcher = compositionLocalOf { ReaderKeyDispatcher() }

val LocalEditableFocus = compositionLocalOf { EditableFocusState() }
