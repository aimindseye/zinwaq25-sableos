package org.sableos.tasks.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The profile's row motion (Settings.Secure sable_profile): BlackBerry 7 fades, BlackBerry 10 slides and fades. */
internal object RowMotion {
    const val BB7_MILLIS = 150
    const val BB10_MILLIS = 250

    fun millis(bb7: Boolean): Int = if (bb7) BB7_MILLIS else BB10_MILLIS
}

/** Whether the system profile is BlackBerry 7. Any other value, or none, is BlackBerry 10. */
@Composable
internal fun rememberIsBb7(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Secure.getString(context.contentResolver, "sable_profile") }.getOrNull() == "bb7"
    }
}

/** A row's motion: it fades in (BlackBerry 10 also slides in) when shown; [Animatable] 1 is fully shown. */
@Composable
internal fun rowMotion(
    key: Any,
    visible: Animatable<Float, *>,
    bb7: Boolean,
): Modifier {
    val millis = RowMotion.millis(bb7)
    val easing = if (bb7) LinearEasing else FastOutSlowInEasing
    LaunchedEffect(key) {
        if (visible.value < 1f) visible.animateTo(1f, tween(millis, easing = easing))
    }
    return Modifier.graphicsLayer {
        alpha = visible.value
        if (!bb7) translationX = (1f - visible.value) * 24.dp.toPx()
    }
}
