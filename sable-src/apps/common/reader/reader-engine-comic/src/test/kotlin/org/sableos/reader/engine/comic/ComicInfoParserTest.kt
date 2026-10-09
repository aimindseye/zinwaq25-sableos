package org.sableos.reader.engine.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.reader.comic.ComicReadingMode

class ComicInfoParserTest {
    private fun parse(body: String) = ComicInfoParser.parse(ComicFixtures.comicInfoXml(body))

    @Test
    fun theUsefulFieldsAreRead() {
        val info = parse(
            "<Title> Night </Title><Series>Moon</Series><Number>3.5</Number><Volume>2</Volume><Writer>Ann</Writer>" +
                "<Publisher>P</Publisher><Summary>S</Summary><Year>2024</Year><PageCount>180</PageCount>" +
                "<Format>Tankobon</Format><Manga>YesAndRightToLeft</Manga>" +
                "<Pages><Page Image=\"0\" Type=\"Story\"/><Page Image=\"1\" Type=\"FrontCover\"/></Pages>",
        )
        assertNotNull(info)
        assertEquals("Night", info?.title)
        assertEquals("Moon", info?.series)
        assertEquals(3.5, info?.seriesIndex ?: 0.0, 0.0)
        assertEquals(2, info?.volume)
        assertEquals(2024, info?.year)
        assertEquals(180, info?.pageCount)
        assertEquals(MangaFlag.YES_RIGHT_TO_LEFT, info?.manga)
        assertEquals(1, info?.coverPage)
    }

    @Test
    fun missingOrBadFieldsAreNullNotFatal() {
        val info = parse("<Volume>x</Volume><Year></Year><PageCount>-3</PageCount><Manga>maybe</Manga>")
        assertNotNull(info)
        assertNull(info?.volume)
        assertNull(info?.year)
        assertNull(info?.pageCount)
        assertEquals(MangaFlag.UNKNOWN, info?.manga)
    }

    @Test
    fun doctypeAndEntitiesAreRefusedInAnyEncoding() {
        val ascii = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x \"y\">]><ComicInfo><Title>&x;</Title></ComicInfo>"
        assertNull(ComicInfoParser.parse(ascii.toByteArray()))
        assertNull(ComicInfoParser.parse(ascii.lowercase().toByteArray()))
        assertNull(ComicInfoParser.parse(ascii.toByteArray(Charsets.UTF_16LE)))
        assertNull(ComicInfoParser.parse(ascii.toByteArray(Charsets.UTF_16BE)))
        val billionLaughs = "<?xml version=\"1.0\"?><!ENTITY a \"aaaa\"><ComicInfo/>"
        assertNull(ComicInfoParser.parse(billionLaughs.toByteArray()))
    }

    @Test
    fun oversizeMalformedAndForeignDocumentsAreRefused() {
        assertNull(ComicInfoParser.parse(ByteArray(0)))
        assertNull(ComicInfoParser.parse(ByteArray(ComicLimits.DEFAULT_MAX_COMIC_INFO_BYTES + 1) { 32 }))
        assertNull(ComicInfoParser.parse("<ComicInfo><Title>".toByteArray()))
        assertNull(ComicInfoParser.parse("<Other><Title>x</Title></Other>".toByteArray()))
        assertNull(ComicInfoParser.parse(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun metadataChoosesTheFirstOpenMode() {
        val rtl = parse("<Manga>YesAndRightToLeft</Manga>")
        assertEquals(ComicReadingMode.PAGED_RTL, ComicViewDefaults.initial(rtl).mode)
        assertEquals(ComicReadingMode.PAGED_LTR, ComicViewDefaults.initial(parse("<Manga>Yes</Manga>")).mode)
        assertEquals(ComicReadingMode.WEBTOON, ComicViewDefaults.initial(parse("<Format>Webtoon</Format>")).mode)
        assertEquals(ComicReadingMode.PAGED_LTR, ComicViewDefaults.initial(null).mode)
    }
}
