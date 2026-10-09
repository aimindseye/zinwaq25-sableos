package org.vaachak.reader.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted

object AppCoroutineConfig {
    @Volatile
    var mainOverride: CoroutineDispatcher? = null

    @Volatile
    var ioOverride: CoroutineDispatcher? = null

    @Volatile
    var defaultOverride: CoroutineDispatcher? = null

    @Volatile
    var sharingStartedOverride: SharingStarted? = null

    @Volatile
    var bookshelfStartupSyncDelayMsOverride: Long? = null

    val main: CoroutineDispatcher
        get() = mainOverride ?: Dispatchers.Main

    val io: CoroutineDispatcher
        get() = ioOverride ?: Dispatchers.IO

    val default: CoroutineDispatcher
        get() = defaultOverride ?: Dispatchers.Default

    val whileSubscribed: SharingStarted
        get() = sharingStartedOverride ?: SharingStarted.WhileSubscribed(5_000)

    val eagerly: SharingStarted
        get() = sharingStartedOverride ?: SharingStarted.Eagerly

    val lazily: SharingStarted
        get() = sharingStartedOverride ?: SharingStarted.Lazily

    val bookshelfStartupSyncDelayMs: Long
        get() = bookshelfStartupSyncDelayMsOverride ?: 2_000L

    fun reset() {
        mainOverride = null
        ioOverride = null
        defaultOverride = null
        sharingStartedOverride = null
        bookshelfStartupSyncDelayMsOverride = null
    }
}