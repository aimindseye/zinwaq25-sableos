package org.sableos.titan2.theme

import android.graphics.Typeface

/**
 * Source Sans 3 for the platform apps' own text views (Sable 2.0 type, UI direction
 * 2026-11-04): the "source-sans" family the image adds in
 * /product/etc/fonts_customization.xml. Where it is missing, Typeface.create returns the
 * default sans-serif, so nothing breaks.
 */
object SableFonts {
    private const val FAMILY = "source-sans"

    val REGULAR: Typeface by lazy { Typeface.create(FAMILY, Typeface.NORMAL) }
    val BOLD: Typeface by lazy { Typeface.create(FAMILY, Typeface.BOLD) }

    fun of(bold: Boolean): Typeface = if (bold) BOLD else REGULAR
}
