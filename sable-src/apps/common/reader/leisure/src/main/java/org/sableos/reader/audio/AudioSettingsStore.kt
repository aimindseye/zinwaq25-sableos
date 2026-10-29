package org.sableos.reader.audio

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudioSettingsCodec
import org.vaachak.reader.core.data.repository.SettingsRepository

/** An audiobook's own playback choices, or the Settings defaults when it has none yet. */
class AudioSettingsStore @Inject constructor(
    private val settingsDao: ItemViewSettingsDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun load(bookHash: String, profileId: String): AudioPlaybackSettings =
        settingsDao.get(bookHash, profileId)?.settingsJson?.let(AudioSettingsCodec::decode)
            ?: settingsRepository.audioDefaults.first()

    suspend fun save(bookHash: String, profileId: String, settings: AudioPlaybackSettings) {
        settingsDao.upsert(ItemViewSettingsEntity(bookHash, profileId, AudioSettingsCodec.encode(settings)))
    }
}
