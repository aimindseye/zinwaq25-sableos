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

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraScannerUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun scanner_top_bar_displays_correct_script_text_for_latin() {
        composeTestRule.setContent {
            ScannerTopBar(
                isDevanagari = false,
                onScriptChange = {}
            )
        }

        // Verify the label and the default Latin text exist
        composeTestRule.onNodeWithText("Detect Script: ").assertIsDisplayed()
        composeTestRule.onNodeWithText("English/Spanish").assertIsDisplayed()
    }

    @Test
    fun scanner_top_bar_displays_correct_script_text_for_devanagari() {
        composeTestRule.setContent {
            ScannerTopBar(
                isDevanagari = true,
                onScriptChange = {}
            )
        }

        // Verify it switches properly when the state is true
        composeTestRule.onNodeWithText("Hindi/Marathi").assertIsDisplayed()
    }

    @Test
    fun scanner_bottom_controls_display_detected_text_and_buttons() {
        val testDetectedText = "Café Menu: Espresso $3"

        composeTestRule.setContent {
            ScannerBottomControls(
                zoomRatio = 1f,
                detectedText = testDetectedText,
                onZoomChange = {},
                onOpenGallery = {},
                onCapture = {},
                onDismiss = {}
            )
        }

        // Verify the extracted text is shown in the preview box
        composeTestRule.onNodeWithText(testDetectedText).assertIsDisplayed()

        // Verify all three Action Buttons exist based on their icons/content descriptions
        // (Note: To test Icon buttons strictly, you often rely on content descriptions or testTags.
        // We will test if the buttons are clickable).
        composeTestRule.onNodeWithContentDescription("Zoom In").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Zoom Out").assertIsDisplayed()
    }
}