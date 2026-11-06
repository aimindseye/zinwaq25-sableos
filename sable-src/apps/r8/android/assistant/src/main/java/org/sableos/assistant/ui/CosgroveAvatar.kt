@file:Suppress("FunctionNaming", "MagicNumber") // Compose naming; the drawing is in fractions of its size.

package org.sableos.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Cosgrove's portrait, drawn rather than shipped as an image: a butler in a dark tailcoat, white
 * wing collar and the gold bow tie of the Sable accent, on the night-blue disc of the app icon.
 */
@Composable
fun CosgroveAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        drawCircle(NIGHT)
        // Shoulders and tailcoat, cut off by the disc.
        drawPath(
            Path().apply {
                moveTo(w * 0.12f, w * 0.98f)
                cubicTo(w * 0.16f, w * 0.72f, w * 0.30f, w * 0.66f, w * 0.50f, w * 0.66f)
                cubicTo(w * 0.70f, w * 0.66f, w * 0.84f, w * 0.72f, w * 0.88f, w * 0.98f)
                close()
            },
            COAT,
        )
        // Shirt front between the lapels.
        drawPath(
            Path().apply {
                moveTo(w * 0.40f, w * 0.68f)
                lineTo(w * 0.60f, w * 0.68f)
                lineTo(w * 0.50f, w * 0.96f)
                close()
            },
            SHIRT,
        )
        face(w)
        bowTie(w)
    }
}

private fun DrawScope.face(w: Float) {
    // Neck, head, then hair combed back and parted.
    drawRect(SKIN_SHADE, topLeft = Offset(w * 0.44f, w * 0.52f), size = Size(w * 0.12f, w * 0.16f))
    drawOval(SKIN, topLeft = Offset(w * 0.33f, w * 0.20f), size = Size(w * 0.34f, w * 0.40f))
    drawPath(
        Path().apply {
            moveTo(w * 0.32f, w * 0.38f)
            cubicTo(w * 0.30f, w * 0.18f, w * 0.44f, w * 0.14f, w * 0.52f, w * 0.15f)
            cubicTo(w * 0.64f, w * 0.15f, w * 0.71f, w * 0.22f, w * 0.68f, w * 0.38f)
            cubicTo(w * 0.64f, w * 0.27f, w * 0.56f, w * 0.25f, w * 0.46f, w * 0.27f)
            cubicTo(w * 0.40f, w * 0.28f, w * 0.35f, w * 0.31f, w * 0.32f, w * 0.38f)
            close()
        },
        HAIR,
    )
    // Eyes and a composed, slight smile.
    drawCircle(HAIR, radius = w * 0.018f, center = Offset(w * 0.44f, w * 0.40f))
    drawCircle(HAIR, radius = w * 0.018f, center = Offset(w * 0.56f, w * 0.40f))
    drawRect(SKIN_SHADE, topLeft = Offset(w * 0.45f, w * 0.49f), size = Size(w * 0.10f, w * 0.012f))
}

private fun DrawScope.bowTie(w: Float) {
    val y = w * 0.70f
    drawPath(
        Path().apply {
            moveTo(w * 0.50f, y)
            lineTo(w * 0.40f, y - w * 0.05f)
            lineTo(w * 0.40f, y + w * 0.05f)
            close()
            moveTo(w * 0.50f, y)
            lineTo(w * 0.60f, y - w * 0.05f)
            lineTo(w * 0.60f, y + w * 0.05f)
            close()
        },
        GOLD,
    )
    drawCircle(GOLD, radius = w * 0.025f, center = Offset(w * 0.50f, y))
}

private val NIGHT = Color(0xFF161C24)
private val COAT = Color(0xFF0B0E12)
private val SHIRT = Color(0xFFF2F2EE)
private val SKIN = Color(0xFFD9BFA3)
private val SKIN_SHADE = Color(0xFFB89A7E)
private val HAIR = Color(0xFF3A3F47)
private val GOLD = Color(0xFFF5C542)
