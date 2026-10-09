package org.vaachak.reader.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

expect fun createDataStore(producePath: () -> String): DataStore<Preferences>

