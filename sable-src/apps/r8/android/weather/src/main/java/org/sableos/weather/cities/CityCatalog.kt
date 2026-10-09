package org.sableos.weather.cities

/**
 * Offline search source for the add-city screen. Weather has no geocoding backend and adds none: search runs over
 * this built-in list, and a city that is not listed can be entered as "Name, latitude, longitude, Area/Zone".
 */
object CityCatalog {
    const val MAX_RESULTS = 8

    val ALL: List<City> =
        listOf(
            City("Amsterdam", 52.3676, 4.9041, "Europe/Amsterdam"),
            City("Athens", 37.9838, 23.7275, "Europe/Athens"),
            City("Atlanta", 33.7490, -84.3880, "America/New_York"),
            City("Auckland", -36.8485, 174.7633, "Pacific/Auckland"),
            City("Bangalore", 12.9716, 77.5946, "Asia/Kolkata"),
            City("Bangkok", 13.7563, 100.5018, "Asia/Bangkok"),
            City("Barcelona", 41.3874, 2.1686, "Europe/Madrid"),
            City("Beijing", 39.9042, 116.4074, "Asia/Shanghai"),
            City("Berlin", 52.5200, 13.4050, "Europe/Berlin"),
            City("Boston", 42.3601, -71.0589, "America/New_York"),
            City("Buenos Aires", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"),
            City("Cairo", 30.0444, 31.2357, "Africa/Cairo"),
            City("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg"),
            City("Chennai", 13.0827, 80.2707, "Asia/Kolkata"),
            City("Chicago", 41.8781, -87.6298, "America/Chicago"),
            City("Dallas", 32.7767, -96.7970, "America/Chicago"),
            City("Delhi", 28.6139, 77.2090, "Asia/Kolkata"),
            City("Denver", 39.7392, -104.9903, "America/Denver"),
            City("Dubai", 25.2048, 55.2708, "Asia/Dubai"),
            City("Dublin", 53.3498, -6.2603, "Europe/Dublin"),
            City("Edison", 40.5187, -74.4121, "America/New_York"),
            City("Hong Kong", 22.3193, 114.1694, "Asia/Hong_Kong"),
            City("Houston", 29.7604, -95.3698, "America/Chicago"),
            City("Hyderabad", 17.3850, 78.4867, "Asia/Kolkata"),
            City("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
            City("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
            City("Jersey City", 40.7178, -74.0430, "America/New_York"),
            City("Johannesburg", -26.2041, 28.0473, "Africa/Johannesburg"),
            City("Kolkata", 22.5726, 88.3639, "Asia/Kolkata"),
            City("Lagos", 6.5244, 3.3792, "Africa/Lagos"),
            City("Lisbon", 38.7223, -9.1393, "Europe/Lisbon"),
            City("London", 51.5074, -0.1278, "Europe/London"),
            City("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"),
            City("Madrid", 40.4168, -3.7038, "Europe/Madrid"),
            City("Melbourne", -37.8136, 144.9631, "Australia/Melbourne"),
            City("Mexico City", 19.4326, -99.1332, "America/Mexico_City"),
            City("Miami", 25.7617, -80.1918, "America/New_York"),
            City("Montreal", 45.5019, -73.5674, "America/Toronto"),
            City("Moscow", 55.7558, 37.6173, "Europe/Moscow"),
            City("Mumbai", 19.0760, 72.8777, "Asia/Kolkata"),
            City("Nairobi", -1.2921, 36.8219, "Africa/Nairobi"),
            City("New York", 40.7128, -74.0060, "America/New_York"),
            City("Newark", 40.7357, -74.1724, "America/New_York"),
            City("Paris", 48.8566, 2.3522, "Europe/Paris"),
            City("Philadelphia", 39.9526, -75.1652, "America/New_York"),
            City("Phoenix", 33.4484, -112.0740, "America/Phoenix"),
            City("Pune", 18.5204, 73.8567, "Asia/Kolkata"),
            City("Rome", 41.9028, 12.4964, "Europe/Rome"),
            City("San Francisco", 37.7749, -122.4194, "America/Los_Angeles"),
            City("Sao Paulo", -23.5505, -46.6333, "America/Sao_Paulo"),
            City("Seattle", 47.6062, -122.3321, "America/Los_Angeles"),
            City("Seoul", 37.5665, 126.9780, "Asia/Seoul"),
            City("Shanghai", 31.2304, 121.4737, "Asia/Shanghai"),
            City("Shenzhen", 22.5431, 114.0579, "Asia/Shanghai"),
            City("Singapore", 1.3521, 103.8198, "Asia/Singapore"),
            City("Stockholm", 59.3293, 18.0686, "Europe/Stockholm"),
            City("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
            City("Taipei", 25.0330, 121.5654, "Asia/Taipei"),
            City("Tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
            City("Toronto", 43.6532, -79.3832, "America/Toronto"),
            City("Vancouver", 49.2827, -123.1207, "America/Vancouver"),
            City("Vienna", 48.2082, 16.3738, "Europe/Vienna"),
            City("Warsaw", 52.2297, 21.0122, "Europe/Warsaw"),
            City("Washington", 38.9072, -77.0369, "America/New_York"),
            City("Zurich", 47.3769, 8.5417, "Europe/Zurich")
        )

    /**
     * Cities whose name starts with [query] first, then names containing it; an exact manual entry
     * ("Name, lat, lon, Area/Zone") is offered first when it parses and validates. Cities already in [exclude] are
     * left out. A blank query has no results.
     */
    fun search(query: String, exclude: List<City> = emptyList()): List<City> {
        val q = CityRules.normalizedName(query)
        if (q.isEmpty()) return emptyList()
        val manual = parseManual(q)?.let { listOf(it) }.orEmpty()
        val prefix = ALL.filter { it.name.startsWith(q, ignoreCase = true) }
        val contains = ALL.filter { it.name.contains(q, ignoreCase = true) && it !in prefix }
        return (manual + prefix + contains)
            .filter { c -> exclude.none { CityRules.samePlace(it, c) } }
            .take(MAX_RESULTS)
    }

    /** "Name, latitude, longitude, Area/Zone" -> a valid [City], or null. */
    fun parseManual(text: String): City? {
        val parts = text.split(',').map { it.trim() }
        if (parts.size != 4) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        val city = City(CityRules.normalizedName(parts[0]), lat, lon, parts[3])
        return if (CityRules.validate(city) == null) city else null
    }
}
