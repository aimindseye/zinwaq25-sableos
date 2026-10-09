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

/*
 * Copyright (c) 2026 Piyush Daiya
 * ...
 */

package org.vaachak.textreader

import android.speech.tts.TextToSpeech
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun app_displays_minimalist_title_and_empty_text_box_on_launch() {
        val mockTts: TextToSpeech = mock(TextToSpeech::class.java)

        composeTestRule.setContent {
            MainScreen(
                ttsEngine = mockTts,
                isTtsReady = true,
                isPlaying = false,
                initialText = "",
                onSpeakText = { _, _ -> },
                onStopText = {}
            )
        }

        composeTestRule.onNodeWithText("V A A C H A K").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan, paste or upload text...").assertIsDisplayed()
    }

    @Test
    fun playback_buttons_enable_correctly_when_text_is_typed() {
        val mockTts: TextToSpeech = mock(TextToSpeech::class.java)

        composeTestRule.setContent {
            MainScreen(
                ttsEngine = mockTts,
                isTtsReady = true,
                isPlaying = false,
                initialText = "",
                onSpeakText = { _, _ -> },
                onStopText = {}
            )
        }

        val playButton = composeTestRule.onNodeWithContentDescription("Play")
        val saveButton = composeTestRule.onNodeWithContentDescription("Export Audio")
        val stopButton = composeTestRule.onNodeWithContentDescription("Stop")

        // Assert all are disabled when the box is empty
        playButton.assertIsNotEnabled()
        saveButton.assertIsNotEnabled()
        stopButton.assertIsNotEnabled() // Disabled because isPlaying is false

        // Type into the text field
        composeTestRule.onNodeWithText("Scan, paste or upload text...")
            .performTextInput("Testing the TTS engine.")

        // Assert Play and Save become enabled, Stop remains disabled
        playButton.assertIsEnabled()
        saveButton.assertIsEnabled()
        stopButton.assertIsNotEnabled()
    }

    @Test
    fun playing_state_disables_play_and_save_and_enables_stop() {
        val mockTts: TextToSpeech = mock(TextToSpeech::class.java)
        val testText = "Reading this text out loud."

        composeTestRule.setContent {
            MainScreen(
                ttsEngine = mockTts,
                isTtsReady = true,
                isPlaying = true, // Simulate that the engine is currently reading
                initialText = testText,
                onSpeakText = { _, _ -> },
                onStopText = {}
            )
        }

        // Play and Export should be disabled while playing to prevent overlapping audio/intents
        composeTestRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Export Audio").assertIsNotEnabled()

        // Stop should be the ONLY enabled playback control
        composeTestRule.onNodeWithContentDescription("Stop").assertIsEnabled()
    }

    @Test
    fun app_loads_shared_text_from_intent_properly() {
        val mockTts: TextToSpeech = mock(TextToSpeech::class.java)
        val sharedIntentText = "This text was shared from a browser!"

        composeTestRule.setContent {
            MainScreen(
                ttsEngine = mockTts,
                isTtsReady = true,
                isPlaying = false,
                initialText = sharedIntentText,
                onSpeakText = { _, _ -> },
                onStopText = {}
            )
        }

        // Verify the text box automatically populates with the shared text
        composeTestRule.onNodeWithText(sharedIntentText).assertIsDisplayed()

        // The Play button should be enabled immediately
        composeTestRule.onNodeWithContentDescription("Play").assertIsEnabled()
    }

    @Test
    fun clicking_settings_icon_opens_audio_settings_dialog() {
        val mockTts: TextToSpeech = mock(TextToSpeech::class.java)

        composeTestRule.setContent {
            MainScreen(
                ttsEngine = mockTts,
                isTtsReady = true,
                isPlaying = false,
                initialText = "",
                onSpeakText = { _, _ -> },
                onStopText = {}
            )
        }

        composeTestRule.onNodeWithText("Audio Settings").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Audio Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }
}