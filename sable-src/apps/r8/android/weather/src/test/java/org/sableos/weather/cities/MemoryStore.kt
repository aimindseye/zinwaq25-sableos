package org.sableos.weather.cities

/** In-memory [KeyValueStore] for tests, standing in for Weather's private preferences. */
class MemoryStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    val data = initial.toMutableMap()

    override fun get(key: String): String? = data[key]

    override fun put(key: String, value: String) {
        data[key] = value
    }

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun keys(): Set<String> = data.keys.toSet()
}
