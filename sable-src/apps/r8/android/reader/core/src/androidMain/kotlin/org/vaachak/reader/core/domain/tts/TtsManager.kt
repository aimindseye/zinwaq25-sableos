/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */
package org.vaachak.reader.core.domain.tts

import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Locator

interface TtsManager {
    val state: StateFlow<TtsState>
    val config: StateFlow<TtsConfig>

    fun play(locator: Locator? = null)
    fun pause()
    fun previous()
    fun next()
    fun stop()

    fun setRate(rate: Double)
    fun setPitch(pitch: Double) // <--- Ensure this line exists!
    fun close()
}

enum class TtsState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

data class TtsConfig(
    val rate: Double = 1.0,
    val pitch: Double = 1.0,
    val language: String? = null
)