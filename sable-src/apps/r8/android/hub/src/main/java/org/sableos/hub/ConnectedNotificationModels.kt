package org.sableos.hub

data class ConnectedNotificationRecord(
    val id: String,
    val key: ConnectedAppKey,
    val notificationKey: String,
    val conversationId: String,
    val conversationTitle: String,
    val sourceLabel: String,
    val senderLabel: String?,
    val body: String?,
    val timestampMillis: Long,
    val incoming: Boolean,
) {
    init {
        require(id.isNotBlank())
        require(notificationKey.isNotBlank())
        require(conversationId.isNotBlank())
        require(conversationTitle.isNotBlank())
        require(sourceLabel.isNotBlank())
        require(timestampMillis >= 0L)
    }
}

object ConnectedNotificationRetention {
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    fun shouldKeep(
        record: ConnectedNotificationRecord,
        retention: HistoryRetention,
        nowMillis: Long,
    ): Boolean {
        val days = retention.days ?: return true
        val cutoff = nowMillis - days * MILLIS_PER_DAY
        return record.timestampMillis >= cutoff
    }
}
