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

package org.vaachak.reader.leisure.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import coil.transform.Transformation

/**
 * A Coil Transformation that converts standard color images into
 * crisp, high-contrast 1-bit dithered images optimized for E-ink screens.
 */
class EinkDitherTransformation : Transformation {

    // This key ensures Coil caches the dithered version separately from the original
    override val cacheKey: String = "eink_dither_transformation"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height

        // Create a mutable copy of the bitmap to manipulate pixels
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)

        // Step 1: Convert the entire image to Grayscale first for accurate luminance
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            // Standard luminosity formula
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        // Step 2: Apply the Floyd-Steinberg Dithering Algorithm
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x

                // Read the grayscale value (R, G, and B are all the same now)
                val oldPixel = Color.red(pixels[index])

                // Threshold: If it's darker than 50% gray, make it pure black. Otherwise, pure white.
                val newPixel = if (oldPixel < 128) 0 else 255
                pixels[index] = Color.rgb(newPixel, newPixel, newPixel)

                // Calculate the quantization error (what we lost by rounding to 0 or 255)
                val quantError = oldPixel - newPixel

                // Diffuse the error to neighboring pixels using the Floyd-Steinberg weights
                // Right (7/16)
                if (x + 1 < width)
                    addError(pixels, index + 1, quantError, 7.0 / 16.0)
                // Bottom-Left (3/16)
                if (x - 1 >= 0 && y + 1 < height)
                    addError(pixels, index + width - 1, quantError, 3.0 / 16.0)
                // Bottom (5/16)
                if (y + 1 < height)
                    addError(pixels, index + width, quantError, 5.0 / 16.0)
                // Bottom-Right (1/16)
                if (x + 1 < width && y + 1 < height)
                    addError(pixels, index + width + 1, quantError, 1.0 / 16.0)
            }
        }

        // Apply the modified pixels back to the bitmap
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun addError(pixels: IntArray, index: Int, error: Int, weight: Double) {
        val oldColor = Color.red(pixels[index])
        var newColor = (oldColor + error * weight).toInt()
        // Clamp the value between 0 and 255 to prevent overflow visual glitches
        newColor = newColor.coerceIn(0, 255)
        pixels[index] = Color.rgb(newColor, newColor, newColor)
    }
}