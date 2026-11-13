package org.sableos.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where Media looks for a station's logo: HTTPS only, the station's own domain, a few requests at most. */
class MediaArtworkTest {
    @Test
    fun logoCandidatesStayOnTheStationsDomain() {
        assertEquals(
            listOf(
                "https://stream.example.com/apple-touch-icon.png",
                "https://stream.example.com/favicon.ico",
                "https://example.com/apple-touch-icon.png",
                "https://example.com/favicon.ico",
            ),
            stationLogoCandidates("http://stream.example.com:8000/live.mp3"),
        )
    }

    @Test
    fun logoCandidatesSkipPublicSuffixes() {
        val hosts =
            stationLogoCandidates("https://radio.bbc.co.uk/live")
                .map { it.substringAfter("//").substringBefore('/') }
        assertEquals(listOf("radio.bbc.co.uk", "bbc.co.uk"), hosts.distinct())
    }

    @Test
    fun noCandidatesForAddressesOrJunk() {
        assertTrue(stationLogoCandidates("http://192.168.1.4:8000/stream").isEmpty())
        assertTrue(stationLogoCandidates("not a url").isEmpty())
    }
}
