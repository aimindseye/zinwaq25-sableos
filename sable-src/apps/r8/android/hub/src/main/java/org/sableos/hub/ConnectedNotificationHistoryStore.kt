package org.sableos.hub

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal const val CONNECTED_LOCAL_REPLY_PREFIX = "local-reply:"

class ConnectedNotificationHistoryStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val helper = HistoryDatabaseHelper(appContext)

    fun load(
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ConnectedNotificationRecord> =
        synchronized(lock) {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                val retained =
                    pruneAndLoad(
                        database = database,
                        policies = policies,
                        nowMillis = nowMillis,
                    )
                database.setTransactionSuccessful()
                retained
            } finally {
                database.endTransaction()
            }
        }

    fun merge(
        records: List<ConnectedNotificationRecord>,
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (records.isEmpty()) {
            return
        }

        synchronized(lock) {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                records.forEach { record ->
                    insertRecord(
                        database = database,
                        record = record,
                    )
                }
                pruneAndLoad(
                    database = database,
                    policies = policies,
                    nowMillis = nowMillis,
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }

        notifyHubDataChanged()
    }

    fun appendLocalReply(
        notificationKey: String,
        body: String,
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val text = body.trim()
        if (text.isEmpty()) {
            return false
        }

        var inserted = false
        synchronized(lock) {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                val source =
                    queryRecords(database).firstOrNull { record ->
                        record.notificationKey == notificationKey
                    }
                val policy =
                    source
                        ?.let { record -> policies[record.key] }
                        ?.normalized()

                if (source != null && policy?.includeInMessages == true) {
                    insertRecord(
                        database = database,
                        record =
                            source.copy(
                                id =
                                    CONNECTED_LOCAL_REPLY_PREFIX +
                                        notificationKey +
                                        ":" +
                                        nowMillis +
                                        ":" +
                                        text.hashCode(),
                                senderLabel = null,
                                body = text,
                                timestampMillis = nowMillis,
                                incoming = false,
                            ),
                    )
                    pruneAndLoad(
                        database = database,
                        policies = policies,
                        nowMillis = nowMillis,
                    )
                    inserted = true
                }

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }

        if (inserted) {
            notifyHubDataChanged()
        }
        return inserted
    }

    fun clearFor(key: ConnectedAppKey) {
        synchronized(lock) {
            helper.writableDatabase.delete(
                TABLE_HISTORY,
                "$COLUMN_PACKAGE_NAME = ? AND $COLUMN_USER_SERIAL = ?",
                arrayOf(
                    key.packageName,
                    key.userSerial.toString(),
                ),
            )
        }

        notifyHubDataChanged()
    }

    private fun notifyHubDataChanged() {
        appContext.contentResolver.notifyChange(
            ConnectedAppsRepository.HISTORY_URI,
            null,
        )
        appContext.contentResolver.notifyChange(
            HubSnapshotProvider.SNAPSHOT_URI,
            null,
        )
    }

    private fun pruneAndLoad(
        database: SQLiteDatabase,
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
        nowMillis: Long,
    ): List<ConnectedNotificationRecord> {
        val allRecords = queryRecords(database)
        val retained =
            allRecords
                .asSequence()
                .filter { record ->
                    shouldRetain(
                        record = record,
                        policy = policies[record.key],
                        nowMillis = nowMillis,
                    )
                }.take(MAX_HISTORY_RECORDS)
                .toList()

        deleteRecordsExcept(
            database = database,
            allRecords = allRecords,
            retained = retained,
        )
        return retained
    }

    private fun shouldRetain(
        record: ConnectedNotificationRecord,
        policy: ConnectedAppPolicy?,
        nowMillis: Long,
    ): Boolean {
        val normalized = policy?.normalized()
        return normalized?.includeInMessages == true &&
            ConnectedNotificationRetention.shouldKeep(
                record = record,
                retention = normalized.retention,
                nowMillis = nowMillis,
            )
    }

    private fun queryRecords(database: SQLiteDatabase): List<ConnectedNotificationRecord> =
        database
            .query(
                TABLE_HISTORY,
                historyColumns,
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP_MILLIS DESC, $COLUMN_ID ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toRecord())
                    }
                }
            }

    private fun android.database.Cursor.toRecord(): ConnectedNotificationRecord =
        ConnectedNotificationRecord(
            id = getString(getColumnIndexOrThrow(COLUMN_ID)),
            key =
                ConnectedAppKey(
                    packageName =
                        getString(
                            getColumnIndexOrThrow(COLUMN_PACKAGE_NAME),
                        ),
                    userSerial =
                        getLong(
                            getColumnIndexOrThrow(COLUMN_USER_SERIAL),
                        ),
                ),
            notificationKey =
                getString(
                    getColumnIndexOrThrow(COLUMN_NOTIFICATION_KEY),
                ),
            conversationId =
                getString(
                    getColumnIndexOrThrow(COLUMN_CONVERSATION_ID),
                ),
            conversationTitle =
                getString(
                    getColumnIndexOrThrow(COLUMN_CONVERSATION_TITLE),
                ),
            sourceLabel =
                getString(
                    getColumnIndexOrThrow(COLUMN_SOURCE_LABEL),
                ),
            senderLabel = getStringOrNull(COLUMN_SENDER_LABEL),
            body = getStringOrNull(COLUMN_BODY),
            timestampMillis =
                getLong(
                    getColumnIndexOrThrow(COLUMN_TIMESTAMP_MILLIS),
                ),
            incoming =
                getInt(
                    getColumnIndexOrThrow(COLUMN_INCOMING),
                ) != 0,
        )

    private fun insertRecord(
        database: SQLiteDatabase,
        record: ConnectedNotificationRecord,
    ) {
        val values =
            ContentValues().apply {
                put(COLUMN_ID, record.id)
                put(COLUMN_PACKAGE_NAME, record.key.packageName)
                put(COLUMN_USER_SERIAL, record.key.userSerial)
                put(COLUMN_NOTIFICATION_KEY, record.notificationKey)
                put(COLUMN_CONVERSATION_ID, record.conversationId)
                put(COLUMN_CONVERSATION_TITLE, record.conversationTitle)
                put(COLUMN_SOURCE_LABEL, record.sourceLabel)
                put(COLUMN_SENDER_LABEL, record.senderLabel)
                put(COLUMN_BODY, record.body)
                put(COLUMN_TIMESTAMP_MILLIS, record.timestampMillis)
                put(COLUMN_INCOMING, if (record.incoming) 1 else 0)
            }
        database.insertWithOnConflict(
            TABLE_HISTORY,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun deleteRecordsExcept(
        database: SQLiteDatabase,
        allRecords: List<ConnectedNotificationRecord>,
        retained: List<ConnectedNotificationRecord>,
    ) {
        val retainedIds =
            retained
                .asSequence()
                .map { record ->
                    record.id
                }.toHashSet()

        allRecords.forEach { record ->
            if (record.id !in retainedIds) {
                database.delete(
                    TABLE_HISTORY,
                    "$COLUMN_ID = ?",
                    arrayOf(record.id),
                )
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) {
            null
        } else {
            getString(index)
        }
    }

    private class HistoryDatabaseHelper(
        context: Context,
    ) : SQLiteOpenHelper(
            context,
            DATABASE_NAME,
            null,
            DATABASE_VERSION,
        ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(createHistoryTable)
            database.execSQL(createHistorySourceIndex)
            database.execSQL(createHistoryConversationIndex)
        }

        override fun onUpgrade(
            database: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) {
            if (oldVersion != newVersion) {
                database.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
                onCreate(database)
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "sable_connected_message_history.db"
        const val DATABASE_VERSION = 2
        const val TABLE_HISTORY = "connected_message_history"
        const val COLUMN_ID = "id"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_USER_SERIAL = "user_serial"
        const val COLUMN_NOTIFICATION_KEY = "notification_key"
        const val COLUMN_CONVERSATION_ID = "conversation_id"
        const val COLUMN_CONVERSATION_TITLE = "conversation_title"
        const val COLUMN_SOURCE_LABEL = "source_label"
        const val COLUMN_SENDER_LABEL = "sender_label"
        const val COLUMN_BODY = "body"
        const val COLUMN_TIMESTAMP_MILLIS = "timestamp_millis"
        const val COLUMN_INCOMING = "incoming"
        const val MAX_HISTORY_RECORDS = 1200
        val lock = Any()

        val historyColumns =
            arrayOf(
                COLUMN_ID,
                COLUMN_PACKAGE_NAME,
                COLUMN_USER_SERIAL,
                COLUMN_NOTIFICATION_KEY,
                COLUMN_CONVERSATION_ID,
                COLUMN_CONVERSATION_TITLE,
                COLUMN_SOURCE_LABEL,
                COLUMN_SENDER_LABEL,
                COLUMN_BODY,
                COLUMN_TIMESTAMP_MILLIS,
                COLUMN_INCOMING,
            )

        val createHistoryTable =
            """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_PACKAGE_NAME TEXT NOT NULL,
                $COLUMN_USER_SERIAL INTEGER NOT NULL,
                $COLUMN_NOTIFICATION_KEY TEXT NOT NULL,
                $COLUMN_CONVERSATION_ID TEXT NOT NULL,
                $COLUMN_CONVERSATION_TITLE TEXT NOT NULL,
                $COLUMN_SOURCE_LABEL TEXT NOT NULL,
                $COLUMN_SENDER_LABEL TEXT,
                $COLUMN_BODY TEXT,
                $COLUMN_TIMESTAMP_MILLIS INTEGER NOT NULL,
                $COLUMN_INCOMING INTEGER NOT NULL
            )
            """.trimIndent()

        val createHistorySourceIndex =
            """
            CREATE INDEX connected_message_history_source
            ON $TABLE_HISTORY (
                $COLUMN_PACKAGE_NAME,
                $COLUMN_USER_SERIAL,
                $COLUMN_TIMESTAMP_MILLIS DESC
            )
            """.trimIndent()

        val createHistoryConversationIndex =
            """
            CREATE INDEX connected_message_history_conversation
            ON $TABLE_HISTORY (
                $COLUMN_PACKAGE_NAME,
                $COLUMN_USER_SERIAL,
                $COLUMN_CONVERSATION_ID,
                $COLUMN_TIMESTAMP_MILLIS DESC
            )
            """.trimIndent()
    }
}
