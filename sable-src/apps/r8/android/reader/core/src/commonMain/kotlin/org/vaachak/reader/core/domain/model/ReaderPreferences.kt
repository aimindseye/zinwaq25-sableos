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

package org.vaachak.reader.core.domain.model

/**
 * Pure Kotlin Data Class holding all visual preferences.
 * Uses Primitives (String, Double) to ensure KMP compatibility.
 */
data class ReaderPreferences(
    val theme: String = "light",
    val fontSize: Double = 1.0,
    val publisherStyles: Boolean = true,

    // CHANGE: These must be nullable to support "Original Layout"
    val fontFamily: String? = null,
    val textAlign: String? = null,
    val lineHeight: Double? = null,
    val letterSpacing: Double? = null,
    val paragraphSpacing: Double? = null,
    val pageMargins: Double? = null,
    val wordSpacing: Double? = null,
    val paragraphIndent: Double? = null,
    val hyphens: Boolean? = null,
    val ligatures: Boolean? = null,
    val marginTop: Double? = null,
    val marginBottom: Double? = null
)