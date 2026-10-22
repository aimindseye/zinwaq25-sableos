package org.sableos.titan2.camera.ui

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import org.sableos.titan2.camera.core.Size

private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val QUARTER_TURN_DEGREES = 90f
private const val HALF_TURN_DEGREES = 180f

/** Display rotation ([Surface].ROTATION_*) in degrees. */
internal fun rotationDegrees(rotation: Int) = when (rotation) {
    Surface.ROTATION_90 -> QUARTER_TURN
    Surface.ROTATION_180 -> HALF_TURN
    Surface.ROTATION_270 -> THREE_QUARTER_TURN
    else -> 0
}

/**
 * Upright preview aspect: the sensor buffer is landscape for sensor orientation 90/270, so
 * swap it when the display is upright.
 */
internal fun uprightAspect(size: Size, sensor: Int, displayDegrees: Int): Float {
    val swap = (sensor % HALF_TURN == QUARTER_TURN) == (displayDegrees % HALF_TURN == 0)
    return if (swap) size.h.toFloat() / size.w else size.w.toFloat() / size.h
}

/** Fits the preview buffer [ps] to this view for the display [rotation]. */
internal fun TextureView.fitPreview(ps: Size, rotation: Int) {
    val vw = width.toFloat()
    val vh = height.toFloat()
    if (vw == 0f || vh == 0f) return
    val m = Matrix()
    val view = RectF(0f, 0f, vw, vh)
    val buf = RectF(0f, 0f, ps.h.toFloat(), ps.w.toFloat())
    val cx = view.centerX()
    val cy = view.centerY()
    when (rotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> {
            buf.offset(cx - buf.centerX(), cy - buf.centerY())
            m.setRectToRect(view, buf, Matrix.ScaleToFit.FILL)
            val s = maxOf(vh / ps.h, vw / ps.w)
            m.postScale(s, s, cx, cy)
            m.postRotate(QUARTER_TURN_DEGREES * (rotation - 2), cx, cy)
        }

        Surface.ROTATION_180 -> m.postRotate(HALF_TURN_DEGREES, cx, cy)
    }
    setTransform(m)
}

/** The viewfinder's surface lifecycle, passed on to the activity. */
internal class PreviewSurfaceListener(
    private val onAvailable: (SurfaceTexture) -> Unit,
    private val onResized: () -> Unit,
    private val onDestroyed: () -> Unit
) : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = onAvailable(st)

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = onResized()

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        onDestroyed()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
        // Nothing to do per frame: the preview is drawn by the TextureView itself.
    }
}
