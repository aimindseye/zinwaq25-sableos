/*
 *
 *  *
 *  *  *  Copyright (c) 2026 Piyush Daiya
 *  *  *  *
 *  *  *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  *  *  * of this software and associated documentation files (the "Software"), to deal
 *  *  *  * in the Software without restriction, including without limitation the rights
 *  *  *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  *  *  * copies of the Software, and to permit persons to whom the Software is
 *  *  *  * furnished to do so, subject to the following conditions:
 *  *  *  *
 *  *  *  * The above copyright notice and this permission notice shall be included in all
 *  *  *  * copies or substantial portions of the Software.
 *  *  *  *
 *  *  *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  *  *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  *  *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  *  *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  *  *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  *  *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  *  *  * SOFTWARE.
 *  *
 *
 */

package org.vaachak.textreader

import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

class TextReaderUtilTest {

    // ---------------------------------------------------------------------------
    // Stream Parsing Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `readTextFromStream returns correct string from valid stream`() {
        val expectedText = "Hello Vaachak\nThis is a unit test."
        val inputStream: InputStream = ByteArrayInputStream(expectedText.toByteArray())

        val result = TextReaderUtil.readTextFromStream(inputStream)

        assertEquals(expectedText, result)
    }

    @Test
    fun `readTextFromStream returns error message on null stream`() {
        val result = TextReaderUtil.readTextFromStream(null)
        assertEquals("Error: File stream is null.", result)
    }

    @Test
    fun `readTextFromStream handles empty stream gracefully`() {
        val inputStream: InputStream = ByteArrayInputStream("".toByteArray())
        val result = TextReaderUtil.readTextFromStream(inputStream)
        assertEquals("", result)
    }

    // ---------------------------------------------------------------------------
    // Language & Locale Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `language tags return correct locale strings`() {
        val hindiLocale = Locale.forLanguageTag("hi-IN")
        val tamilLocale = Locale.forLanguageTag("ta-IN")
        val gujaratiLocale = Locale.forLanguageTag("gu-IN")

        assertEquals("hi", hindiLocale.language)
        assertEquals("IN", hindiLocale.country)

        assertEquals("ta", tamilLocale.language)
        assertEquals("gu", gujaratiLocale.language)
    }

    @Test
    fun `Lang data class holds correct properties`() {
        val lang = Lang("Bengali", "bn", "bn-IN")

        assertEquals("Bengali", lang.name)
        assertEquals("bn", lang.code)
        assertEquals("bn-IN", lang.ttsTag)
    }

    // ---------------------------------------------------------------------------
    // Voice Mapper Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `voice mapper formats network voice correctly`() {
        // Arrange: Mock the Android Voice object
        val mockVoice = mock(Voice::class.java)
        `when`(mockVoice.locale).thenReturn(Locale.forLanguageTag("hi-IN"))
        `when`(mockVoice.name).thenReturn("hi-in-x-hie-network")
        `when`(mockVoice.isNetworkConnectionRequired).thenReturn(true)

        // Act
        val result = VoiceNameMapper.getFriendlyName(mockVoice)

        // Assert
        assertEquals("Hindi (India) - Hie (Cloud)", result)
    }

    @Test
    fun `voice mapper formats local voice correctly`() {
        // Arrange
        val mockVoice = mock(Voice::class.java)
        `when`(mockVoice.locale).thenReturn(Locale.forLanguageTag("en-AU"))
        `when`(mockVoice.name).thenReturn("en-au-x-aud-local")
        `when`(mockVoice.isNetworkConnectionRequired).thenReturn(false)

        // Act
        val result = VoiceNameMapper.getFriendlyName(mockVoice)

        // Assert
        assertEquals("English (Australia) - Aud (Local)", result)
    }
}