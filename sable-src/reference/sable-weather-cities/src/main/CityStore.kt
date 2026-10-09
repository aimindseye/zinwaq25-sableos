package org.sableos.reference.weathercities

/** The only storage the model needs. The host backs it with its own private preferences. */
interface KeyValueStore {
    fun get(key: String): String?

    fun put(key: String, value: String)
}

/** Plain-text, versioned encoding of the city list and the selection. Unreadable lines are dropped, never fatal. */
object CityCodec {
    const val HEADER = "SABLE_CITIES_V1"
    private const val SELECTED = "SELECTED"
    private const val CITY = "CITY"

    fun encode(state: CityState): String {
        val lines = mutableListOf(HEADER, "$SELECTED|${escape(state.selected.name)}")
        state.cities.forEach {
            lines +=
                "$CITY|${escape(it.name)}|${it.latitude}|${it.longitude}|${escape(it.timezone)}"
        }
        return lines.joinToString("\n")
    }

    fun decode(text: String?): CityState {
        val lines = text?.lines().orEmpty()
        if (lines.firstOrNull() != HEADER) return CityState.defaults()
        val selected = lines.firstOrNull {
            it.startsWith("$SELECTED|")
        }?.let { unescape(it.substringAfter('|')) }
        val cities = lines.mapNotNull { city(it) }
        return CityState.of(cities, selected)
    }

    private fun city(line: String): City? {
        val parts = line.split('|')
        if (parts.size != 5 || parts[0] != CITY) return null
        val lat = parts[2].toDoubleOrNull()
        val lon = parts[3].toDoubleOrNull()
        return if (lat == null ||
            lon == null
        ) {
            null
        } else {
            City(unescape(parts[1]), lat, lon, unescape(parts[4]))
        }
    }

    private fun escape(s: String) =
        s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = if (c == '\\' && i + 1 < s.length) s[i + 1] else null
            val mapped = when (next) {
                'p' -> '|'
                'n' -> '\n'
                'r' -> '\r'
                '\\' -> '\\'
                else -> null
            }
            out.append(mapped ?: c)
            i += if (mapped != null) 2 else 1
        }
        return out.toString()
    }
}

/** Load, edit and save. Every successful edit is written before it is reported, so a restart sees it. */
class CityRepository(private val store: KeyValueStore) {
    fun load(): CityState = CityCodec.decode(store.get(KEY))

    fun add(city: City, select: Boolean = true): CityEdit = edit { it.add(city, select) }

    fun remove(name: String): CityEdit = edit { it.remove(name) }

    fun select(name: String): CityEdit = edit { it.select(name) }

    fun reset(): CityEdit = edit { it.reset() }

    private fun edit(op: (CityState) -> CityEdit): CityEdit {
        val result = op(load())
        if (result.changed) store.put(KEY, CityCodec.encode(result.state))
        return result
    }

    companion object {
        const val KEY = "cities_v1"
    }
}
