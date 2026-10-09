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

package org.vaachak.reader.leisure.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

object EinkHelper {

    // 1. Detect if Device is E-ink (Best Guess)
    fun isEinkDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()

        return brand.contains("onyx") || manufacturer.contains("onyx") ||
                brand.contains("boyue") || manufacturer.contains("boyue") ||
                brand.contains("hisense") || manufacturer.contains("hisense") ||
                hardware.contains("freescale") || // Common E-ink chip
                product.contains("ntx") // Nook/Kobo often use this
    }

    // 2. Trigger Full Screen Refresh (GC)
    fun requestFullRefresh(context: Context) {
        if (!isEinkDevice()) return

        try {
            val brand = Build.BRAND.lowercase()

            when {
                // ONYX BOOX (Leaf, Note, Nova, etc.)
                brand.contains("onyx") -> {
                    // Method 1: Standard Onyx Broadcast
                    context.sendBroadcast(Intent("android.intent.action.screen.refresh"))
                }

                // BOYUE / MEENBOOK / LIKEBOOK
                brand.contains("boyue") || brand.contains("meebook") -> {
                    context.sendBroadcast(Intent("android.intent.action.eink.FULL_REFRESH"))
                }

                // HISENSE
                brand.contains("hisense") -> {
                    context.sendBroadcast(Intent("com.hisense.eink.service.REFRESH"))
                }

                // GENERIC FALLBACK (Experimental)
                else -> {
                    Timber.tag("EinkHelper").d("Unknown E-ink brand, skipping refresh trigger.")
                }
            }
        } catch (e: Exception) {
            Timber.tag("EinkHelper").e(e, "Failed to request E-ink refresh")
        }
    }
}